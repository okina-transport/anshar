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

import com.hazelcast.map.IMap;
import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.data.*;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.mapping.ExternalIdsService;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.SiriApisRequestHandlerRoute;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.xml.sax.SAXException;
import uk.org.siri.siri20.*;

import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class SiriHandlerTest extends SpringBootBaseTest {

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private FacilityMonitoring facilityMonitoring;

    @Autowired
    private GeneralMessages generalMessage;

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

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private DiscoveryCache discoveryCache;


    @BeforeEach
    public void init() {
        subscriptionManager.clearAllSubscriptions();
        estimatedTimetables.clearAll();
        vehicleActivities.clearAll();
        situations.clearAll();
        stopVisits.clearAll();
        generalMessage.clearAll();
        facilityMonitoring.clearAll();
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
            SubscriptionSetup sxSubscription = getSxSubscription("tst");
            subscriptionManager.addSubscription(sxSubscription.getSubscriptionId(), sxSubscription);
            handler.handleIncomingSiri(sxSubscription.getSubscriptionId(), new ByteArrayInputStream(xml.getBytes()));
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
            SubscriptionSetup etSubscription = getEtSubscription("tst");
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
            SubscriptionSetup vmSubscription = getVmSubscription("tst");
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
            SubscriptionSetup smSubscription = getSmSubscription("tst");
            smSubscription.getStopMonitoringRefValues().add("sp3");
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
            SubscriptionSetup fmSubscription = getFmSubscription("tst");
            fmSubscription.getStopMonitoringRefValues().add("sp3");
            subscriptionManager.addSubscription(fmSubscription.getSubscriptionId(), fmSubscription);
            handler.handleIncomingSiri(fmSubscription.getSubscriptionId(), new ByteArrayInputStream(xml.getBytes()));
        } catch (Throwable t) {
            fail("Handling empty response caused exception");
        }
    }


    /**
     * Test to check that file given by cityway complies with okina management rules
     *
     * @throws JAXBException
     */
    @Test
    public void testCitywaySxCompliance() throws JAXBException {
        SubscriptionSetup sxSubscription = getSxSubscription("tst");
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
     *
     * @throws JAXBException
     */
    @Test
    public void testCitywaySmCompliance() throws JAXBException {
        SubscriptionSetup smSubscription = getSmSubscription("tst");
        smSubscription.getStopMonitoringRefValues().add("sp4");
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
        SubscriptionSetup fmSubscription = getFmSubscription("tst");
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
     *
     * @throws JAXBException
     */
    //@Test
    public void testFmCompliance() throws JAXBException {
        facilityMonitoring.clearAll();
        SubscriptionSetup fmSubscription = getFmSubscription("tst");
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
        generalMessage.clearAll();
        SubscriptionSetup gmSubscription = getGmSubscription("tst");
        subscriptionManager.addSubscription(gmSubscription.getSubscriptionId(), gmSubscription);
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File("src/test/resources/gm_example_delivery.xml");

        try {
            handler.handleIncomingSiri(gmSubscription.getSubscriptionId(), new ByteArrayInputStream(FileUtils.readFileToByteArray(file)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Collection<GeneralMessage> savedGeneralMessages = generalMessage.getAll();
        assertFalse(savedGeneralMessages.isEmpty(), "Un objet a dû être ajouté");
    }

    /**
     * Test to check that file given by cityway complies with okina management rules
     *
     * @throws JAXBException
     */
//    @Test
    public void testCitywayEtCompliance() throws JAXBException {

        SubscriptionSetup etSubscription = getEtSubscription("tst");
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
        SubscriptionSetup smSubscription1 = getSmSubscription("tst");
        smSubscription1.getStopMonitoringRefValues().add("sp1");
        smSubscription1.setDatasetId("DAT1");
        subscriptionManager.addSubscription(smSubscription1.getSubscriptionId(), smSubscription1);

        SubscriptionSetup smSubscription2 = getSmSubscription("tst");
        smSubscription2.getStopMonitoringRefValues().add("sp2");
        smSubscription2.setDatasetId("DAT1");
        subscriptionManager.addSubscription(smSubscription2.getSubscriptionId(), smSubscription2);
        discoveryCache.addStop("DAT1", "sp1");
        discoveryCache.addStop("DAT1", "sp2");

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File("src/test/resources/discoveryTest/stop_points_discovery_test.xml");

        try {
            Siri result = handler.handleIncomingSiri(null, new ByteArrayInputStream(FileUtils.readFileToByteArray(file)), "DAT1", null, OutboundIdMappingPolicy.ORIGINAL_ID, -1, null, false);
            assertNotNull(result.getStopPointsDelivery());
            assertNotNull(result.getStopPointsDelivery().getAnnotatedStopPointReves());
            assertEquals(2, result.getStopPointsDelivery().getAnnotatedStopPointReves().size());
            List<String> expectedPointRef = Arrays.asList("sp1", "sp2", "sp3", "sp4");
            for (AnnotatedStopPointStructure annotatedStopPointReve : result.getStopPointsDelivery().getAnnotatedStopPointReves()) {
                assertTrue(expectedPointRef.contains(annotatedStopPointReve.getStopPointRef().getValue()));
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }


    public void stopPointsDiscoveryTestWithDifferentDatasetId() throws JAXBException, IOException {
        SubscriptionSetup smSubscription1 = getSmSubscription("tst1");
        smSubscription1.getStopMonitoringRefValues().add("sp1");
        subscriptionManager.addSubscription(smSubscription1.getSubscriptionId(), smSubscription1);

        SubscriptionSetup smSubscription2 = getSmSubscription("tst2");
        smSubscription2.getStopMonitoringRefValues().add("sp2");
        subscriptionManager.addSubscription(smSubscription2.getSubscriptionId(), smSubscription2);

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File("src/test/resources/discoveryTest/stop_points_discovery_test.xml");

        try {
            Siri result = handler.handleIncomingSiri(null, new ByteArrayInputStream(FileUtils.readFileToByteArray(file)), null, null, null, -1, null, false);
            assertNotNull(result.getStopPointsDelivery());
            assertNotNull(result.getStopPointsDelivery().getAnnotatedStopPointReves());
            assertEquals(2, result.getStopPointsDelivery().getAnnotatedStopPointReves().size());
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
//        estimatedTimetables.clearAll();
//        SubscriptionSetup vmSubscription1 = getVmSubscription("tst");
//        vmSubscription1.getLineRefValues().add("line1");
//        subscriptionManager.addSubscription(vmSubscription1.getSubscriptionId(), vmSubscription1);
//
//        SubscriptionSetup vmSubscription2 = getVmSubscription("tst");
//        vmSubscription2.getLineRefValues().add("line2");
//        subscriptionManager.addSubscription(vmSubscription2.getSubscriptionId(), vmSubscription2);
//
//        SubscriptionSetup vmSubscription3 = getVmSubscription("tst");
//        vmSubscription3.getLineRefValues().add("line3");
//        subscriptionManager.addSubscription(vmSubscription3.getSubscriptionId(), vmSubscription3);
//
//        estimatedTimetables.add(getVmSubscription("tst").getDatasetId(), createEstimatedVehicleJourney("line3", "vehicle3", 0, 30, ZonedDateTime.now().plusHours(1), true));
//        estimatedTimetables.add(getVmSubscription("tst").getDatasetId(), createEstimatedVehicleJourney("line4", "vehicle4", 0, 30, ZonedDateTime.now().plusHours(1), true));
//

        discoveryCache.addLine("DAT1", "line1");
        discoveryCache.addLine("DAT1", "line2");
        discoveryCache.addLine("DAT1", "line3");
        discoveryCache.addLine("DAT1", "line4");

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File("src/test/resources/discoveryTest/lines_discovery_test.xml");

        try {
            Siri result = handler.handleIncomingSiri(null, new ByteArrayInputStream(FileUtils.readFileToByteArray(file)), null, null, -1, null, false);
            assertNotNull(result.getLinesDelivery());
            assertNotNull(result.getLinesDelivery().getAnnotatedLineReves());
            assertEquals(4, result.getLinesDelivery().getAnnotatedLineReves().size());
            List<String> expectedLineRef = Arrays.asList("line1", "line2", "line3", "line4");

            for (AnnotatedLineRef annotatedLineReve : result.getLinesDelivery().getAnnotatedLineReves()) {
                assertTrue(expectedLineRef.contains(annotatedLineReve.getLineRef().getValue()));
            }

        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    public void linesDiscoveryTestWithDifferentDatasetId() throws JAXBException, IOException {
        estimatedTimetables.clearAll();
        SubscriptionSetup vmSubscription1 = getVmSubscription("tst1");
        vmSubscription1.getLineRefValues().add("line1");
        subscriptionManager.addSubscription(vmSubscription1.getSubscriptionId(), vmSubscription1);

        SubscriptionSetup vmSubscription2 = getVmSubscription("tst2");
        vmSubscription2.getLineRefValues().add("line2");
        subscriptionManager.addSubscription(vmSubscription2.getSubscriptionId(), vmSubscription2);

        SubscriptionSetup vmSubscription3 = getVmSubscription("tst3");
        vmSubscription3.getLineRefValues().add("line3");
        subscriptionManager.addSubscription(vmSubscription3.getSubscriptionId(), vmSubscription3);

        estimatedTimetables.add(getVmSubscription("tst3").getDatasetId(), createEstimatedVehicleJourney("line3", "vehicle3", 0, 30, ZonedDateTime.now().plusHours(1), true));
        estimatedTimetables.add(getVmSubscription("tst4").getDatasetId(), createEstimatedVehicleJourney("line4", "vehicle4", 0, 30, ZonedDateTime.now().plusHours(1), true));


        discoveryCache.addLine("tst1", "line1");
        discoveryCache.addLine("tst2", "line2");
        discoveryCache.addLine("tst3", "line3");
        discoveryCache.addLine("tst4", "line4");

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File("src/test/resources/discoveryTest/lines_discovery_test.xml");

        try {
            Siri result = handler.handleIncomingSiri(null, new ByteArrayInputStream(FileUtils.readFileToByteArray(file)), null, null, -1, null, false);
            assertNotNull(result.getLinesDelivery());
            assertNotNull(result.getLinesDelivery().getAnnotatedLineReves());
            assertEquals(4, result.getLinesDelivery().getAnnotatedLineReves().size());
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
     *
     * @throws JAXBException
     */
    @Test
    public void testSxValidityPeriodStartTime() throws JAXBException {
        SubscriptionSetup sxSubscription = getSxSubscription("tst");
        subscriptionManager.addSubscription(sxSubscription.getSubscriptionId(), sxSubscription);
        File file = new File("src/test/resources/siri-sx_validity_period_start_time.xml");

        try {
            handler.handleIncomingSiri(sxSubscription.getSubscriptionId(), new ByteArrayInputStream(FileUtils.readFileToByteArray(file)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Collection<PtSituationElement> savedSituations = situations.getAll();

        assertEquals(4, savedSituations.size());

        for (PtSituationElement savedSituation : savedSituations) {
            assertNotNull(savedSituation.getValidityPeriods());
            assertNotEquals(savedSituation.getValidityPeriods().size(), 0);
            for (HalfOpenTimestampOutputRangeStructure validityPeriod : savedSituation.getValidityPeriods()) {
                assertNotNull(validityPeriod.getStartTime());
            }
        }
    }

    public void initStopPlaceMapper() {
        Map<String, Pair<String, String>> stopPlaceMap;

        stopPlaceMap = new HashMap<>();
        stopPlaceMap.put("TEST1:StopPoint:SP:121:LOC", Pair.of("MOBIITI:Quay:a", "test1"));
        stopPlaceMap.put("TEST2:StopPoint:SP:122:LOC", Pair.of("MOBIITI:Quay:a", "test2"));
        stopPlaceMap.put("TEST3:StopPoint:SP:123:LOC", Pair.of("MOBIITI:Quay:b", "test3"));
        stopPlaceMap.put("TEST4:StopPoint:SP:124:LOC", Pair.of("MOBIITI:Quay:b", "test4"));

        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);

        //Manually adding custom mapping to Spring context
        stopPlaceService.addStopPlaceMappings(stopPlaceMap);

        Set<String> stopQuays = new HashSet<>(stopPlaceMap.keySet());
        stopPlaceService.addStopQuays(stopQuays);

        Map<String, List<String>> stopPlaceReverseMap = new HashMap<>();
        List<String> originalIds = new ArrayList<>();
        originalIds.add("TEST1:StopPoint:SP:121:LOC");
        originalIds.add("TEST2:StopPoint:SP:122:LOC");
        stopPlaceReverseMap.put("MOBIITI:Quay:a", originalIds);
        originalIds.add("TEST1:StopPoint:SP:123:LOC");
        originalIds.add("TEST2:StopPoint:SP:124:LOC");
        stopPlaceReverseMap.put("MOBIITI:Quay:b", originalIds);
        stopPlaceService.addStopPlaceReverseMappings(stopPlaceReverseMap);
    }


    private SubscriptionSetup getSxSubscription(String datasetId) {
        return getSubscriptionSetup(SiriDataType.SITUATION_EXCHANGE, datasetId);
    }

    private SubscriptionSetup getVmSubscription(String datasetId) {
        return getSubscriptionSetup(SiriDataType.VEHICLE_MONITORING, datasetId);
    }

    private SubscriptionSetup getEtSubscription(String datasetId) {
        return getSubscriptionSetup(SiriDataType.ESTIMATED_TIMETABLE, datasetId);
    }

    private SubscriptionSetup getSmSubscription(String datasetId) {
        return getSubscriptionSetup(SiriDataType.STOP_MONITORING, datasetId);
    }

    private SubscriptionSetup getFmSubscription(String datasetId) {
        return getSubscriptionSetup(SiriDataType.FACILITY_MONITORING, datasetId);
    }

    private SubscriptionSetup getGmSubscription(String datasetId) {
        return getSubscriptionSetup(SiriDataType.GENERAL_MESSAGE, datasetId);
    }

    private SubscriptionSetup getSubscriptionSetup(SiriDataType type, String datasetId) {
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
                datasetId,
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

    /**
     * Stop monitoring
     * id producteur
     * useOriginalId true
     * avec datasetId
     * retour données producteurs identifiants locaux
     **/
    @Test
    public void SM_idProducer_DatasetId() throws JAXBException {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-sm-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-sm-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(stopVisits.getAll().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <StopMonitoringRequest version=\"2.0\">\n" +
                "            <MonitoringRef>TEST1:StopPoint:SP:121:LOC</MonitoringRef>\n" +
                "        </StopMonitoringRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);
        ;

        Siri response = handler.handleIncomingSiri(null, xml, "TEST1", SiriHandler.getIdMappingPolicy("true", "false"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().isEmpty());
        assertEquals(1, response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().size());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef().getValue());
        assertEquals("TEST1:StopPoint:SP:121:LOC", response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef().getValue());
    }

    /**
     * Stop monitoring
     * id producteur
     * useOriginalId true
     * sans datasetId
     * retour rien
     **/
    @Test
    public void SM_idProducer_No_DatasetId() throws JAXBException {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-sm-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-sm-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(stopVisits.getAll().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <StopMonitoringRequest version=\"2.0\">\n" +
                "            <MonitoringRef>TEST1:StopPoint:SP:121:LOC</MonitoringRef>\n" +
                "        </StopMonitoringRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(null, xml, null, SiriHandler.getIdMappingPolicy("true", "false"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        assertTrue(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().isEmpty());
    }

    /**
     * Stop monitoring
     * id producteur
     * useOriginalId false
     * sans datasetId
     * retour rien
     **/
    @Test
    public void SM_IdProducer_UseOriginalId_False_No_DatasetId() throws JAXBException {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-sm-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-sm-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(stopVisits.getAll().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <StopMonitoringRequest version=\"2.0\">\n" +
                "            <MonitoringRef>TEST1:StopPoint:SP:121:LOC</MonitoringRef>\n" +
                "        </StopMonitoringRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(null, xml, null, SiriHandler.getIdMappingPolicy("false", "false"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        assertTrue(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().isEmpty());
    }

    /**
     * Stop monitoring
     * id producteur
     * useOriginalId false
     * avec datasetId
     * retour rien
     **/
    @Test
    public void SM_IdProducer_UseOriginalId_False_DatasetId() throws JAXBException {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-sm-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-sm-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(stopVisits.getAll().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <StopMonitoringRequest version=\"2.0\">\n" +
                "            <MonitoringRef>TEST1:StopPoint:SP:121:LOC</MonitoringRef>\n" +
                "        </StopMonitoringRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(null, xml, "TEST1", SiriHandler.getIdMappingPolicy("false", "false"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        assertTrue(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().isEmpty());
    }

    /**
     * Stop monitoring
     * sans id
     * useOriginalId false
     * avec datasetId
     * retour tous les points d'arrêt du datasetId
     **/
    @Test
    public void SM_No_Id_UseOriginalId_False_DatasetId() throws JAXBException {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-sm-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-sm-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }
        File fileInject3 = new File("src/test/resources/siri-sm-test3.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject3, fileInject1.getPath(), "TEST3");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject4 = new File("src/test/resources/siri-sm-test4.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject4, fileInject2.getPath(), "TEST4");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(stopVisits.getAll().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <StopMonitoringRequest version=\"2.0\">\n" +
                "        </StopMonitoringRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(null, xml, "TEST1", SiriHandler.getIdMappingPolicy("false", "false"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().isEmpty());
        assertEquals(1, response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().size());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef().getValue());
        assertEquals("MOBIITI:Quay:a", response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef().getValue());
    }

    /**
     * Stop monitoring
     * sans id
     * useOriginalId false
     * pas de datasetId
     * retour rien
     **/
    @Test
    public void SM_No_Id_UseOriginalId_False_No_DatasetId() throws JAXBException {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-sm-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-sm-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }
        File fileInject3 = new File("src/test/resources/siri-sm-test3.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject3, fileInject1.getPath(), "TEST3");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject4 = new File("src/test/resources/siri-sm-test4.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject4, fileInject2.getPath(), "TEST4");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(stopVisits.getAll().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <StopMonitoringRequest version=\"2.0\">\n" +
                "        </StopMonitoringRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(null, xml, null, SiriHandler.getIdMappingPolicy("false", "false"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        assertTrue(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().isEmpty());
    }

    /**
     * Estimated timetable
     * avec id
     * avec datasetId
     * useOriginalId true
     * retour données producteurs identifiants locaux
     **/
    @Test
    public void ET_Id_DatasetId_UseOriginalId_True() throws JAXBException {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-et-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-et-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(estimatedTimetables.getAll().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <EstimatedTimetableRequest version=\"2.0\">\n" +
                "           <Lines>\n" +
                "               <LineDirection>\n" +
                "                   <LineRef>TEST1:Line:1:LOC</LineRef>\n" +
                "               </LineDirection>\n" +
                "           </Lines>\n" +
                "        </EstimatedTimetableRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(null, xml, "TEST1", SiriHandler.getIdMappingPolicy("true", "false"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertEquals(1, response.getServiceDelivery().getEstimatedTimetableDeliveries().size());
        assertEquals("TEST1:Line:1:LOC", response.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).getLineRef().getValue());
        assertEquals("TEST1:VehicleJourney:1:LOC", response.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).getDatedVehicleJourneyRef().getValue());
        assertEquals("TEST1:StopPoint:SP:121:LOC", response.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).getEstimatedCalls().getEstimatedCalls().get(0).getStopPointRef().getValue());
    }

    /**
     * Estimated timetable
     * avec id
     * avec datasetId
     * useOriginalId false
     * retour données MOBIITI
     **/
    @Test
    public void ET_Id_DatasetId_UseOriginalId_False() throws JAXBException {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-et-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-et-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(estimatedTimetables.getAll().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <EstimatedTimetableRequest version=\"2.0\">\n" +
                "           <Lines>\n" +
                "               <LineDirection>\n" +
                "                   <LineRef>TEST1:Line:1:LOC</LineRef>\n" +
                "               </LineDirection>\n" +
                "           </Lines>\n" +
                "        </EstimatedTimetableRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(null, xml, "TEST1", SiriHandler.getIdMappingPolicy("false", "false"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertEquals(1, response.getServiceDelivery().getEstimatedTimetableDeliveries().size());
        assertEquals("TEST1:Line:1:LOC", response.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).getLineRef().getValue());
        assertEquals("TEST1:VehicleJourney:1:LOC", response.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).getDatedVehicleJourneyRef().getValue());
        assertEquals("MOBIITI:Quay:a", response.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).getEstimatedCalls().getEstimatedCalls().get(0).getStopPointRef().getValue());
    }

    /**
     * Estimated timetable
     * avec id
     * sans datasetId
     * useOriginalId true
     * retour rien
     **/
    @Test
    public void ET_Id_No_DatasetId_UseOriginalId_True() throws JAXBException {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-et-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-et-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(estimatedTimetables.getAll().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <EstimatedTimetableRequest version=\"2.0\">\n" +
                "           <Lines>\n" +
                "               <LineDirection>\n" +
                "                   <LineRef>TEST1:Line:1:LOC</LineRef>\n" +
                "               </LineDirection>\n" +
                "           </Lines>\n" +
                "        </EstimatedTimetableRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(null, xml, null, SiriHandler.getIdMappingPolicy("true", "false"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getEstimatedTimetableDeliveries().isEmpty());
        assertTrue(response.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().isEmpty());
    }

    /**
     * Estimated timetable
     * avec id
     * sans datasetId
     * useOriginalId false
     * retour rien
     **/
    @Test
    public void ET_Id_No_DatasetId_UseOriginalId_False() throws JAXBException {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-et-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-et-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(estimatedTimetables.getAll().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <EstimatedTimetableRequest version=\"2.0\">\n" +
                "           <Lines>\n" +
                "               <LineDirection>\n" +
                "                   <LineRef>TEST1:Line:1:LOC</LineRef>\n" +
                "               </LineDirection>\n" +
                "           </Lines>\n" +
                "        </EstimatedTimetableRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(null, xml, null, SiriHandler.getIdMappingPolicy("false", "false"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getEstimatedTimetableDeliveries().isEmpty());
        assertTrue(response.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().isEmpty());
    }

    /**
     * Estimated timetable
     * sans id
     * useOriginalId false
     * sans datasetId
     * retour rien
     **/
    @Test
    public void ET_No_Id_No_DatasetId_UseOriginalId_False() throws JAXBException {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-et-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-et-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(estimatedTimetables.getAll().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <EstimatedTimetableRequest version=\"2.0\">\n" +
                "        </EstimatedTimetableRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(null, xml, null, SiriHandler.getIdMappingPolicy("false", "false"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getEstimatedTimetableDeliveries().isEmpty());
        assertTrue(response.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().isEmpty());
    }

    /**
     * Estimated timetable
     * sans id
     * useOriginalId false
     * avec datasetId
     * retour tous les ET du datasetId
     **/
    @Test
    public void ET_No_Id_DatasetId_UseOriginalId_False() throws JAXBException {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-et-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-et-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(estimatedTimetables.getAll().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <EstimatedTimetableRequest version=\"2.0\">\n" +
                "        </EstimatedTimetableRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(null, xml, "TEST1", SiriHandler.getIdMappingPolicy("false", "false"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertEquals(1, response.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().size());
        assertEquals("TEST1:Line:1:LOC", response.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).getLineRef().getValue());
        assertEquals("TEST1:VehicleJourney:1:LOC", response.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).getDatedVehicleJourneyRef().getValue());
        assertEquals("MOBIITI:Quay:a", response.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).getEstimatedCalls().getEstimatedCalls().get(0).getStopPointRef().getValue());
    }

    /**
     * Vehicle monitoring
     * id producteur
     * avec datasetId
     * retour données producteurs identifiants locaux
     **/
    @Test
    public void VM_DatasetId() throws JAXBException {
        File fileInject1 = new File("src/test/resources/siri-vm-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-vm", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-vm-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-vm", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(vehicleActivities.getAll().isEmpty());


        String stringXmlLineRef = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <VehicleMonitoringRequest version=\"2.0\">\n" +
                "            <LineRef>TEST1::Line::1:LOC</LineRef>\n" +
                "        </VehicleMonitoringRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xmlLineRef = IOUtils.toInputStream(stringXmlLineRef, StandardCharsets.UTF_8);

        Siri responseLineRef = handler.handleIncomingSiri(null, xmlLineRef, "TEST1", SiriHandler.getIdMappingPolicy("true", "false"), -1, null);
        assertNotNull(responseLineRef);
        assertNotNull(responseLineRef.getServiceDelivery());
        assertFalse(responseLineRef.getServiceDelivery().getVehicleMonitoringDeliveries().isEmpty());
        assertEquals(1, responseLineRef.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size());
        assertEquals("TEST1::Line::1:LOC", responseLineRef.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().get(0).getMonitoredVehicleJourney().getLineRef().getValue());


//        String stringXmlVehicleRef = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
//                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
//                "    <ServiceRequest>\n" +
//                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
//                "        <VehicleMonitoringRequest version=\"2.0\">\n" +
//                "            <VehicleRef>TEST1:VehicleJourney::1:LOC</VehicleRef>\n" +
//                "        </VehicleMonitoringRequest>\n" +
//                "    </ServiceRequest>\n" +
//                "</Siri>";
//
//        InputStream xmlVehicleRef = IOUtils.toInputStream(stringXmlVehicleRef, StandardCharsets.UTF_8);
//
//        Siri responseVehicleRef = handler.handleIncomingSiri(null, xmlVehicleRef, "TEST1", SiriHandler.getIdMappingPolicy("false", "false"), -1, null);
//        assertNotNull(responseVehicleRef);
//        assertNotNull(responseVehicleRef.getServiceDelivery());
//        assertFalse(responseVehicleRef.getServiceDelivery().getVehicleMonitoringDeliveries().isEmpty());
//        assertEquals(1, responseVehicleRef.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size());
//        assertEquals("TEST1:VehicleJourney::1:LOC", responseVehicleRef.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().get(0).getVehicleMonitoringRef().getValue());
    }

    /**
     * Vehicle monitoring
     * sans datasetId
     * retour rien
     **/
    @Test
    public void VM_No_DatasetId() throws JAXBException {
        File fileInject1 = new File("src/test/resources/siri-vm-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-vm", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-vm-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-vm", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(vehicleActivities.getAll().isEmpty());


        String stringXmlLineRef = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <VehicleMonitoringRequest version=\"2.0\">\n" +
                "            <LineRef>TEST1::Line::1:LOC</LineRef>\n" +
                "        </VehicleMonitoringRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xmlLineRef = IOUtils.toInputStream(stringXmlLineRef, StandardCharsets.UTF_8);

        Siri responseLineRef = handler.handleIncomingSiri(null, xmlLineRef, null, SiriHandler.getIdMappingPolicy("false", "false"), -1, null);
        assertNotNull(responseLineRef);
        assertNotNull(responseLineRef.getServiceDelivery());
        assertFalse(responseLineRef.getServiceDelivery().getVehicleMonitoringDeliveries().isEmpty());
        assertEquals(0, responseLineRef.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size());


//        String stringXmlVehicleRef = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
//                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
//                "    <ServiceRequest>\n" +
//                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
//                "        <VehicleMonitoringRequest version=\"2.0\">\n" +
//                "            <VehicleRef>TEST1:VehicleJourney::1:LOC</VehicleRef>\n" +
//                "        </VehicleMonitoringRequest>\n" +
//                "    </ServiceRequest>\n" +
//                "</Siri>";
//
//        InputStream xmlVehicleRef = IOUtils.toInputStream(stringXmlVehicleRef, StandardCharsets.UTF_8);
//
//        Siri responseVehicleRef = handler.handleIncomingSiri(null, xmlVehicleRef, null, SiriHandler.getIdMappingPolicy("true", "false"), -1, null);
//        assertNotNull(responseVehicleRef);
//        assertNotNull(responseVehicleRef.getServiceDelivery());
//        assertFalse(responseVehicleRef.getServiceDelivery().getVehicleMonitoringDeliveries().isEmpty());
//        assertEquals(0, responseVehicleRef.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size());
    }

    /**
     * Situation exchange
     * avec datasetId
     * retour données producteurs identifiants locaux
     **/
    @Test
    public void SX_DatasetId() throws JAXBException {

        IMap<SiriObjectStorageKey, PtSituationElement> originalSaved = situations.getSituationElements();
        HazelcastTestMap<PtSituationElement> testMap = new HazelcastTestMap<PtSituationElement>();
        situations.setSituationElements(testMap);

        File fileInject1 = new File("src/test/resources/siri-sx-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sx", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-sx-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sx", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(situations.getAll().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <SituationExchangeRequest version=\"2.0\">\n" +
                "        </SituationExchangeRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xmlLine = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(null, xmlLine, "TEST1", SiriHandler.getIdMappingPolicy("false", "false"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertEquals(1, response.getServiceDelivery().getSituationExchangeDeliveries().size());
        assertEquals("TEST1:J1", response.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements().get(0).getSituationNumber().getValue());
        situations.setSituationElements(originalSaved);
    }

    /**
     * Situation exchange
     * sans datasetId
     * retour tout
     **/
//    @Test
    // @TODO à corriger
    public void SX_No_DatasetId() throws JAXBException {

        IMap<SiriObjectStorageKey, PtSituationElement> originalSaved = situations.getSituationElements();
        HazelcastTestMap<PtSituationElement> testMap = new HazelcastTestMap<PtSituationElement>();
        situations.setSituationElements(testMap);

        List<GtfsRTApi> gtfsRTApiList = new ArrayList<>();
        GtfsRTApi firstSub = new GtfsRTApi();
        firstSub.setActive(true);
        firstSub.setDatasetId("TEST1");
        gtfsRTApiList.add(firstSub);

        GtfsRTApi second = new GtfsRTApi();
        second.setActive(true);
        second.setDatasetId("TEST2");
        gtfsRTApiList.add(second);
        subscriptionConfig.setGtfsRTApis(gtfsRTApiList);


        File fileInject1 = new File("src/test/resources/siri-sx-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sx", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-sx-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sx", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(situations.getAll().isEmpty());
        situations.cleanChangesMap();


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <SituationExchangeRequest version=\"2.0\">\n" +
                "        </SituationExchangeRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(null, xml, null, SiriHandler.getIdMappingPolicy("false", "false"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getSituationExchangeDeliveries().isEmpty());
        assertEquals(2, response.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements().size());
        situations.setSituationElements(originalSaved);
    }

    /**
     * Facility monitoring
     * id producteur
     * useOriginalId true
     * avec datasetId
     * retour données producteurs identifiants locaux
     **/
//    @Test
    public void FM_idProducer_DatasetId() throws JAXBException {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-fm-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-fm", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-fm-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-fm", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(facilityMonitoring.getAll().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <FacilityMonitoringRequest version=\"2.0\">\n" +
                "        </FacilityMonitoringRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);
        ;

        Siri response = handler.handleIncomingSiri(null, xml, "TEST1", SiriHandler.getIdMappingPolicy("false", "false"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getFacilityMonitoringDeliveries().isEmpty());
    }

    /**
     * Facility monitoring
     * id producteur
     * userOriginalId true
     * sans datasetId
     * retour rien
     **/
//    @Test
    public void FM_idProducer_No_DatasetId() throws JAXBException {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-fm-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-fm", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-fm-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-fm", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(facilityMonitoring.getAll().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <FacilityMonitoringRequest version=\"2.0\">\n" +
                "            <MonitoringRef>TEST1:StopPoint:SP:121:LOC</MonitoringRef>\n" +
                "        </FacilityMonitoringRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(null, xml, null, SiriHandler.getIdMappingPolicy("false", "false"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertTrue(response.getServiceDelivery().getFacilityMonitoringDeliveries().isEmpty());
    }


    /**
     * General message
     * useOriginalId true
     * avec datasetId
     * retour données producteurs identifiants locaux
     **/
//    @Test
    public void GM_datasetId() throws UnmarshalException {
        File fileInject1 = new File("src/test/resources/siri-gm-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-gm", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-gm-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-gm", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(generalMessage.getAll().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <GeneralMessageRequest version=\"2.0\">\n" +
                "        </GeneralMessageRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xmlLine = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(null, xmlLine, "TEST1", SiriHandler.getIdMappingPolicy("false", "false"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertEquals(1, response.getServiceDelivery().getGeneralMessageDeliveries().size());
    }

    /**
     * General message
     * userOriginalId true
     * sans datasetId
     * retour rien
     **/
//    @Test
    public void GM_No_datasetId() throws UnmarshalException {
        File fileInject1 = new File("src/test/resources/siri-gm-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-gm", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-gm-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-gm", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(generalMessage.getAll().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <GeneralMessageRequest version=\"2.0\">\n" +
                "        </GeneralMessageRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(null, xml, null, SiriHandler.getIdMappingPolicy("false", "false"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getGeneralMessageDeliveries().isEmpty());
        assertTrue(response.getServiceDelivery().getGeneralMessageDeliveries().get(0).getGeneralMessages().isEmpty());
    }


    /**
     * Stop monitoring
     * avec datasetId
     * retour données du datasetId
     **/
    @Test
    public void SM_idMobi_datasetId() throws JAXBException {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-sm-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-sm-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(stopVisits.getAll().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <StopMonitoringRequest version=\"2.0\">\n" +
                "            <MonitoringRef>MOBIITI:Quay:a</MonitoringRef>\n" +
                "        </StopMonitoringRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(null, xml, "TEST1", SiriHandler.getIdMappingPolicy("false", "false"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().isEmpty());
        assertEquals(1, response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().size());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef().getValue());
        assertEquals("MOBIITI:Quay:a", response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef().getValue());
    }

    /**
     * Stop monitoring
     * sans datasetId
     * retour données de tous les producteurs
     **/
    @Test
    public void SM_idMobi_No_datasetId() throws JAXBException {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-sm-test1.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject1, fileInject1.getPath(), "TEST1");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        File fileInject2 = new File("src/test/resources/siri-sm-test2.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject2, fileInject2.getPath(), "TEST2");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }

        assertFalse(stopVisits.getAll().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <StopMonitoringRequest version=\"2.0\">\n" +
                "            <MonitoringRef>MOBIITI:Quay:a</MonitoringRef>\n" +
                "        </StopMonitoringRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(null, xml, null, SiriHandler.getIdMappingPolicy("false", "false"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().isEmpty());
        assertEquals(2, response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().size());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef().getValue());
        assertEquals("MOBIITI:Quay:a", response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef().getValue());
    }


    @Test
    public void SM_AltID_DatasetId() throws JAXBException {
        File file = new File("src/test/resources/stops_mapping.csv");
        externalIdsService.feedCacheStopWithFile(file, "TEST1");

        File fileInject = new File("src/test/resources/siri-sm-test1-alt.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject, fileInject.getPath(), "TEST1");
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

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(null, xml, "TEST1", SiriHandler.getIdMappingPolicy("false", "true"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().isEmpty());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef().getValue());
        assertEquals("30", response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef().getValue());
    }

    @Test
    public void SM_AltID_No_DatasetId() throws JAXBException {
        File file = new File("src/test/resources/stops_mapping.csv");
        externalIdsService.feedCacheStopWithFile(file, "TEST1");

        File fileInject = new File("src/test/resources/siri-sm.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject, fileInject.getPath(), "TEST1");
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

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(null, xml, null, SiriHandler.getIdMappingPolicy("false", "true"), -1, null);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        assertTrue(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().isEmpty());
    }


    /**
     * Vehicle monitoring
     * avec altId
     * avec datasetId
     * retour données identifiants producteurs locaux
     **/
    @Test
    public void VM_AltId_DatasetId() throws JAXBException {
        File file = new File("src/test/resources/lines_mapping.csv");
        externalIdsService.feedCacheLineWithFile(file, "TEST");

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


        InputStream xmlLineRef12 = IOUtils.toInputStream(stringXmlLineRef12, StandardCharsets.UTF_8);
        ;

        Siri responseLineRef12 = handler.handleIncomingSiri(null, xmlLineRef12, "TEST", SiriHandler.getIdMappingPolicy("false", "true"), -1, null);
        assertNotNull(responseLineRef12);
        assertNotNull(responseLineRef12.getServiceDelivery());
        assertFalse(responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().isEmpty());
        assertFalse(responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().isEmpty());
        assertNotNull(responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().get(0).getVehicleMonitoringRef());
        assertNotNull(responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().get(0).getVehicleMonitoringRef());
        assertNotNull(responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().get(0).getVehicleMonitoringRef().getValue());
        assertEquals("TEST:VehicleJourney::23:LOC", responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().get(0).getVehicleMonitoringRef().getValue());
        assertEquals("12", responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().get(0).getMonitoredVehicleJourney().getLineRef().getValue());

        String stringXmlLineRef34 = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "           <VehicleMonitoringRequest version=\"2.0\">\n" +
                "               <LineRef>34</LineRef>\n" +
                "           </VehicleMonitoringRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";


        InputStream xmlLineRef34 = IOUtils.toInputStream(stringXmlLineRef34, StandardCharsets.UTF_8);
        ;

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
        assertEquals("TEST:VehicleJourney::232:LOC", vehicleActivityStructures.get(0).getVehicleMonitoringRef().getValue());
        assertEquals("34", vehicleActivityStructures.get(0).getMonitoredVehicleJourney().getLineRef().getValue());

        assertNotNull(vehicleActivityStructures.get(1).getVehicleMonitoringRef());
        assertNotNull(vehicleActivityStructures.get(1).getVehicleMonitoringRef());
        assertNotNull(vehicleActivityStructures.get(1).getVehicleMonitoringRef().getValue());
        assertEquals("TEST:VehicleJourney::233:LOC", vehicleActivityStructures.get(1).getVehicleMonitoringRef().getValue());
        assertEquals("34", vehicleActivityStructures.get(1).getMonitoredVehicleJourney().getLineRef().getValue());
    }

}
