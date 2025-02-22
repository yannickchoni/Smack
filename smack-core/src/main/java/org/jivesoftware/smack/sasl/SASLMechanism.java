/**
 *
 * Copyright 2003-2007 Jive Software, 2014-2019 Florian Schmaus
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.smack.sasl;

import java.text.Normalizer;
import java.text.Normalizer.Form;

import javax.net.ssl.SSLSession;
import javax.security.auth.callback.CallbackHandler;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.SmackException.SmackSaslException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.sasl.packet.SaslNonza.AuthMechanism;
import org.jivesoftware.smack.sasl.packet.SaslNonza.Response;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smack.util.stringencoder.Base64;

import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.EntityBareJid;

/**
 * Base class for SASL mechanisms.
 * Subclasses will likely want to implement their own versions of these methods:
 * <ul>
 *  <li>{@link #authenticate(String, String, DomainBareJid, String, EntityBareJid, SSLSession)} -- Initiate authentication stanza using the
 *  deprecated method.</li>
 *  <li>{@link #authenticate(String, DomainBareJid, CallbackHandler, EntityBareJid, SSLSession)} -- Initiate authentication stanza
 *  using the CallbackHandler method.</li>
 *  <li>{@link #challengeReceived(String, boolean)} -- Handle a challenge from the server.</li>
 * </ul>
 *
 * @author Jay Kline
 * @author Florian Schmaus
 */
public abstract class SASLMechanism implements Comparable<SASLMechanism> {

    public static final String CRAMMD5 = "CRAM-MD5";
    public static final String DIGESTMD5 = "DIGEST-MD5";
    public static final String EXTERNAL = "EXTERNAL";
    public static final String GSSAPI = "GSSAPI";
    public static final String PLAIN = "PLAIN";

    /**
     * Boolean indicating if SASL negotiation has finished and was successful.
     */
    private boolean authenticationSuccessful;

    /**
     * Either of type {@link SmackSaslException},{@link SASLErrorException}, {@link NotConnectedException} or
     * {@link InterruptedException}.
     */
    private Exception exception;

    protected XMPPConnection connection;

    protected ConnectionConfiguration connectionConfiguration;

    /**
     * Then authentication identity (authcid). RFC 6120 § 6.3.7 informs us that some SASL mechanisms use this as a
     * "simple user name". But the exact form is a matter of the mechanism and that it does not necessarily map to an
     * localpart. But it usually is the localpart of the client JID, although sometimes other formats are used (e.g. the
     * full JID).
     * <p>
     * Not to be confused with the authzid (see RFC 6120 § 6.3.8).
     * </p>
     */
    protected String authenticationId;

    /**
     * The authorization identifier (authzid).
     * This is always a bare Jid, but can be null.
     */
    protected EntityBareJid authorizationId;

    /**
     * The name of the XMPP service
     */
    protected DomainBareJid serviceName;

    /**
     * The users password
     */
    protected String password;
    protected String host;

    /**
     * The used SSL/TLS session (if any).
     */
    protected SSLSession sslSession;

