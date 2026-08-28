package no.rutebanken.anshar.XSLTests;

import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.NaturalLanguageStringStructure;
import uk.org.siri.siri21.RequestorRef;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SubscriptionRefStructure;
import uk.org.siri.siri21.VehicleMonitoringDeliveryStructure;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

class RawToSoapTests extends SpringBootBaseTest {

    private final Source xslDoc = new StreamSource("src/main/resources/xsl/siri_raw_soap.xsl");

    @Test
    void stopPointsDiscoveryTest() throws IOException, TransformerException, ParserConfigurationException, SAXException {

        TransformerFactory tFactory = TransformerFactory.newInstance();

        Source xmlDoc = new StreamSource("src/test/resources/discoveryTest/stop_points_raw_to_soap_test.xml");
        String outputFileName = "src/test/resources/discoveryTest/results/result_raw_to_soap_SP.xml";

        OutputStream htmlFile = new FileOutputStream(outputFileName);
        Transformer transform = tFactory.newTransformer(xslDoc);
        transform.transform(xmlDoc, new StreamResult(htmlFile));

        // XML file generated. Now trying to read it
        File file = new File(outputFileName);
        checkXmlResult(file, Arrays.asList("Stop_8", "Stop_66", "Stop_78"), "StopPointRef");

        file.delete();
    }

    @Test
    void checkStatusTest() throws JAXBException, FileNotFoundException, TransformerException {
        SubscriptionSetup subscription = new SubscriptionSetup();
        subscription.setVersion("2.0");
        subscription.setRequestorRef("TESTREQ");

        Siri checkStatusRequest = SiriObjectFactory.createCheckStatusRequest(subscription);
        String body = CustomSiriXml.toXml(checkStatusRequest);
        String xslBody = CustomSiriXml.rawToSoap(body);
        Assertions.assertTrue(xslBody.contains("<siri:CheckStatus xmlns:siri=\"http://wsdl.siri.org.uk\">"));
        Assertions.assertTrue(xslBody.contains("<RequestorRef xmlns=\"http://www.siri.org.uk/siri\">TESTREQ</RequestorRef>"));
    }

    @Test
    void subscriptionRefTest() throws JAXBException, FileNotFoundException, TransformerException {
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

        RequestorRef reqRef = new RequestorRef();
        reqRef.setValue("TESTREQ");
        et.setSubscriberRef(reqRef);

        serviceDel.getEstimatedTimetableDeliveries().add(et);
        siri.setServiceDelivery(serviceDel);


        String body = CustomSiriXml.toXml(siri);

        String xslBody = CustomSiriXml.subscriptionRawToSoap(body);
        Assertions.assertTrue(xslBody.contains("TESTREQ"));
    }

    @Test
    void estimatedTimetableNotificationContainsSubscriberSubscriptionAndStatusTest() throws JAXBException, FileNotFoundException, TransformerException {

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

        RequestorRef subscriberRef = new RequestorRef();
        subscriberRef.setValue("SUBSCRIBER_1");
        et.setSubscriberRef(subscriberRef);

        SubscriptionRefStructure subscriptionRef = new SubscriptionRefStructure();
        subscriptionRef.setValue("SUBSCRIPTION_1");
        et.setSubscriptionRef(subscriptionRef);

        et.setStatus(true);
        serviceDel.setStatus(true);

        serviceDel.getEstimatedTimetableDeliveries().add(et);
        siri.setServiceDelivery(serviceDel);

        String body = CustomSiriXml.toXml(siri);
        String xslBody = CustomSiriXml.subscriptionRawToSoap(body);

        Assertions.assertTrue(xslBody.contains("SUBSCRIBER_1"), "SubscriberRef is missing from the SOAP notification");
        Assertions.assertTrue(xslBody.contains("SUBSCRIPTION_1"), "SubscriptionRef is missing from the SOAP notification");
        // one Status at ServiceDelivery level (ServiceDeliveryInfo) and one at the Delivery level (Notification)
        Assertions.assertEquals(1, countStatusTrueOccurrences(xslBody), "Status is missing from the SOAP notification (ServiceDeliveryInfo and/or Notification)");
    }

