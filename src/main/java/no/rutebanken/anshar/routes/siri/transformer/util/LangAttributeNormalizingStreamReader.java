package no.rutebanken.anshar.routes.siri.transformer.util;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;
import java.util.Set;

/**
 * Some SIRI producers (e.g. Traffic Report / Chaos) emit a bare {@code lang="xx"} attribute on
 * Summary/Description/Prompt elements instead of the standards-compliant {@code xml:lang="xx"}.
 * The SIRI model only binds the reserved XML namespace variant of "lang", so JAXB silently drops
 * the bare attribute during unmarshalling and the language information is lost.
 * <p>
 * This wraps the underlying reader so a bare "lang" attribute on those elements is reported as
 * belonging to the XML namespace, letting JAXB bind it as expected. Every other attribute -
 * including "lang" attributes elsewhere, such as inside Extensions - is left untouched.
 */
public class LangAttributeNormalizingStreamReader extends StreamReaderDelegate {

    private static final Set<String> LANG_AWARE_ELEMENTS = Set.of("Summary", "Description", "Prompt");
    private static final String LANG_ATTRIBUTE_NAME = "lang";

    public LangAttributeNormalizingStreamReader(XMLStreamReader reader) {
        super(reader);
    }

    @Override
    public QName getAttributeName(int index) {
        QName name = super.getAttributeName(index);
        if (isBareLangAttribute(name)) {
            return new QName(XMLConstants.XML_NS_URI, LANG_ATTRIBUTE_NAME, XMLConstants.XML_NS_PREFIX);
        }
        return name;
    }

    @Override
    public String getAttributeNamespace(int index) {
        if (isBareLangAttribute(super.getAttributeName(index))) {
            return XMLConstants.XML_NS_URI;
        }
        return super.getAttributeNamespace(index);
    }

    private boolean isBareLangAttribute(QName attributeName) {
        String namespaceUri = attributeName.getNamespaceURI();
        return (namespaceUri == null || namespaceUri.isEmpty())
                && LANG_ATTRIBUTE_NAME.equals(attributeName.getLocalPart())
                && LANG_AWARE_ELEMENTS.contains(getLocalName());
    }
}