    /**
     * Builds and sends the <code>auth</code> stanza to the server. Note that this method of
     * authentication is not recommended, since it is very inflexible. Use
     * {@link #authenticate(String, DomainBareJid, CallbackHandler, EntityBareJid, SSLSession)} whenever possible.
     *
     * Explanation of auth stanza:
     *
     * The client authentication stanza needs to include the digest-uri of the form: xmpp/serviceName
     * From RFC-2831:
     * digest-uri = "digest-uri" "=" digest-uri-value
     * digest-uri-value = serv-type "/" host [ "/" serv-name ]
     *
     * digest-uri:
     * Indicates the principal name of the service with which the client
     * wishes to connect, formed from the serv-type, host, and serv-name.
     * For example, the FTP service
     * on "ftp.example.com" would have a "digest-uri" value of "ftp/ftp.example.com"; the SMTP
     * server from the example above would have a "digest-uri" value of
     * "smtp/mail3.example.com/example.com".
     *
     * host:
     * The DNS host name or IP address for the service requested. The DNS host name
     * must be the fully-qualified canonical name of the host. The DNS host name is the
     * preferred form; see notes on server processing of the digest-uri.
     *
     * serv-name:
     * Indicates the name of the service if it is replicated. The service is
     * considered to be replicated if the client's service-location process involves resolution
     * using standard DNS lookup operations, and if these operations involve DNS records (such
     * as SRV, or MX) which resolve one DNS name into a set of other DNS names. In this case,
     * the initial name used by the client is the "serv-name", and the final name is the "host"
     * component. For example, the incoming mail service for "example.com" may be replicated
     * through the use of MX records stored in the DNS, one of which points at an SMTP server
     * called "mail3.example.com"; it's "serv-name" would be "example.com", it's "host" would be
     * "mail3.example.com". If the service is not replicated, or the serv-name is identical to
     * the host, then the serv-name component MUST be omitted
     *
     * digest-uri verification is needed for ejabberd 2.0.3 and higher
     *
     * @param username the username of the user being authenticated.
     * @param host the hostname where the user account resides.
     * @param serviceName the xmpp service location - used by the SASL client in digest-uri creation
     * serviceName format is: host [ "/" serv-name ] as per RFC-2831
     * @param password the password for this account.
     * @param authzid the optional authorization identity.
     * @param sslSession the optional SSL/TLS session (if one was established)
     * @throws SmackSaslException if a SASL related error occurs.
     * @throws NotConnectedException if the XMPP connection is not connected.
     * @throws InterruptedException if the calling thread was interrupted.
     */
    public final void authenticate(String username, String host, DomainBareJid serviceName, String password,
                    EntityBareJid authzid, SSLSession sslSession)
                    throws SmackSaslException, NotConnectedException, InterruptedException {
        this.authenticationId = username;
        this.host = host;
        this.serviceName = serviceName;
        this.password = password;
        this.authorizationId = authzid;
        this.sslSession = sslSession;
        assert authorizationId == null || authzidSupported();
        authenticateInternal();
        authenticate();
    }

    protected void authenticateInternal() throws SmackSaslException {
    }

    /**
     * Builds and sends the <code>auth</code> stanza to the server. The callback handler will handle
     * any additional information, such as the authentication ID or realm, if it is needed.
     *
     * @param host     the hostname where the user account resides.
     * @param serviceName the xmpp service location
     * @param cbh      the CallbackHandler to obtain user information.
     * @param authzid the optional authorization identity.
     * @param sslSession the optional SSL/TLS session (if one was established)
     * @throws SmackSaslException if a SASL related error occurs.
     * @throws NotConnectedException if the XMPP connection is not connected.
     * @throws InterruptedException if the calling thread was interrupted.
     */
    public void authenticate(String host, DomainBareJid serviceName, CallbackHandler cbh, EntityBareJid authzid, SSLSession sslSession)
                    throws SmackSaslException, NotConnectedException, InterruptedException {
        this.host = host;
        this.serviceName = serviceName;
        this.authorizationId = authzid;
        this.sslSession = sslSession;
        assert authorizationId == null || authzidSupported();
        authenticateInternal(cbh);
        authenticate();
    }

    protected abstract void authenticateInternal(CallbackHandler cbh) throws SmackSaslException;

    private void authenticate() throws SmackSaslException, NotConnectedException, InterruptedException {
        byte[] authenticationBytes = getAuthenticationText();
        String authenticationText;
        // Some SASL mechanisms do return an empty array (e.g. EXTERNAL from javax), so check that
        // the array is not-empty. Mechanisms are allowed to return either 'null' or an empty array
        // if there is no authentication text.
        if (authenticationBytes != null && authenticationBytes.length > 0) {
            authenticationText = Base64.encodeToString(authenticationBytes);
        } else {
            // RFC6120 6.4.2 "If the initiating entity needs to send a zero-length initial response,
            // it MUST transmit the response as a single equals sign character ("="), which
            // indicates that the response is present but contains no data."
            authenticationText = "=";
        }
        // Send the authentication to the server
        connection.sendNonza(new AuthMechanism(getName(), authenticationText));
    }

    /**
     * Should return the initial response of the SASL mechanism. The returned byte array will be
     * send base64 encoded to the server. SASL mechanism are free to return <code>null</code> or an
     * empty array here.
     *
     * @return the initial response or null
     * @throws SmackSaslException if a SASL specific error occured.
     */
    protected abstract byte[] getAuthenticationText() throws SmackSaslException;

