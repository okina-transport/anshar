package no.rutebanken.anshar.XSLTests;

import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import uk.org.siri.siri21.*;

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
import java.io.*;
import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class RawToSoapTests extends SpringBootBaseTest {


    private Source xslDoc = new StreamSource("src/main/resources/xsl/siri_raw_soap.xsl");

    @Test
    public void stopPointsDiscoveryTest() throws IOException, TransformerException, JAXBException, XMLStreamException, ParserConfigurationException, SAXException {

        TransformerFactory tFactory = TransformerFactory.newInstance();

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


        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    public void checkStatusTest() throws JAXBException, FileNotFoundException, TransformerException {
        SubscriptionSetup subscription = new SubscriptionSetup();
        subscription.setVersion("2.0");
        subscription.setRequestorRef("TESTREQ");

        Siri checkStatusRequest = SiriObjectFactory.createCheckStatusRequest(subscription);
        String body = CustomSiriXml.toXml(checkStatusRequest);
        String xslBody = CustomSiriXml.rawToSoap(body);
        assertTrue(xslBody.contains("<siri:CheckStatus xmlns:siri=\"http://wsdl.siri.org.uk\">"));
        assertTrue(xslBody.contains("<RequestorRef xmlns=\"http://www.siri.org.uk/siri\">TESTREQ</RequestorRef>"));
    }

    @Test
    public void subscriptionRefTest() throws JAXBException, FileNotFoundException, TransformerException {
        SubscriptionSetup subscription = new SubscriptionSetup();
        subscription.setVersion("2.0");
        subscription.setRequestorRef("TESTREQ");

        Siri siri = new Siri();
        ServiceDelivery serviceDel = new ServiceDelivery();
        EstimatedTimetableDeliveryStructure et = new EstimatedTimetableDeliveryStructure();
        et.setVersion("2.0");
        
        EstimatedVersionFrameStructure struct = new EstimatedVersionFrameStructure();
        EstimatedVehicleJourney estijourney = new EstimatedVehicleJourney();
        NaturalLanguageStringStructure publishedName = new NaturalLanguageStringStructure();
        publishedName.setValue("lineName");
        estijourney.getPublishedLineNames().add(publishedName);
        struct.getEstimatedVehicleJourneies().add(estijourney);
        et.getEstimatedJourneyVersionFrames().add(struct);
        // et.setResponseTimestamp(ZonedDateTime.now());
        RequestorRef reqRef = new RequestorRef();
        reqRef.setValue("TESTREQ");
        et.setSubscriberRef(reqRef);

        serviceDel.getEstimatedTimetableDeliveries().add(et);
        siri.setServiceDelivery(serviceDel);


        String body = CustomSiriXml.toXml(siri);
        System.out.println(body);
        String xslBody = CustomSiriXml.subscriptionRawToSoap(body);
        assertTrue(xslBody.contains("TESTREQ"));

    }

    @Test
    public void linesDiscoveryTest() throws IOException, TransformerException, SAXException, XMLStreamException, ParserConfigurationException {

        TransformerFactory tFactory = TransformerFactory.newInstance();
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

        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    private void checkXmlResult(File file, List<String> expectedValues, String tagName) throws ParserConfigurationException, SAXException, IOException {
        Document document = parseXML(file);
        NodeList idLists = document.getElementsByTagName(tagName);

        //List<String> lineList = Arrays.asList("N", "L2", "L1");

        int nbOfLines = 0;
        for (int i = 0; i < idLists.getLength(); i++) {
            Node node = idLists.item(i);
            nbOfLines++;
            assertTrue(expectedValues.contains(node.getFirstChild().getNodeValue()));
        }
        assertEquals(nbOfLines, expectedValues.size());
    }

    private Document parseXML(File file) throws IOException, ParserConfigurationException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(FileUtils.readFileToByteArray(file));
        return builder.parse(byteArrayInputStream);
    }
}
