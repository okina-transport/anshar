package no.rutebanken.anshar.XSLTests;

import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.health.LivenessReadinessRoute;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import org.apache.commons.io.FileUtils;
import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.Siri;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SoapToRawTests extends SpringBootBaseTest {


    private Source xslDoc = new StreamSource("src/main/resources/xsl/siri_soap_raw.xsl");


    @Autowired
    private LivenessReadinessRoute livenessReadinessRoute;

    @Test
    public void testCheckStatusResponse() throws XMLStreamException, JAXBException, FileNotFoundException, TransformerException {

        String body = """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                      <s:Body>
                        <CheckStatusResponse xmlns="http://wsdl.siri.org.uk">
                          <CheckStatusAnswerInfo xmlns="">
                            <ResponseTimestamp xmlns="http://www.siri.org.uk/siri">2025-11-17T15:27:29.3250644Z</ResponseTimestamp>
                            <ProducerRef xmlns="http://www.siri.org.uk/siri">qom</ProducerRef>
                            <RequestMessageRef xmlns="http://www.siri.org.uk/siri">26c9172f-1535-491f-842c-7f82a613985c</RequestMessageRef>
                          </CheckStatusAnswerInfo>
                          <Answer xmlns="">
                            <Status xmlns="http://www.siri.org.uk/siri">true</Status>
                            <ServiceStartedTime xmlns="http://www.siri.org.uk/siri">2025-11-13T16:24:16.3597018Z</ServiceStartedTime>
                          </Answer>
                        </CheckStatusResponse>
                      </s:Body>
                    </s:Envelope>               
                """;

        boolean result = livenessReadinessRoute.isStatusOk(body);
        Assertions.assertTrue(result);

    }

    @Test
    public void stopPointsDiscoveryTest() throws IOException, TransformerException, JAXBException, XMLStreamException {

        TransformerFactory tFactory = TransformerFactory.newInstance();

        Source xmlDoc = new StreamSource("src/test/resources/discoveryTest/stop_points_soap_to_xml_test.xml");
        String outputFileName = "src/test/resources/discoveryTest/results/resultSP.xml";

        try {

            OutputStream htmlFile = new FileOutputStream(outputFileName);
            Transformer transform = tFactory.newTransformer(xslDoc);
            transform.transform(xmlDoc, new StreamResult(htmlFile));

            // XML file generated. Now trying to read it
            File file = new File(outputFileName);
            Siri incoming = SiriValueTransformer.parseXml(new ByteArrayInputStream(FileUtils.readFileToByteArray(file)));

            assertNotNull(incoming.getStopPointsRequest());
            file.delete();


        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    public void fmSOAPTest() throws JAXBException, FileNotFoundException, TransformerException, XMLStreamException {

        String soapFMmsg = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                    <soapenv:Header/>
                    <soapenv:Body>
                        <NotifyFacilityMonitoring xmlns="http://wsdl.siri.org.uk">
                            <ServiceDeliveryInfo xmlns="">
                                <ResponseTimestamp xmlns="http://www.siri.org.uk/siri">2025-09-30T09:58:25.163955921+02:00</ResponseTimestamp>
                                <ProducerRef xmlns="http://www.siri.org.uk/siri">OKI</ProducerRef>
                                <RequestMessageRef xmlns="http://www.siri.org.uk/siri">OKI</RequestMessageRef>
                                <ResponseMessageIdentifier xmlns="http://www.siri.org.uk/siri">c3d12c8d-6812-40ee-9a6d-29ca0921f5ab</ResponseMessageIdentifier>
                            </ServiceDeliveryInfo>
                            <Notification xmlns="">
                                <siri:FacilityMonitoringDelivery xmlns:siri="http://www.siri.org.uk/siri" version="2.1">
                                    <siri:ResponseTimestamp>2025-09-30T09:58:25.163955921+02:00</siri:ResponseTimestamp>
                                    <siri:RequestMessageRef>OKI</siri:RequestMessageRef>
                                    <FacilityCondition xmlns="http://www.siri.org.uk/siri">
                                        <FacilityRef>FR:44109:Parking:39:LOC</FacilityRef>
                                        <FacilityStatus>
                                            <Status>available</Status>
                                            <Description xml:lang="en">rentingAvailable</Description>
                                            <Description xml:lang="en">returningAvailable</Description>
                                        </FacilityStatus>
                                        <MonitoredCounting>
                                            <CountingType>availabilityCount</CountingType>
                                            <CountedFeatureUnit>vehicles</CountedFeatureUnit>
                                            <Count>16</Count>
                                        </MonitoredCounting>
                                        <MonitoredCounting>
                                            <CountingType>availabilityCount</CountingType>
                                            <CountedFeatureUnit>vehicles</CountedFeatureUnit>
                                            <TypeOfCountedFeature>
                                                <TypeOfValueCode>mechanical</TypeOfValueCode>
                                            </TypeOfCountedFeature>
                                            <Count>16</Count>
                                        </MonitoredCounting>
                                        <MonitoredCounting>
                                            <CountingType>outOfOrderCount</CountingType>
                                            <CountedFeatureUnit>vehicles</CountedFeatureUnit>
                                            <Count>0</Count>
                                        </MonitoredCounting>
                                        <MonitoredCounting>
                                            <CountingType>availabilityCount</CountingType>
                                            <CountedFeatureUnit>bays</CountedFeatureUnit>
                                            <Count>8</Count>
                                        </MonitoredCounting>
                                        <MonitoredCounting>
                                            <CountingType>outOfOrderCount</CountingType>
                                            <CountedFeatureUnit>bays</CountedFeatureUnit>
                                            <Count>0</Count>
                                        </MonitoredCounting>
                                        <ValidityPeriod>
                                            <StartTime>2025-09-11T16:01:52+02:00</StartTime>
                                        </ValidityPeriod>
                                    </FacilityCondition>
                                    <FacilityCondition xmlns="http://www.siri.org.uk/siri">
                                        <FacilityRef>FR:44109:Parking:8:LOC</FacilityRef>
                                        <FacilityStatus>
                                            <Status>available</Status>
                                            <Description xml:lang="en">rentingAvailable</Description>
                                            <Description xml:lang="en">returningAvailable</Description>
                                        </FacilityStatus>
                                        <MonitoredCounting>
                                            <CountingType>availabilityCount</CountingType>
                                            <CountedFeatureUnit>vehicles</CountedFeatureUnit>
                                            <Count>21</Count>
                                        </MonitoredCounting>
                                        <MonitoredCounting>
                                            <CountingType>availabilityCount</CountingType>
                                            <CountedFeatureUnit>vehicles</CountedFeatureUnit>
                                            <TypeOfCountedFeature>
                                                <TypeOfValueCode>mechanical</TypeOfValueCode>
                                            </TypeOfCountedFeature>
                                            <Count>21</Count>
                                        </MonitoredCounting>
                                        <MonitoredCounting>
                                            <CountingType>outOfOrderCount</CountingType>
                                            <CountedFeatureUnit>vehicles</CountedFeatureUnit>
                                            <Count>0</Count>
                                        </MonitoredCounting>
                                        <MonitoredCounting>
                                            <CountingType>availabilityCount</CountingType>
                                            <CountedFeatureUnit>bays</CountedFeatureUnit>
                                            <Count>3</Count>
                                        </MonitoredCounting>
                                        <MonitoredCounting>
                                            <CountingType>outOfOrderCount</CountingType>
                                            <CountedFeatureUnit>bays</CountedFeatureUnit>
                                            <Count>0</Count>
                                        </MonitoredCounting>
                                        <ValidityPeriod>
                                            <StartTime>2025-09-11T16:02:50+02:00</StartTime>
                                        </ValidityPeriod>
                                    </FacilityCondition>
                                    <FacilityCondition xmlns="http://www.siri.org.uk/siri">
                                        <FacilityRef>FR:44109:Parking:63:LOC</FacilityRef>
                                        <FacilityStatus>
                                            <Status>available</Status>
                                            <Description xml:lang="en">rentingAvailable</Description>
                                            <Description xml:lang="en">returningAvailable</Description>
                                        </FacilityStatus>
                                        <MonitoredCounting>
                                            <CountingType>availabilityCount</CountingType>
                                            <CountedFeatureUnit>vehicles</CountedFeatureUnit>
                                            <Count>1</Count>
                                        </MonitoredCounting>
                                        <MonitoredCounting>
                                            <CountingType>availabilityCount</CountingType>
                                            <CountedFeatureUnit>vehicles</CountedFeatureUnit>
                                            <TypeOfCountedFeature>
                                                <TypeOfValueCode>mechanical</TypeOfValueCode>
                                            </TypeOfCountedFeature>
                                            <Count>1</Count>
                                        </MonitoredCounting>
                                        <MonitoredCounting>
                                            <CountingType>outOfOrderCount</CountingType>
                                            <CountedFeatureUnit>vehicles</CountedFeatureUnit>
                                            <Count>1</Count>
                                        </MonitoredCounting>
                                        <MonitoredCounting>
                                            <CountingType>availabilityCount</CountingType>
                                            <CountedFeatureUnit>bays</CountedFeatureUnit>
                                            <Count>13</Count>
                                        </MonitoredCounting>
                                        <MonitoredCounting>
                                            <CountingType>outOfOrderCount</CountingType>
                                            <CountedFeatureUnit>bays</CountedFeatureUnit>
                                            <Count>0</Count>
                                        </MonitoredCounting>
                                        <ValidityPeriod>
                                            <StartTime>2025-09-11T16:00:30+02:00</StartTime>
                                        </ValidityPeriod>
                                    </FacilityCondition>
                                    <FacilityCondition xmlns="http://www.siri.org.uk/siri">
                                        <FacilityRef>FR:44109:Parking:76:LOC</FacilityRef>
                                        <FacilityStatus>
                                            <Status>available</Status>
                                            <Description xml:lang="en">rentingAvailable</Description>
                                            <Description xml:lang="en">returningAvailable</Description>
                                        </FacilityStatus>
                                        <MonitoredCounting>
                                            <CountingType>availabilityCount</CountingType>
                                            <CountedFeatureUnit>vehicles</CountedFeatureUnit>
                                            <Count>8</Count>
                                        </MonitoredCounting>
                                        <MonitoredCounting>
                                            <CountingType>availabilityCount</CountingType>
                                            <CountedFeatureUnit>vehicles</CountedFeatureUnit>
                                            <TypeOfCountedFeature>
                                                <TypeOfValueCode>mechanical</TypeOfValueCode>
                                            </TypeOfCountedFeature>
                                            <Count>8</Count>
                                        </MonitoredCounting>
                                        <MonitoredCounting>
                                            <CountingType>outOfOrderCount</CountingType>
                                            <CountedFeatureUnit>vehicles</CountedFeatureUnit>
                                            <Count>0</Count>
                                        </MonitoredCounting>
                                        <MonitoredCounting>
                                            <CountingType>availabilityCount</CountingType>
                                            <CountedFeatureUnit>bays</CountedFeatureUnit>
                                            <Count>7</Count>
                                        </MonitoredCounting>
                                        <MonitoredCounting>
                                            <CountingType>outOfOrderCount</CountingType>
                                            <CountedFeatureUnit>bays</CountedFeatureUnit>
                                            <Count>0</Count>
                                        </MonitoredCounting>
                                        <ValidityPeriod>
                                            <StartTime>2025-09-11T15:55:26+02:00</StartTime>
                                        </ValidityPeriod>
                                    </FacilityCondition>
                                </siri:FacilityMonitoringDelivery>
                            </Notification>
                            <SiriExtension xmlns=""/>
                        </NotifyFacilityMonitoring>
                    </soapenv:Body>
                </soapenv:Envelope>
                """;

        String raw = CustomSiriXml.soapToRaw(soapFMmsg);
        InputStream inputStream = new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8));

        Siri siriResponse = SiriValueTransformer.parseXml(inputStream);
        assertNotNull(siriResponse.getServiceDelivery());
        assertNotNull(siriResponse.getServiceDelivery().getFacilityMonitoringDeliveries());
        assertFalse(siriResponse.getServiceDelivery().getFacilityMonitoringDeliveries().isEmpty());
        assertEquals(4, siriResponse.getServiceDelivery().getFacilityMonitoringDeliveries().getFirst().getFacilityConditions().size());
    }

    @Test
    public void checkStatusTest() throws JAXBException, FileNotFoundException, TransformerException, XMLStreamException {
        String receivedCheckstatus = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Header/>
                    <soap:Body>
                        <ns1:CheckStatusResponse xmlns:ns1="http://wsdl.siri.org.uk">
                            <CheckStatusAnswerInfo xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt"
                                                   xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0"
                                                   xmlns:ns5="http://www.siri.org.uk/siri" xmlns:ns6="http://wsdl.siri.org.uk/siri"
                                                   xmlns:ns7="http://www.divia.fr/mcos" xmlns:ns8="http://qommute.siri.org.uk/siri"
                                                   xmlns:ns9="http://www.rtm.fr/hub">
                                <ns5:ResponseTimestamp>2025-09-25T10:26:28.243+02:00</ns5:ResponseTimestamp>
                                <ns5:ProducerRef>TCAR</ns5:ProducerRef>
                                <ns5:ResponseMessageIdentifier>TCAR:ResponseMessage::32e1cdfc-06dd-453c-9ac5-120ffd3481a1:LOC
                                </ns5:ResponseMessageIdentifier>
                                <ns5:RequestMessageRef>0691bac4-e34c-4f2d-b9a4-f614b1ddbcb6</ns5:RequestMessageRef>
                            </CheckStatusAnswerInfo>
                            <Answer xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt"
                                    xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" xmlns:ns5="http://www.siri.org.uk/siri"
                                    xmlns:ns6="http://wsdl.siri.org.uk/siri" xmlns:ns7="http://www.divia.fr/mcos"
                                    xmlns:ns8="http://qommute.siri.org.uk/siri" xmlns:ns9="http://www.rtm.fr/hub">
                                <ns5:Status>true</ns5:Status>
                                <ns5:ServiceStartedTime>2025-09-25T03:22:07.574+02:00</ns5:ServiceStartedTime>
                            </Answer>
                            <AnswerExtension xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt"
                                             xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" xmlns:ns5="http://www.siri.org.uk/siri"
                                             xmlns:ns6="http://wsdl.siri.org.uk/siri" xmlns:ns7="http://www.divia.fr/mcos"
                                             xmlns:ns8="http://qommute.siri.org.uk/siri" xmlns:ns9="http://www.rtm.fr/hub"/>
                        </ns1:CheckStatusResponse>
                    </soap:Body>
                </soap:Envelope>
                """;


        String raw = CustomSiriXml.soapToRaw(receivedCheckstatus);
        InputStream inputStream = new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8));

        Siri siriResponse = SiriValueTransformer.parseXml(inputStream);
        assertNotNull(siriResponse.getCheckStatusResponse());
        assertNotNull(siriResponse.getCheckStatusResponse().getServiceStartedTime());
        assertNotNull(siriResponse.getCheckStatusResponse().isStatus());
        assertEquals(true, siriResponse.getCheckStatusResponse().isStatus());
    }


    @Test
    public void linesDiscoveryTest() throws IOException, TransformerException, JAXBException, XMLStreamException {

        TransformerFactory tFactory = TransformerFactory.newInstance();
        Source xmlDoc = new StreamSource("src/test/resources/discoveryTest/lines_soap_to_xml_test.xml");

        String outputFileName = "src/test/resources/discoveryTest/results/resultLines.xml";

        try {

            OutputStream htmlFile = new FileOutputStream(outputFileName);
            Transformer transform = tFactory.newTransformer(xslDoc);
            transform.transform(xmlDoc, new StreamResult(htmlFile));

            // XML file generated. Now trying to read it
            File file = new File(outputFileName);
            Siri incoming = SiriValueTransformer.parseXml(new ByteArrayInputStream(FileUtils.readFileToByteArray(file)));

            assertNotNull(incoming.getLinesRequest());
            file.delete();

        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    public void etSubscriptionSoapTest() throws TransformerException, IOException, XMLStreamException, JAXBException {
        // Arrange
        Path inputPath = Path.of("src/test/resources/xsl/et-subscription-soap.xml");
        String etSubscriptionSoap = Files.readString(inputPath);

        // Act
        String etSubscription = CustomSiriXml.soapToRaw(etSubscriptionSoap);
        Siri siri = SiriXml.parseXml(etSubscription);

        // Assert
        assertNotNull(siri);
        assertNotNull(siri.getSubscriptionRequest());
        assertEquals("requestorRef", siri.getSubscriptionRequest().getRequestorRef().getValue());
        assertEquals("https://www.flimsurlecyclimse.fr", siri.getSubscriptionRequest().getConsumerAddress());
        assertEquals(1, siri.getSubscriptionRequest().getEstimatedTimetableSubscriptionRequests().size());

        var etsr = siri.getSubscriptionRequest().getEstimatedTimetableSubscriptionRequests().getFirst();
        assertEquals("5114d8c6556247689f0bf36614d78f5b", etsr.getSubscriptionIdentifier().getValue());

        var etr = etsr.getEstimatedTimetableRequest();
        assertEquals("2.0:FR-2.4", etr.getVersion());
        assertEquals("8bd15707955240c88647c928a64f4456", etr.getMessageIdentifier().getValue());
        assertEquals(60, etr.getPreviewInterval().getMinutes());
    }
}