    /**
     * The server is challenging the SASL mechanism for the stanza he just sent. Send a
     * response to the server's challenge.
     *
     * @param challengeString a base64 encoded string representing the challenge.
     * @param finalChallenge true if this is the last challenge send by the server within the success stanza
     * @throws SmackSaslException if a SASL related error occurs.
     * @throws InterruptedException if the connection is interrupted
     * @throws NotConnectedException if the XMPP connection is not connected.
     */
    public final void challengeReceived(String challengeString, boolean finalChallenge) throws SmackSaslException, InterruptedException, NotConnectedException {
        byte[] challenge = Base64.decode((challengeString != null && challengeString.equals("=")) ? "" : challengeString);
        byte[] response = evaluateChallenge(challenge);
        if (finalChallenge) {
            return;
        }

        Response responseStanza;
        if (response == null) {
            responseStanza = new Response();
        }
        else {
            responseStanza = new Response(Base64.encodeToString(response));
        }

        // Send the authentication to the server
        connection.sendNonza(responseStanza);
    }

    /**
     * Evaluate the SASL challenge.
     *
     * @param challenge challenge to evaluate.
     *
     * @return null.
     * @throws SmackSaslException If a SASL related error occurs.
     */
    protected byte[] evaluateChallenge(byte[] challenge) throws SmackSaslException {
        return null;
    }

    @Override
    public final int compareTo(SASLMechanism other) {
        Integer ourPriority = getPriority();
        return Integer.compare(ourPriority, other.getPriority());
    }

    /**
     * Returns the common name of the SASL mechanism. E.g.: PLAIN, DIGEST-MD5 or GSSAPI.
     *
     * @return the common name of the SASL mechanism.
     */
    public abstract String getName();

    /**
     * Get the priority of this SASL mechanism. Lower values mean higher priority.
     *
     * @return the priority of this SASL mechanism.
     */
    public abstract int getPriority();

    /**
     * Check if the SASL mechanism was successful and if it was, then mark it so.
     *
     * @throws SmackSaslException in case of an SASL error.
     */
    public final void afterFinalSaslChallenge() throws SmackSaslException {
        checkIfSuccessfulOrThrow();

        authenticationSuccessful = true;
    }

    protected abstract void checkIfSuccessfulOrThrow() throws SmackSaslException;

    public SASLMechanism instanceForAuthentication(XMPPConnection connection, ConnectionConfiguration connectionConfiguration) {
        SASLMechanism saslMechansim = newInstance();
        saslMechansim.connection = connection;
        saslMechansim.connectionConfiguration = connectionConfiguration;
        return saslMechansim;
    }

    public boolean authzidSupported() {
        return false;
    }

    public boolean isAuthenticationSuccessful() {
        return authenticationSuccessful;
    }

    public boolean isFinished() {
        return isAuthenticationSuccessful() || exception != null;
    }

    public void throwExceptionIfRequired() throws SmackSaslException, SASLErrorException, NotConnectedException,
                    InterruptedException, NoResponseException {
        if (exception != null) {
            if (exception instanceof SmackSaslException) {
                throw (SmackSaslException) exception;
            } else if (exception instanceof SASLErrorException) {
                throw (SASLErrorException) exception;
            } else if (exception instanceof NotConnectedException) {
                throw (NotConnectedException) exception;
            } else if (exception instanceof InterruptedException) {
                throw (InterruptedException) exception;
            } else {
                throw new IllegalStateException("Unexpected exception type", exception);
            }
        }

        if (!authenticationSuccessful) {
            throw NoResponseException.newWith(connection, "successful SASL authentication");
        }
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    protected abstract SASLMechanism newInstance();

    protected static byte[] toBytes(String string) {
        return StringUtils.toUtf8Bytes(string);
    }

    /**
     * SASLprep the given String. The resulting String is in UTF-8.
     *
     * @param string the String to sasl prep.
     * @return the given String SASL preped
     * @see <a href="http://tools.ietf.org/html/rfc4013">RFC 4013 - SASLprep: Stringprep Profile for User Names and Passwords</a>
     */
    protected static String saslPrep(String string) {
        return Normalizer.normalize(string, Form.NFKC);
    }

    @Override
    public final String toString() {
        return "SASL Mech: " + getName() + ", Prio: " + getPriority();
    }
}
