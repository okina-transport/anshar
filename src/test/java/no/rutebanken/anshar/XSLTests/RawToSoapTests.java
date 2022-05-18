package no.rutebanken.anshar.XSLTests;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import uk.org.siri.siri20.Siri;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.*;

public class RawToSoapTests extends SpringBootBaseTest {


    private Source xslDoc = new StreamSource("src/main/resources/xsl/siri_raw_soap.xsl");

    @Test
    public void stopPointsDiscoveryTest() throws IOException, TransformerException, JAXBException, XMLStreamException, ParserConfigurationException, SAXException {

        TransformerFactory tFactory=TransformerFactory.newInstance();

        Source xmlDoc = new StreamSource("src/test/resources/discoveryTest/stop_points_raw_to_soap_test.xml");
        String outputFileName = "src/test/resources/discoveryTest/results/result_raw_to_soap_SP.xml";

        try {

            OutputStream htmlFile = new FileOutputStream(outputFileName);
            Transformer transform = tFactory.newTransformer(xslDoc);
            transform.transform(xmlDoc, new StreamResult(htmlFile));

            // XML file generated. Now trying to read it
            File file = new File(outputFileName);
            checkXmlResult(file, Arrays.asList("Stop_8", "Stop_66", "Stop_78"), "StopPointRef");

            file.delete();


        }catch (Exception e){
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    public void linesDiscoveryTest() throws IOException, TransformerException, SAXException, XMLStreamException, ParserConfigurationException {

        TransformerFactory tFactory=TransformerFactory.newInstance();
        Source xmlDoc = new StreamSource("src/test/resources/discoveryTest/lines_raw_to_soap_test.xml");

        String outputFileName = "src/test/resources/discoveryTest/results/result_raw_to_soap_lines.xml";

        try {

            OutputStream htmlFile = new FileOutputStream(outputFileName);
            Transformer transform = tFactory.newTransformer(xslDoc);
            transform.transform(xmlDoc, new StreamResult(htmlFile));

            // XML file generated. Now trying to read it
            File file = new File(outputFileName);

            checkXmlResult(file, Arrays.asList("N", "L2", "L1"), "LineRef");
            file.delete();

        }catch (Exception e){
            e.printStackTrace();
            throw e;
        }
    }

    private void checkXmlResult(File file,  List<String> expectedValues, String tagName ) throws ParserConfigurationException, SAXException, IOException {
        Document document = parseXML(file);
        NodeList idLists = document.getElementsByTagName(tagName);

        //List<String> lineList = Arrays.asList("N", "L2", "L1");

        int nbOfLines = 0;
        for (int i = 0; i < idLists.getLength(); i++) {
            Node node = idLists.item(i);
            nbOfLines++;
            assertTrue(expectedValues.contains(node.getFirstChild().getNodeValue()));
        }
        assertEquals(nbOfLines,expectedValues.size());
    }

    private Document parseXML(File file) throws IOException, ParserConfigurationException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(FileUtils.readFileToByteArray(file));
        return builder.parse(byteArrayInputStream);
    }
}