    @Test
    void vehicleMonitoringNotificationContainsSubscriberSubscriptionAndStatusTest() throws JAXBException, FileNotFoundException, TransformerException {

        Siri siri = new Siri();
        ServiceDelivery serviceDel = new ServiceDelivery();
        VehicleMonitoringDeliveryStructure vm = new VehicleMonitoringDeliveryStructure();
        vm.setVersion("2.0");

        RequestorRef subscriberRef = new RequestorRef();
        subscriberRef.setValue("SUBSCRIBER_2");
        vm.setSubscriberRef(subscriberRef);

        SubscriptionRefStructure subscriptionRef = new SubscriptionRefStructure();
        subscriptionRef.setValue("SUBSCRIPTION_2");
        vm.setSubscriptionRef(subscriptionRef);

        vm.setStatus(true);
        serviceDel.setStatus(true);

        serviceDel.getVehicleMonitoringDeliveries().add(vm);
        siri.setServiceDelivery(serviceDel);

        String body = CustomSiriXml.toXml(siri);
        String xslBody = CustomSiriXml.subscriptionRawToSoap(body);

        Assertions.assertTrue(xslBody.contains("SUBSCRIBER_2"), "SubscriberRef is missing from the SOAP notification");
        Assertions.assertTrue(xslBody.contains("SUBSCRIPTION_2"), "SubscriptionRef is missing from the SOAP notification");
        // one Status at ServiceDelivery level (ServiceDeliveryInfo) and one at the Delivery level (Notification)
        Assertions.assertEquals(1, countStatusTrueOccurrences(xslBody), "Status is missing from the SOAP notification (ServiceDeliveryInfo and/or Notification)");
    }

    @Test
    void linesDiscoveryTest() throws IOException, TransformerException, SAXException, ParserConfigurationException {

        TransformerFactory tFactory = TransformerFactory.newInstance();
        Source xmlDoc = new StreamSource("src/test/resources/discoveryTest/lines_raw_to_soap_test.xml");

        String outputFileName = "src/test/resources/discoveryTest/results/result_raw_to_soap_lines.xml";

        OutputStream htmlFile = new FileOutputStream(outputFileName);
        Transformer transform = tFactory.newTransformer(xslDoc);
        transform.transform(xmlDoc, new StreamResult(htmlFile));

        // XML file generated. Now trying to read it
        File file = new File(outputFileName);

        checkXmlResult(file, Arrays.asList("N", "L2", "L1"), "LineRef");
        file.delete();
    }

    private static final Pattern STATUS_TRUE_PATTERN = Pattern.compile(
            "<([\\w:]*Delivery)\\b[^>]*>(?:(?!</?\\1\\b)[\\s\\S])*?<[\\w:]*Status\\b[^>]*>true</[\\w:]*Status>(?:(?!</?\\1\\b)[\\s\\S])*?</\\1>");

    private int countStatusTrueOccurrences(String xml) {
        java.util.regex.Matcher matcher = STATUS_TRUE_PATTERN.matcher(xml);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private void checkXmlResult(File file, List<String> expectedValues, String tagName) throws ParserConfigurationException, SAXException, IOException {
        Document document = parseXML(file);
        NodeList idLists = document.getElementsByTagName(tagName);

               int nbOfLines = 0;
        for (int i = 0; i < idLists.getLength(); i++) {
            Node node = idLists.item(i);
            nbOfLines++;
            Assertions.assertTrue(expectedValues.contains(node.getFirstChild().getNodeValue()));
        }
        Assertions.assertEquals(nbOfLines, expectedValues.size());
    }

    private Document parseXML(File file) throws IOException, ParserConfigurationException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(FileUtils.readFileToByteArray(file));
        return builder.parse(byteArrayInputStream);
    }
}
