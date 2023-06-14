package no.rutebanken.anshar.data.util;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import no.rutebanken.anshar.data.frGeneralMessageStructure.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import uk.org.siri.siri21.Siri;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;

public class CustomSiriXml {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static JAXBContext jaxbContext;

    public CustomSiriXml() {
    }

    private static void init() throws JAXBException {
        if (jaxbContext == null) {
            jaxbContext = JAXBContext.newInstance(new Class[]{Siri.class, Content.class});
        }

    }

    public static Siri parseXml(String xml) throws JAXBException, XMLStreamException {
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        XMLInputFactory xmlif = XMLInputFactory.newInstance();
        XMLStreamReader xmler = xmlif.createXMLStreamReader(new StringReader(xml));
        return (Siri) jaxbUnmarshaller.unmarshal(xmler);
    }

    public static Siri parseXml(InputStream inputStream) throws JAXBException, XMLStreamException {
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        XMLInputFactory xmlif = XMLInputFactory.newInstance();
        XMLStreamReader xmler = xmlif.createXMLStreamReader(inputStream);
        return (Siri) jaxbUnmarshaller.unmarshal(xmler);
    }

    public static String toXml(Siri siri) throws JAXBException {
        return toXml(siri, (NamespacePrefixMapper) null);
    }

    public static String toXml(Siri siri, NamespacePrefixMapper customNamespacePrefixMapper) throws JAXBException {
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();


        if (customNamespacePrefixMapper != null) {
            jaxbMarshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", customNamespacePrefixMapper);
        }

        StringWriter sw = new StringWriter();
        jaxbMarshaller.marshal(siri, sw);
        return sw.toString();
    }

    public static void toXml(Siri siri, NamespacePrefixMapper customNamespacePrefixMapper, OutputStream out) throws JAXBException, IOException {
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
        if (customNamespacePrefixMapper != null) {
            jaxbMarshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", customNamespacePrefixMapper);
        }

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        jaxbMarshaller.marshal(siri, byteArrayOutputStream);
        doLastModifications(out, byteArrayOutputStream);
    }

    private static void doLastModifications(OutputStream outputStreamOut, OutputStream byteArrayOutputIn) {
        String s = null;
        try {
            s = new String(((ByteArrayOutputStream) byteArrayOutputIn).toByteArray(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        s = s.replace("Content xmlns=\"http://www.w3.org/2001/XMLSchema-instance\"", "Content ");

        s = s.replace("<ns6:", "<");
        s = s.replace("</ns6:", "</");
        s = s.replace("xmlns:ns6=\"http://www.siri.org.uk/siri\"", "");
        s = s.replace("xmlns=\"\"", "");
        Document document = stringToDocument(s);
        Node item = document.getElementsByTagName("Siri").item(0);

        try {
            if (item != null) {
                Element element = (Element) item;
                element.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
                outputStreamOut.write(documentToString(document).getBytes("UTF-8"));
            } else {
                outputStreamOut.write(s.getBytes("UTF-8"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Document stringToDocument(String strXml) {

        Document doc = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            StringReader strReader = new StringReader(strXml);
            InputSource is = new InputSource(strReader);
            doc = (Document) builder.parse(is);
            doc.setXmlStandalone(false);
        } catch (Exception e) {
            return null;
        }

        return doc;
    }

    private static String documentToString(Document doc) {
        String output = null;
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            StringWriter writer = new StringWriter();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            output = writer.getBuffer().toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return output;
    }

    static {
        try {
            init();
        } catch (JAXBException var1) {
            throw new InstantiationError();
        }
    }
}
