/**
 *
 * Copyright 2018-2019 Florian Schmaus
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
package org.jivesoftware.smack.packet;

import javax.xml.namespace.QName;

public interface FullyQualifiedElement extends NamedElement {

    /**
     * Returns the root element XML namespace.
     *
     * @return the namespace.
     */
    String getNamespace();

    default QName getQName() {
        String namespaceURI = getNamespace();
        String localPart = getElementName();
        return new QName(namespaceURI, localPart);
    }

    /**
     * Returns the xml:lang of this XML element, or null if one has not been set.
     *
     * @return the xml:lang of this XML element, or null.
     */
    default String getLanguage() {
        return null;
    }
}
