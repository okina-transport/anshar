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

import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.data.MonitoredStopVisits;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.AnnotatedLineRef;
import uk.org.siri.siri20.AnnotatedStopPointStructure;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri20.MonitoredStopVisit;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.Siri;

import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static junit.framework.TestCase.*;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SiriHandlerTest extends SpringBootBaseTest {

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private SiriHandler handler;

    @Autowired
    private Situations situations;

    @Autowired
    private MonitoredStopVisits stopVisits;

    @Autowired
    private EstimatedTimetables estimatedTimetables;

   // @Test
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
        File file = new File("src/test/resources/PT_RT_STOPTIME_SYTRAL_siri-sm_dynamic.xml");

        try {
            handler.handleIncomingSiri(smSubscription.getSubscriptionId(), new ByteArrayInputStream(FileUtils.readFileToByteArray(file)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Collection<MonitoredStopVisit> savedStopVisits = stopVisits.getAll();

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
            assertEquals(result.getStopPointsDelivery().getAnnotatedStopPointReves().size(),4);
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

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File("src/test/resources/discoveryTest/lines_discovery_test.xml");

        try {
            Siri result = handler.handleIncomingSiri(null, new ByteArrayInputStream(FileUtils.readFileToByteArray(file)), null, null, null, -1, null);
            assertNotNull(result.getLinesDelivery());
            assertNotNull(result.getLinesDelivery().getAnnotatedLineReves());
            assertEquals(result.getLinesDelivery().getAnnotatedLineReves().size(),3);
            List<String> expectedLineRef = Arrays.asList("line1", "line2", "TEST:Line:1");

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

        assertTrue(4 == savedSituations.size());

        for(PtSituationElement savedSituation :  savedSituations) {
            assertNotNull(savedSituation.getValidityPeriods());
            assertNotEquals(savedSituation.getValidityPeriods().size(), 0);
            for(HalfOpenTimestampOutputRangeStructure validityPeriod : savedSituation.getValidityPeriods()){
                assertNotNull(validityPeriod.getStartTime());
            }
        }
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
}
