/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.siri.handler;

import no.rutebanken.anshar.data.*;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.mapping.ExternalIdsService;
import no.rutebanken.anshar.routes.siri.SiriApisRequestHandlerRoute;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.xml.sax.SAXException;
import uk.org.siri.siri20.*;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SiriHandlerTest extends SpringBootBaseTest {

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private FacilityMonitoring facilityMonitoring;

    @Autowired
    private GeneralMessages genrealMessage;

    @Autowired
    private SiriHandler handler;

    @Autowired
    private Situations situations;

    @Autowired
    private MonitoredStopVisits stopVisits;

    @Autowired
    private VehicleActivities vehicleActivities;

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private ExternalIdsService externalIdsService;

    @Autowired
    private SiriApisRequestHandlerRoute siriApisRequestHandlerRoute;


    @BeforeEach
    public void init() {
        subscriptionManager.clearAllSubscriptions();
    }


//    @Test
    public void testErrorInSXServiceDelivery() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<siri:Siri xmlns:siri=\"http://www.siri.org.uk/siri\">\n" +
                "  <siril:ServiceDelivery xmlns:siril=\"http://www.siri.org.uk/siri\">\n" +
                "    <ResponseTimestamp xmlns=\"http://www.siri.org.uk/siri\">2016-11-10T04:27:15.9028457+01:00</ResponseTimestamp>\n" +
                "    <ProducerRef xmlns=\"http://www.siri.org.uk/siri\">ATB</ProducerRef>\n" +
                "    <ResponseMessageIdentifier xmlns=\"http://www.siri.org.uk/siri\">R_</ResponseMessageIdentifier>\n" +
                "    <SituationExchangeDelivery xmlns=\"http://www.siri.org.uk/siri\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"                                 xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"                                 xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"                                 version=\"2.0\">\n" +
                "      <ResponseTimestamp>2016-11-10T04:27:15.9028457+01:00</ResponseTimestamp>\n" +
                "      <RequestMessageRef>e1995179-cc74-4354-84b2-dbb9850c1b9a</RequestMessageRef>\n" +
                "      <Status>false</Status>\n" +
                "      <ErrorCondition>\n" +
                "        <NoInfoForTopicError/>\n" +
                "        <Description>Unable to connect to the remote server</Description>\n" +
                "      </ErrorCondition>\n" +
                "    </SituationExchangeDelivery>\n" +
                "  </siril:ServiceDelivery>\n" +
                "</siri:Siri>\n";

        try {
            SubscriptionSetup sxSubscription = getSxSubscription();
            subscriptionManager.addSubscription(sxSubscription.getSubscriptionId(), sxSubscription);
            handler.handleIncomingSiri(sxSubscription.getSubscriptionId(),new ByteArrayInputStream(xml.getBytes()));
        } catch (Throwable t) {
            fail("Handling empty response caused exception");
        }
    }


    @Test
    public void testErrorInETServiceDelivery() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<siri:Siri xmlns:siri=\"http://www.siri.org.uk/siri\">\n" +
                "  <siril:ServiceDelivery xmlns:siril=\"http://www.siri.org.uk/siri\">\n" +
                "    <ResponseTimestamp xmlns=\"http://www.siri.org.uk/siri\">2016-11-10T04:27:15.9028457+01:00</ResponseTimestamp>\n" +
                "    <ProducerRef xmlns=\"http://www.siri.org.uk/siri\">ATB</ProducerRef>\n" +
                "    <ResponseMessageIdentifier xmlns=\"http://www.siri.org.uk/siri\">R_</ResponseMessageIdentifier>\n" +
                "    <EstimatedTimetableDelivery xmlns=\"http://www.siri.org.uk/siri\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"                                 xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"                                 xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"                                 version=\"2.0\">\n" +
                "      <ResponseTimestamp>2016-11-10T04:27:15.9028457+01:00</ResponseTimestamp>\n" +
                "      <RequestMessageRef>e1995179-cc74-4354-84b2-dbb9850c1b9a</RequestMessageRef>\n" +
                "      <Status>false</Status>\n" +
                "      <ErrorCondition>\n" +
                "        <NoInfoForTopicError/>\n" +
                "        <Description>Unable to connect to the remote server</Description>\n" +
                "      </ErrorCondition>\n" +
                "    </EstimatedTimetableDelivery>\n" +
                "  </siril:ServiceDelivery>\n" +
                "</siri:Siri>\n";

        try {
            SubscriptionSetup etSubscription = getEtSubscription();
            subscriptionManager.addSubscription(etSubscription.getSubscriptionId(), etSubscription);
            handler.handleIncomingSiri(etSubscription.getSubscriptionId(), new ByteArrayInputStream(xml.getBytes()));
        } catch (Throwable t) {
            fail("Handling empty response caused exception");
        }
    }


   // @Test
    public void testErrorInVMServiceDelivery() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<siri:Siri xmlns:siri=\"http://www.siri.org.uk/siri\">\n" +
                "  <siril:ServiceDelivery xmlns:siril=\"http://www.siri.org.uk/siri\">\n" +
                "    <ResponseTimestamp xmlns=\"http://www.siri.org.uk/siri\">2016-11-10T04:27:15.9028457+01:00</ResponseTimestamp>\n" +
                "    <ProducerRef xmlns=\"http://www.siri.org.uk/siri\">ATB</ProducerRef>\n" +
                "    <ResponseMessageIdentifier xmlns=\"http://www.siri.org.uk/siri\">R_</ResponseMessageIdentifier>\n" +
                "    <VehicleMonitoringDelivery xmlns=\"http://www.siri.org.uk/siri\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"                                 xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"                                 xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"                                 version=\"2.0\">\n" +
                "      <ResponseTimestamp>2016-11-10T04:27:15.9028457+01:00</ResponseTimestamp>\n" +
                "      <RequestMessageRef>e1995179-cc74-4354-84b2-dbb9850c1b9a</RequestMessageRef>\n" +
                "      <Status>false</Status>\n" +
                "      <ErrorCondition>\n" +
                "        <NoInfoForTopicError/>\n" +
                "        <Description>Unable to connect to the remote server</Description>\n" +
                "      </ErrorCondition>\n" +
                "    </VehicleMonitoringDelivery>\n" +
                "  </siril:ServiceDelivery>\n" +
                "</siri:Siri>\n";
        try {
            SubscriptionSetup vmSubscription = getVmSubscription();
            subscriptionManager.addSubscription(vmSubscription.getSubscriptionId(), vmSubscription);
            handler.handleIncomingSiri(vmSubscription.getSubscriptionId(), new ByteArrayInputStream(xml.getBytes()));
        } catch (Throwable t) {
            fail("Handling empty response caused exception");
        }
    }

    @Test
    public void testErrorInSMServiceDelivery() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<siri:Siri xmlns:siri=\"http://www.siri.org.uk/siri\">\n" +
                "  <siril:ServiceDelivery xmlns:siril=\"http://www.siri.org.uk/siri\">\n" +
                "    <ResponseTimestamp xmlns=\"http://www.siri.org.uk/siri\">2016-11-10T04:27:15.9028457+01:00</ResponseTimestamp>\n" +
                "    <ProducerRef xmlns=\"http://www.siri.org.uk/siri\">ATB</ProducerRef>\n" +
                "    <ResponseMessageIdentifier xmlns=\"http://www.siri.org.uk/siri\">R_</ResponseMessageIdentifier>\n" +
                "    <StopMonitoringDelivery xmlns=\"http://www.siri.org.uk/siri\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"                                 xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"                                 xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"                                 version=\"2.0\">\n" +
                "      <ResponseTimestamp>2016-11-10T04:27:15.9028457+01:00</ResponseTimestamp>\n" +
                "      <RequestMessageRef>e1995179-cc74-4354-84b2-dbb9850c1b9a</RequestMessageRef>\n" +
                "      <Status>false</Status>\n" +
                "      <ErrorCondition>\n" +
                "        <NoInfoForTopicError/>\n" +
                "        <Description>Unable to connect to the remote server</Description>\n" +
                "      </ErrorCondition>\n" +
                "    </StopMonitoringDelivery>\n" +
                "  </siril:ServiceDelivery>\n" +
                "</siri:Siri>\n";
        try {
            SubscriptionSetup smSubscription = getSmSubscription();
            smSubscription.setStopMonitoringRefValue("sp3");
            subscriptionManager.addSubscription(smSubscription.getSubscriptionId(), smSubscription);
            handler.handleIncomingSiri(smSubscription.getSubscriptionId(), new ByteArrayInputStream(xml.getBytes()));
        } catch (Throwable t) {
            fail("Handling empty response caused exception");
        }
    }

    @Test
    public void testErrorInFMServiceDelivery() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<siri:Siri xmlns:siri=\"http://www.siri.org.uk/siri\">\n" +
                "  <siril:ServiceDelivery xmlns:siril=\"http://www.siri.org.uk/siri\">\n" +
                "    <ResponseTimestamp xmlns=\"http://www.siri.org.uk/siri\">2016-11-10T04:27:15.9028457+01:00</ResponseTimestamp>\n" +
                "    <ProducerRef xmlns=\"http://www.siri.org.uk/siri\">ATB</ProducerRef>\n" +
                "    <ResponseMessageIdentifier xmlns=\"http://www.siri.org.uk/siri\">R_</ResponseMessageIdentifier>\n" +
                "    <FacilityMonitoringDelivery xmlns=\"http://www.siri.org.uk/siri\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"                                 xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"                                 xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"                                 version=\"2.0\">\n" +
                "      <ResponseTimestamp>2016-11-10T04:27:15.9028457+01:00</ResponseTimestamp>\n" +
                "      <RequestMessageRef>e1995179-cc74-4354-84b2-dbb9850c1b9a</RequestMessageRef>\n" +
                "      <Status>false</Status>\n" +
                "      <ErrorCondition>\n" +
                "        <NoInfoForTopicError/>\n" +
                "        <Description>Unable to connect to the remote server</Description>\n" +
                "      </ErrorCondition>\n" +
                "    </FacilityMonitoringDelivery>\n" +
                "  </siril:ServiceDelivery>\n" +
                "</siri:Siri>\n";

        try {
            SubscriptionSetup fmSubscription = getFmSubscription();
            fmSubscription.setStopMonitoringRefValue("sp3");
            subscriptionManager.addSubscription(fmSubscription.getSubscriptionId(), fmSubscription);
            handler.handleIncomingSiri(fmSubscription.getSubscriptionId(), new ByteArrayInputStream(xml.getBytes()));
        } catch (Throwable t) {
            fail("Handling empty response caused exception");
        }
    }


    /**
     * Test to check that file given by cityway complies with okina management rules
     * @throws JAXBException
     */
    @Test
    public void testCitywaySxCompliance() throws JAXBException {
        SubscriptionSetup sxSubscription = getSxSubscription();
        subscriptionManager.addSubscription(sxSubscription.getSubscriptionId(), sxSubscription);
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File("src/test/resources/PT_EVENT_CG38_siri-sx_dynamic.xml");

        try {
            handler.handleIncomingSiri(sxSubscription.getSubscriptionId(), new ByteArrayInputStream(FileUtils.readFileToByteArray(file)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Collection<PtSituationElement> savedSituations = situations.getAll();


    }

    /**
     * Test to check that file given by cityway complies with okina management rules
     * @throws JAXBException
     */
    @Test
    public void testCitywaySmCompliance() throws JAXBException {
        SubscriptionSetup smSubscription = getSmSubscription();
        smSubscription.setStopMonitoringRefValue("sp4");
        subscriptionManager.addSubscription(smSubscription.getSubscriptionId(), smSubscription);
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File("src/test/resources/PT_RT_STOPTIME_TEST_siri-sm_dynamic.xml");

        try {
            handler.handleIncomingSiri(smSubscription.getSubscriptionId(), new ByteArrayInputStream(FileUtils.readFileToByteArray(file)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Collection<MonitoredStopVisit> savedStopVisits = stopVisits.getAll();

    }

    // @Test expiration bloquant pour les TU à cause de la gestion de cache
    public void testFmComplianceExpired() throws JAXBException {
        facilityMonitoring.clearAll();
        SubscriptionSetup fmSubscription = getFmSubscription();
        subscriptionManager.addSubscription(fmSubscription.getSubscriptionId(), fmSubscription);
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File("src/test/resources/fm_example_expired_delivery.xml");

        try {
            handler.handleIncomingSiri(fmSubscription.getSubscriptionId(), new ByteArrayInputStream(FileUtils.readFileToByteArray(file)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Collection<FacilityConditionStructure> savedfacilities = facilityMonitoring.getAll();
        assertTrue(savedfacilities.isEmpty(), "La liste des objets ajouté doit être vide");
    }

    /**
     * Test to check that file given by GitHub siri complies with okina management rules
     * @throws JAXBException
     */
    //@Test
    public void testFmCompliance() throws JAXBException {
        facilityMonitoring.clearAll();
        SubscriptionSetup fmSubscription = getFmSubscription();
        subscriptionManager.addSubscription(fmSubscription.getSubscriptionId(), fmSubscription);
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File("src/test/resources/fm_example_delivery.xml");

        try {
            handler.handleIncomingSiri(fmSubscription.getSubscriptionId(), new ByteArrayInputStream(FileUtils.readFileToByteArray(file)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Collection<FacilityConditionStructure> savedfacilities = facilityMonitoring.getAll();
        assertFalse(savedfacilities.isEmpty(), "Un objet a dû être ajouté");
    }

    //@Test
    public void testGmCompliance() throws JAXBException {
        genrealMessage.clearAll();
        SubscriptionSetup gmSubscription = getGmSubscription();
        subscriptionManager.addSubscription(gmSubscription.getSubscriptionId(), gmSubscription);
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File("src/test/resources/gm_example_delivery.xml");

        try {
            handler.handleIncomingSiri(gmSubscription.getSubscriptionId(), new ByteArrayInputStream(FileUtils.readFileToByteArray(file)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Collection<GeneralMessage> savedGeneralMessages = genrealMessage.getAll();
        assertFalse(savedGeneralMessages.isEmpty(), "Un objet a dû être ajouté");
    }

    /**
     * Test to check that file given by cityway complies with okina management rules
     * @throws JAXBException
     */
//    @Test
    public void testCitywayEtCompliance() throws JAXBException {

        SubscriptionSetup etSubscription = getEtSubscription();
        subscriptionManager.addSubscription(etSubscription.getSubscriptionId(), etSubscription);
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File("src/test/resources/PT_RT_STOPTIME_STAS_siri-et_dynamic.xml");

        try {
            handler.handleIncomingSiri(etSubscription.getSubscriptionId(), new ByteArrayInputStream(FileUtils.readFileToByteArray(file)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Collection<EstimatedVehicleJourney> savedEstimatedTimetables = estimatedTimetables.getAll();

    }

    @Test
    public void stopPointsDiscoveryTest() throws JAXBException, IOException {
        SubscriptionSetup smSubscription1 = getSmSubscription();
        smSubscription1.setStopMonitoringRefValue("sp1");
        subscriptionManager.addSubscription(smSubscription1.getSubscriptionId(), smSubscription1);

        SubscriptionSetup smSubscription2 = getSmSubscription();
        smSubscription2.setStopMonitoringRefValue("sp2");
        subscriptionManager.addSubscription(smSubscription2.getSubscriptionId(), smSubscription2);

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File("src/test/resources/discoveryTest/stop_points_discovery_test.xml");

        try {
            Siri result = handler.handleIncomingSiri(null, new ByteArrayInputStream(FileUtils.readFileToByteArray(file)), null, null, null, -1, null);
            assertNotNull(result.getStopPointsDelivery());
            assertNotNull(result.getStopPointsDelivery().getAnnotatedStopPointReves());
            assertEquals(2,result.getStopPointsDelivery().getAnnotatedStopPointReves().size());
            List<String> expectedPointRef = Arrays.asList("sp1", "sp2", "sp3", "sp4");
            for (AnnotatedStopPointStructure annotatedStopPointReve : result.getStopPointsDelivery().getAnnotatedStopPointReves()) {
                    assertTrue(expectedPointRef.contains(annotatedStopPointReve.getStopPointRef().getValue()));
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }


    @Test
    public void linesDiscoveryTest() throws JAXBException, IOException {
        SubscriptionSetup vmSubscription1 = getVmSubscription();
        vmSubscription1.setLineRefValue("line1");
        subscriptionManager.addSubscription(vmSubscription1.getSubscriptionId(), vmSubscription1);

        SubscriptionSetup vmSubscription2 = getVmSubscription();
        vmSubscription2.setLineRefValue("line2");
        subscriptionManager.addSubscription(vmSubscription2.getSubscriptionId(), vmSubscription2);

        SubscriptionSetup vmSubscription3 = getVmSubscription();
        vmSubscription3.setLineRefValue("line3");
        subscriptionManager.addSubscription(vmSubscription3.getSubscriptionId(), vmSubscription3);

        estimatedTimetables.add(getVmSubscription().getDatasetId(), createEstimatedVehicleJourney("line3", "vehicle3", 0, 30, ZonedDateTime.now().plusHours(1), true));
        estimatedTimetables.add(getVmSubscription().getDatasetId(), createEstimatedVehicleJourney("line4", "vehicle4", 0, 30, ZonedDateTime.now().plusHours(1), true));

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File("src/test/resources/discoveryTest/lines_discovery_test.xml");

        try {
            Siri result = handler.handleIncomingSiri(null, new ByteArrayInputStream(FileUtils.readFileToByteArray(file)), null, null, null, -1, null);
            assertNotNull(result.getLinesDelivery());
            assertNotNull(result.getLinesDelivery().getAnnotatedLineReves());
            assertEquals(4,result.getLinesDelivery().getAnnotatedLineReves().size());
            List<String> expectedLineRef = Arrays.asList("line1", "line2", "line3", "line4");

            for (AnnotatedLineRef annotatedLineReve : result.getLinesDelivery().getAnnotatedLineReves()) {
                assertTrue(expectedLineRef.contains(annotatedLineReve.getLineRef().getValue()));
            }

        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Test to check sx with and without validity period and start time
     * @throws JAXBException
     */
    @Test
    public void testSxValidityPeriodStartTime() throws JAXBException {
        SubscriptionSetup sxSubscription = getSxSubscription();
        situations.clearAll();
        subscriptionManager.addSubscription(sxSubscription.getSubscriptionId(), sxSubscription);
        File file = new File("src/test/resources/siri-sx_validity_period_start_time.xml");

        try {
            handler.handleIncomingSiri(sxSubscription.getSubscriptionId(), new ByteArrayInputStream(FileUtils.readFileToByteArray(file)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Collection<PtSituationElement> savedSituations = situations.getAll();

        assertEquals(savedSituations.size(), 4);

        for(PtSituationElement savedSituation :  savedSituations) {
            assertNotNull(savedSituation.getValidityPeriods());
            assertNotEquals(savedSituation.getValidityPeriods().size(), 0);
            for(HalfOpenTimestampOutputRangeStructure validityPeriod : savedSituation.getValidityPeriods()){
                assertNotNull(validityPeriod.getStartTime());
            }
        }
    }

    @Test
    public void testAltIdStop() throws JAXBException {
        File file = new File("src/test/resources/stops_mapping.csv");
        externalIdsService.feedCacheStopWithFile(file, "TEST");

        SubscriptionSetup smSubscription = getSmSubscription();
        smSubscription.setStopMonitoringRefValue("TEST:StopPoint:SP:12:LOC");
        smSubscription.setDatasetId("TEST");
        subscriptionManager.addSubscription(smSubscription.getSubscriptionId(), smSubscription);


        File fileInject = new File("src/test/resources/siri-sm.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject, fileInject.getPath(), "TEST");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(stopVisits.getAll().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <StopMonitoringRequest version=\"2.0\">\n" +
                "            <MonitoringRef>30</MonitoringRef>\n" +
                "        </StopMonitoringRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);;

        Siri response = handler.handleIncomingSiri(null, xml, "TEST", SiriHandler.getIdMappingPolicy("false", "true"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().isEmpty());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef().getValue());
        assertEquals(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef().getValue(), "30");
    }


    @Test
    public void testAltIdLine() throws JAXBException {
        File file = new File("src/test/resources/lines_mapping.csv");
        externalIdsService.feedCacheLineWithFile(file, "TEST");

        SubscriptionSetup vmSubscription = getVmSubscription();
        vmSubscription.setVehicleMonitoringRefValue("TEST:VehicleJourney::23:LOC");
        vmSubscription.setDatasetId("TEST");
        subscriptionManager.addSubscription(vmSubscription.getSubscriptionId(), vmSubscription);


        File fileInject = new File("src/test/resources/siri-vm.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-vm", fileInject, fileInject.getPath(), "TEST");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(vehicleActivities.getAll().isEmpty());


        String stringXmlLineRef12 = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "           <VehicleMonitoringRequest version=\"2.0\">\n" +
                "               <LineRef>12</LineRef>\n" +
                "           </VehicleMonitoringRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";


        InputStream xmlLineRef12 = IOUtils.toInputStream(stringXmlLineRef12, StandardCharsets.UTF_8);;

        Siri responseLineRef12 = handler.handleIncomingSiri(null, xmlLineRef12, "TEST", SiriHandler.getIdMappingPolicy("false", "true"), -1, null);
        assertNotNull(responseLineRef12);
        assertNotNull(responseLineRef12.getServiceDelivery());
        assertFalse(responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().isEmpty());
        assertFalse(responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().isEmpty());
        assertNotNull(responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().get(0).getVehicleMonitoringRef());
        assertNotNull(responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().get(0).getVehicleMonitoringRef());
        assertNotNull(responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().get(0).getVehicleMonitoringRef().getValue());
        assertEquals(responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().get(0).getVehicleMonitoringRef().getValue(), "TEST:VehicleJourney::23:LOC");
        assertEquals(responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().get(0).getMonitoredVehicleJourney().getLineRef().getValue(), "12");

        String stringXmlLineRef34 = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "           <VehicleMonitoringRequest version=\"2.0\">\n" +
                "               <LineRef>34</LineRef>\n" +
                "           </VehicleMonitoringRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";


        InputStream xmlLineRef34 = IOUtils.toInputStream(stringXmlLineRef34, StandardCharsets.UTF_8);;

        Siri responseLineRef34 = handler.handleIncomingSiri(null, xmlLineRef34, "TEST", SiriHandler.getIdMappingPolicy("false", "true"), -1, null);
        assertNotNull(responseLineRef34);
        assertNotNull(responseLineRef34.getServiceDelivery());
        assertFalse(responseLineRef34.getServiceDelivery().getVehicleMonitoringDeliveries().isEmpty());
        assertFalse(responseLineRef34.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().isEmpty());

        List<VehicleActivityStructure> vehicleActivityStructures = responseLineRef34.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities();
        Comparator<VehicleActivityStructure> vehicleActivityStructureComparator
                = Comparator.comparing(vehicleActivityStructure -> vehicleActivityStructure.getVehicleMonitoringRef().getValue());
        vehicleActivityStructures.sort(vehicleActivityStructureComparator);

        assertNotNull(vehicleActivityStructures.get(0).getVehicleMonitoringRef());
        assertNotNull(vehicleActivityStructures.get(0).getVehicleMonitoringRef());
        assertNotNull(vehicleActivityStructures.get(0).getVehicleMonitoringRef().getValue());
        assertEquals(vehicleActivityStructures.get(0).getVehicleMonitoringRef().getValue(), "TEST:VehicleJourney::232:LOC");
        assertEquals(vehicleActivityStructures.get(0).getMonitoredVehicleJourney().getLineRef().getValue(), "34");

        assertNotNull(vehicleActivityStructures.get(1).getVehicleMonitoringRef());
        assertNotNull(vehicleActivityStructures.get(1).getVehicleMonitoringRef());
        assertNotNull(vehicleActivityStructures.get(1).getVehicleMonitoringRef().getValue());
        assertEquals(vehicleActivityStructures.get(1).getVehicleMonitoringRef().getValue(), "TEST:VehicleJourney::233:LOC");
        assertEquals(vehicleActivityStructures.get(1).getMonitoredVehicleJourney().getLineRef().getValue(), "34");
    }









    private SubscriptionSetup getSxSubscription() {
        return getSubscriptionSetup(SiriDataType.SITUATION_EXCHANGE);
    }

    private SubscriptionSetup getVmSubscription() {
        return getSubscriptionSetup(SiriDataType.VEHICLE_MONITORING);
    }

    private SubscriptionSetup getEtSubscription() {
        return getSubscriptionSetup(SiriDataType.ESTIMATED_TIMETABLE);
    }

    private SubscriptionSetup getSmSubscription() { return getSubscriptionSetup(SiriDataType.STOP_MONITORING);
    }

    private SubscriptionSetup getFmSubscription() { return getSubscriptionSetup(SiriDataType.FACILITY_MONITORING);
    }

    private SubscriptionSetup getGmSubscription() { return getSubscriptionSetup(SiriDataType.GENERAL_MESSAGE);
    }

    private SubscriptionSetup getSubscriptionSetup(SiriDataType type) {
        return new SubscriptionSetup(
                type,
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                "http://localhost",
                Duration.ofMinutes(1),
                Duration.ofSeconds(1),
                "http://www.kolumbus.no/siri",
                new HashMap<>(),
                "1.4",
                "SwarcoMizar",
                "tst",
                SubscriptionSetup.ServiceType.SOAP,
                new ArrayList<>(),
                new HashMap<>(),
                new ArrayList<>(),
                UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofSeconds(600),
                true
        );
    }

    private EstimatedVehicleJourney createEstimatedVehicleJourney(String lineRefValue, String vehicleRefValue, int startOrder, int callCount, ZonedDateTime arrival, Boolean isComplete) {
        return createEstimatedVehicleJourney(lineRefValue, vehicleRefValue, startOrder, callCount, arrival, arrival, isComplete);
    }

    private EstimatedVehicleJourney createEstimatedVehicleJourney(String lineRefValue, String vehicleRefValue, int startOrder, int callCount, ZonedDateTime arrival, ZonedDateTime departure, Boolean isComplete) {
        EstimatedVehicleJourney element = new EstimatedVehicleJourney();
        LineRef lineRef = new LineRef();
        lineRef.setValue(lineRefValue);
        element.setLineRef(lineRef);
        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue(vehicleRefValue);
        element.setVehicleRef(vehicleRef);
        element.setIsCompleteStopSequence(isComplete);

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
        for (int i = startOrder; i < callCount; i++) {

            StopPointRef stopPointRef = new StopPointRef();
            stopPointRef.setValue("NSR:TEST:" + i);
            EstimatedCall call = new EstimatedCall();
            call.setStopPointRef(stopPointRef);
            call.setAimedArrivalTime(arrival);
            call.setExpectedArrivalTime(arrival);
            call.setAimedDepartureTime(departure);
            call.setExpectedDepartureTime(departure);
            call.setOrder(BigInteger.valueOf(i));
            call.setVisitNumber(BigInteger.valueOf(i));
            estimatedCalls.getEstimatedCalls().add(call);
        }

        element.setEstimatedCalls(estimatedCalls);
        element.setRecordedAtTime(ZonedDateTime.now());

        return element;
    }
}
