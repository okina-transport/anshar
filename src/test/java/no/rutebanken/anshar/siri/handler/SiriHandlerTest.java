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
import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.IncomingSiriParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.*;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.mapping.LineUpdaterService;
import no.rutebanken.anshar.routes.mapping.OutputExternalIdsService;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.SiriApisRequestHandlerRoute;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SiriHandlerTest extends SpringBootBaseTest {

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
    private OutputExternalIdsService outputExternalIdsService;

    @Autowired
    private SiriApisRequestHandlerRoute siriApisRequestHandlerRoute;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private DiscoveryCache discoveryCache;


    @BeforeEach
    void init() {
        subscriptionManager.clearAllSubscriptions();
        estimatedTimetables.clearAll();
        vehicleActivities.clearAll();
        situations.clearAll();
        stopVisits.clearAll();
        generalMessage.clearAll();
        facilityMonitoring.clearAll();
        SiriValueTransformer.clearCachedGettersForAdapter();
        subscriptionConfig.getIdProcessingParameters().clear();
    }


    @ParameterizedTest(name = "{1}")
    @CsvSource({
            "SITUATION_EXCHANGE, SituationExchangeDelivery, false, false",
            "ESTIMATED_TIMETABLE, EstimatedTimetableDelivery, false, true",
            "VEHICLE_MONITORING, VehicleMonitoringDelivery, false, false",
            "STOP_MONITORING, StopMonitoringDelivery, true, true",
            "FACILITY_MONITORING, FacilityMonitoringDelivery, true, true",
    })
    void testErrorInServiceDelivery(SiriDataType dataType, String deliveryTag, boolean addStopMonitoringRef, boolean enabled) {
        Assumptions.assumeTrue(enabled, "No idea why this is disabled");

        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <siri:Siri xmlns:siri="http://www.siri.org.uk/siri">
                  <siril:ServiceDelivery xmlns:siril="http://www.siri.org.uk/siri">
                    <ResponseTimestamp xmlns="http://www.siri.org.uk/siri">2016-11-10T04:27:15.9028457+01:00</ResponseTimestamp>
                    <ProducerRef xmlns="http://www.siri.org.uk/siri">ATB</ProducerRef>
                    <ResponseMessageIdentifier xmlns="http://www.siri.org.uk/siri">R_</ResponseMessageIdentifier>
                    <%1$s xmlns="http://www.siri.org.uk/siri" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" version="2.0">
                      <ResponseTimestamp>2016-11-10T04:27:15.9028457+01:00</ResponseTimestamp>
                      <RequestMessageRef>e1995179-cc74-4354-84b2-dbb9850c1b9a</RequestMessageRef>
                      <Status>false</Status>
                      <ErrorCondition>
                        <NoInfoForTopicError/>
                        <Description>Unable to connect to the remote server</Description>
                      </ErrorCondition>
                    </%1$s>
                  </siril:ServiceDelivery>
                </siri:Siri>
                """.formatted(deliveryTag);

        try {
            SubscriptionSetup subscription = getSubscriptionSetup(dataType, "tst");
            if (addStopMonitoringRef) {
                subscription.getStopMonitoringRefValues().add("sp3");
            }
            subscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);
            handler.handleIncomingSiri(IncomingSiriParameters.buildFromSubscription(subscription.getSubscriptionId(), new ByteArrayInputStream(xml.getBytes())));
        } catch (Exception e) {
            fail("Handling empty response caused exception");
        }
    }


    /**
     * Test to check that file given by cityway complies with okina management rules
     *
     * @throws JAXBException
     */
    @Test
    void testCitywaySxCompliance() throws Exception {
        SubscriptionSetup sxSubscription = getSxSubscription("tst");
        subscriptionManager.addSubscription(sxSubscription.getSubscriptionId(), sxSubscription);
        File file = new File("src/test/resources/PT_EVENT_CG38_siri-sx_dynamic.xml");

        handler.handleIncomingSiri(IncomingSiriParameters.buildFromSubscription(sxSubscription.getSubscriptionId(), new ByteArrayInputStream(FileUtils.readFileToByteArray(file))));
    }

    /**
     * Test to check that file given by cityway complies with okina management rules
     *
     * @throws JAXBException
     */
    @Test
    void testCitywaySmCompliance() throws Exception {
        SubscriptionSetup smSubscription = getSmSubscription("tst");
        smSubscription.getStopMonitoringRefValues().add("sp4");
        subscriptionManager.addSubscription(smSubscription.getSubscriptionId(), smSubscription);
        File file = new File("src/test/resources/PT_RT_STOPTIME_TEST_siri-sm_dynamic.xml");
        handler.handleIncomingSiri(IncomingSiriParameters.buildFromSubscription(smSubscription.getSubscriptionId(), new ByteArrayInputStream(FileUtils.readFileToByteArray(file))));
    }

    @Test
    @Disabled("No idea why this is disabled")
    void testFmComplianceExpired() throws Exception {
        facilityMonitoring.clearAll();
        SubscriptionSetup fmSubscription = getFmSubscription("tst");
        subscriptionManager.addSubscription(fmSubscription.getSubscriptionId(), fmSubscription);
        File file = new File("src/test/resources/fm_example_expired_delivery.xml");

        handler.handleIncomingSiri(IncomingSiriParameters.buildFromSubscription(fmSubscription.getSubscriptionId(), new ByteArrayInputStream(FileUtils.readFileToByteArray(file))));

        Collection<FacilityConditionStructure> savedfacilities = facilityMonitoring.getAll();
        assertTrue(savedfacilities.isEmpty(), "La liste des objets ajouté doit être vide");
    }

    @Test
    @Disabled("No idea why this is disabled")
    void testFmCompliance() throws Exception {
        facilityMonitoring.clearAll();
        SubscriptionSetup fmSubscription = getFmSubscription("tst");
        subscriptionManager.addSubscription(fmSubscription.getSubscriptionId(), fmSubscription);
        File file = new File("src/test/resources/fm_example_delivery.xml");

        handler.handleIncomingSiri(IncomingSiriParameters.buildFromSubscription(fmSubscription.getSubscriptionId(), new ByteArrayInputStream(FileUtils.readFileToByteArray(file))));

        Collection<FacilityConditionStructure> savedfacilities = facilityMonitoring.getAll();
        assertFalse(savedfacilities.isEmpty(), "Un objet a dû être ajouté");
    }

    @Test
    @Disabled("No idea why this is disabled")
    void testGmCompliance() throws Exception {
        generalMessage.clearAll();
        SubscriptionSetup gmSubscription = getGmSubscription("tst");
        subscriptionManager.addSubscription(gmSubscription.getSubscriptionId(), gmSubscription);
        File file = new File("src/test/resources/gm_example_delivery.xml");

        handler.handleIncomingSiri(IncomingSiriParameters.buildFromSubscription(gmSubscription.getSubscriptionId(), new ByteArrayInputStream(FileUtils.readFileToByteArray(file))));

        Collection<GeneralMessage> savedGeneralMessages = generalMessage.getAll();
        assertFalse(savedGeneralMessages.isEmpty(), "Un objet a dû être ajouté");
    }

    /**
     * Test to check that file given by cityway complies with okina management rules
     *
     * @throws JAXBException
     */
    @Test
    @Disabled("No idea why this is disabled")
    void testCitywayEtCompliance() throws Exception {

        SubscriptionSetup etSubscription = getEtSubscription("tst");
        subscriptionManager.addSubscription(etSubscription.getSubscriptionId(), etSubscription);
        File file = new File("src/test/resources/PT_RT_STOPTIME_STAS_siri-et_dynamic.xml");

        handler.handleIncomingSiri(IncomingSiriParameters.buildFromSubscription(etSubscription.getSubscriptionId(), new ByteArrayInputStream(FileUtils.readFileToByteArray(file))));
    }

    @Test
    void stopPointsDiscoveryTest() throws Exception {
        discoveryCache.clearDiscoveryStops();
        Map<String, Pair<String, String>> stopPlaceMap;
        stopPlaceMap = new HashMap<>();
        stopPlaceMap.put("DAT1:Quay:sp1", Pair.of("MOBIITI:Quay:a", "sp1Name"));
        stopPlaceMap.put("DAT1:Quay:sp2", Pair.of("MOBIITI:Quay:b", "sp2Name"));


        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);

        stopPlaceService.addStopPlaceMappings(stopPlaceMap);


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

        File file = new File("src/test/resources/discoveryTest/stop_points_discovery_test.xml");

        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(new ByteArrayInputStream(FileUtils.readFileToByteArray(file)));
        params.setDatasetId("DAT1");
        params.setOutboundIdMappingPolicy(OutboundIdMappingPolicy.ORIGINAL_ID);
        params.setMaxSize(-1);
        params.setSoapTransformation(false);

        Siri result = handler.handleIncomingSiri(params);
        assertNotNull(result.getStopPointsDelivery());
        assertNotNull(result.getStopPointsDelivery().getAnnotatedStopPointReves());
        assertEquals(2, result.getStopPointsDelivery().getAnnotatedStopPointReves().size());
        List<String> expectedPointRef = Arrays.asList("sp1", "sp2", "sp3", "sp4");
        for (AnnotatedStopPointStructure annotatedStopPointReve : result.getStopPointsDelivery().getAnnotatedStopPointReves()) {
            assertTrue(expectedPointRef.contains(annotatedStopPointReve.getStopPointRef().getValue()));

            assertEquals(annotatedStopPointReve.getStopPointRef().getValue() + "Name", annotatedStopPointReve.getStopNames().getFirst().getValue());
        }
    }

    @Test
    @Disabled("No idea why this is disabled")
    void stopPointsDiscoveryTestWithDifferentDatasetId() throws Exception {
        SubscriptionSetup smSubscription1 = getSmSubscription("tst1");
        smSubscription1.getStopMonitoringRefValues().add("sp1");
        subscriptionManager.addSubscription(smSubscription1.getSubscriptionId(), smSubscription1);

        SubscriptionSetup smSubscription2 = getSmSubscription("tst2");
        smSubscription2.getStopMonitoringRefValues().add("sp2");
        subscriptionManager.addSubscription(smSubscription2.getSubscriptionId(), smSubscription2);

        File file = new File("src/test/resources/discoveryTest/stop_points_discovery_test.xml");

        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(new ByteArrayInputStream(FileUtils.readFileToByteArray(file)));
        params.setMaxSize(-1);
        params.setSoapTransformation(false);


        Siri result = handler.handleIncomingSiri(params);
        assertNotNull(result.getStopPointsDelivery());
        assertNotNull(result.getStopPointsDelivery().getAnnotatedStopPointReves());
        assertEquals(2, result.getStopPointsDelivery().getAnnotatedStopPointReves().size());
        List<String> expectedPointRef = Arrays.asList("sp1", "sp2", "sp3", "sp4");
        for (AnnotatedStopPointStructure annotatedStopPointReve : result.getStopPointsDelivery().getAnnotatedStopPointReves()) {
            assertTrue(expectedPointRef.contains(annotatedStopPointReve.getStopPointRef().getValue()));
        }
    }


    @Test
    void linesDiscoveryTest() throws Exception {
        estimatedTimetables.clearAll();
        discoveryCache.clearDiscoveryLines();
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

        LineUpdaterService lineUpdaterService = ApplicationContextHolder.getContext().getBean(LineUpdaterService.class);
        lineUpdaterService.addLineName("line1", "line1Name");
        lineUpdaterService.addLineName("line2", "line2Name");
        lineUpdaterService.addLineName("line3", "line3Name");
        lineUpdaterService.addLineName("line4", "line4Name");


        File file = new File("src/test/resources/discoveryTest/lines_discovery_test.xml");

        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(new ByteArrayInputStream(FileUtils.readFileToByteArray(file)));
        params.setMaxSize(-1);
        params.setSoapTransformation(false);

        Siri result = handler.handleIncomingSiri(params);
        assertNotNull(result.getLinesDelivery());
        assertNotNull(result.getLinesDelivery().getAnnotatedLineReves());
        assertEquals(4, result.getLinesDelivery().getAnnotatedLineReves().size());
        List<String> expectedLineRef = Arrays.asList("line1", "line2", "line3", "line4");

        for (AnnotatedLineRef annotatedLineReve : result.getLinesDelivery().getAnnotatedLineReves()) {
            assertTrue(expectedLineRef.contains(annotatedLineReve.getLineRef().getValue()));
            assertEquals(annotatedLineReve.getLineRef().getValue() + "Name", annotatedLineReve.getLineNames().getFirst().getValue());
        }

    }

    @Test
    void linesDiscoveryTestWithDifferentDatasetId() throws Exception {
        estimatedTimetables.clearAll();
        discoveryCache.clearDiscoveryLines();
        SubscriptionSetup vmSubscription1 = getVmSubscription("tst1");
        vmSubscription1.getLineRefValues().add("line1");
        subscriptionManager.addSubscription(vmSubscription1.getSubscriptionId(), vmSubscription1);

        SubscriptionSetup vmSubscription2 = getVmSubscription("tst2");
        vmSubscription2.getLineRefValues().add("line2");
        subscriptionManager.addSubscription(vmSubscription2.getSubscriptionId(), vmSubscription2);

        SubscriptionSetup vmSubscription3 = getVmSubscription("tst3");
        vmSubscription3.getLineRefValues().add("line3");
        subscriptionManager.addSubscription(vmSubscription3.getSubscriptionId(), vmSubscription3);

        estimatedTimetables.add(getVmSubscription("tst3").getDatasetId(), createEstimatedVehicleJourney("line3", "vehicle3", ZonedDateTime.now().plusHours(1)));
        estimatedTimetables.add(getVmSubscription("tst4").getDatasetId(), createEstimatedVehicleJourney("line4", "vehicle4", ZonedDateTime.now().plusHours(1)));


        discoveryCache.addLine("tst1", "line1");
        discoveryCache.addLine("tst2", "line2");
        discoveryCache.addLine("tst3", "line3");
        discoveryCache.addLine("tst4", "line4");

        File file = new File("src/test/resources/discoveryTest/lines_discovery_test.xml");

        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(new ByteArrayInputStream(FileUtils.readFileToByteArray(file)));
        params.setMaxSize(-1);
        params.setSoapTransformation(false);

        Siri result = handler.handleIncomingSiri(params);
        assertNotNull(result.getLinesDelivery());
        assertNotNull(result.getLinesDelivery().getAnnotatedLineReves());
        assertEquals(4, result.getLinesDelivery().getAnnotatedLineReves().size());
        List<String> expectedLineRef = Arrays.asList("line1", "line2", "line3", "line4");

        for (AnnotatedLineRef annotatedLineReve : result.getLinesDelivery().getAnnotatedLineReves()) {
            assertTrue(expectedLineRef.contains(annotatedLineReve.getLineRef().getValue()));
        }

    }

    /**
     * Test to check sx with and without validity period and start time
     *
     * @throws JAXBException
     */
    @Test
    void testSxValidityPeriodStartTime() throws Exception {
        SubscriptionSetup sxSubscription = getSxSubscription("tst");
        subscriptionManager.addSubscription(sxSubscription.getSubscriptionId(), sxSubscription);
        File file = new File("src/test/resources/siri-sx_validity_period_start_time.xml");

        handler.handleIncomingSiri(IncomingSiriParameters.buildFromSubscription(sxSubscription.getSubscriptionId(), new ByteArrayInputStream(FileUtils.readFileToByteArray(file))));

        Collection<PtSituationElement> savedSituations = situations.getAll();

        assertEquals(4, savedSituations.size());

        for (PtSituationElement savedSituation : savedSituations) {
            assertNotNull(savedSituation.getValidityPeriods());
            assertNotEquals(0, savedSituation.getValidityPeriods().size());
            for (HalfOpenTimestampOutputRangeStructure validityPeriod : savedSituation.getValidityPeriods()) {
                assertNotNull(validityPeriod.getStartTime());
            }
        }
    }

    void initStopPlaceMapper() {
        resetIdProcessings();
        Map<String, Pair<String, String>> stopPlaceMap;

        stopPlaceMap = new HashMap<>();
        stopPlaceMap.put("TEST1:Quay:121", Pair.of("MOBIITI:Quay:a", "test1"));
        stopPlaceMap.put("TEST2:Quay:122", Pair.of("MOBIITI:Quay:a", "test2"));
        stopPlaceMap.put("TEST3:StopPoint:SP:123:LOC", Pair.of("MOBIITI:Quay:b", "test3"));
        stopPlaceMap.put("TEST4:StopPoint:SP:124:LOC", Pair.of("MOBIITI:Quay:b", "test4"));

        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);

        //Manually adding custom mapping to Spring context
        stopPlaceService.addStopPlaceMappings(stopPlaceMap);

        Set<String> stopQuays = new HashSet<>(stopPlaceMap.keySet());
        stopPlaceService.addStopQuays(stopQuays);

        Map<String, Set<String>> stopPlaceReverseMap = new HashMap<>();
        Set<String> originalIds = new HashSet<>();
        originalIds.add("TEST1:Quay:121");
        originalIds.add("TEST2:Quay:122");
        stopPlaceReverseMap.put("MOBIITI:Quay:a", originalIds);

        Set<String> originalIds2 = new HashSet<>();
        originalIds2.add("TEST1:StopPoint:SP:123:LOC");
        originalIds2.add("TEST2:StopPoint:SP:124:LOC");
        stopPlaceReverseMap.put("MOBIITI:Quay:b", originalIds2);
        stopPlaceService.addStopPlaceReverseMappings(stopPlaceReverseMap);
    }


    private void resetIdProcessings() {
        subscriptionConfig.getIdProcessingParameters().clear();


        IdProcessingParameters dat1Stop = new IdProcessingParameters();
        dat1Stop.setObjectType(ObjectType.STOP);
        dat1Stop.setDatasetId("TEST1");
        dat1Stop.setOutputPrefixToAdd("TEST1:Quay:");
        subscriptionConfig.getIdProcessingParameters().add(dat1Stop);

        IdProcessingParameters dat2Stop = new IdProcessingParameters();
        dat2Stop.setObjectType(ObjectType.STOP);
        dat2Stop.setDatasetId("TEST2");
        dat2Stop.setOutputPrefixToAdd("TEST2:Quay:");
        subscriptionConfig.getIdProcessingParameters().add(dat2Stop);

        IdProcessingParameters dat3VJ = new IdProcessingParameters();
        dat3VJ.setObjectType(ObjectType.LINE);
        dat3VJ.setDatasetId("TEST");
        subscriptionConfig.getIdProcessingParameters().add(dat3VJ);

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
                new EnumMap<>(RequestType.class),
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
                true,
                ZonedDateTime.now()
        );
    }

    private EstimatedVehicleJourney createEstimatedVehicleJourney(String lineRefValue, String vehicleRefValue, ZonedDateTime arrival) {
        return createEstimatedVehicleJourney(lineRefValue, vehicleRefValue, arrival, arrival);
    }

    private EstimatedVehicleJourney createEstimatedVehicleJourney(String lineRefValue, String vehicleRefValue, ZonedDateTime arrival, ZonedDateTime departure) {
        EstimatedVehicleJourney element = new EstimatedVehicleJourney();
        LineRef lineRef = new LineRef();
        lineRef.setValue(lineRefValue);
        element.setLineRef(lineRef);
        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue(vehicleRefValue);
        element.setVehicleRef(vehicleRef);
        element.setIsCompleteStopSequence(true);

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
        for (int i = 0; i < 30; i++) {

            StopPointRefStructure stopPointRef = new StopPointRefStructure();
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
    void SM_idProducer_DatasetId() throws Exception {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-sm-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-sm-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject2, fileInject2.getPath(), "TEST2");

        assertFalse(stopVisits.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <StopMonitoringRequest version="2.0">
                            <MonitoringRef>121</MonitoringRef>
                        </StopMonitoringRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(xml);
        params.setDatasetId("TEST1");
        params.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("true", "false"));
        params.setMaxSize(-1);

        Siri response = handler.handleIncomingSiri(params);
        assertNotNull(response);
        Assertions.assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().getFirst().getMonitoringRef());
    }

    /**
     * Stop monitoring
     * id producteur
     * useOriginalId true
     * sans datasetId
     * retour rien
     **/
    @Test
    void SM_idProducer_No_DatasetId() throws Exception {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-sm-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-sm-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject2, fileInject2.getPath(), "TEST2");

        assertFalse(stopVisits.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <StopMonitoringRequest version="2.0">
                            <MonitoringRef>TEST1:StopPoint:SP:121:LOC</MonitoringRef>
                        </StopMonitoringRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);
        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(xml);
        params.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("true", "false"));
        params.setMaxSize(-1);

        Siri response = handler.handleIncomingSiri(params);
        assertNotNull(response);
        Assertions.assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        Assertions.assertTrue(response.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().isEmpty());
    }

    /**
     * Stop monitoring
     * id producteur
     * useOriginalId false
     * sans datasetId
     * retour rien
     **/
    @Test
    void SM_IdProducer_UseOriginalId_False_No_DatasetId() throws Exception {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-sm-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-sm-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject2, fileInject2.getPath(), "TEST2");

        assertFalse(stopVisits.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <StopMonitoringRequest version="2.0">
                            <MonitoringRef>TEST1:StopPoint:SP:121:LOC</MonitoringRef>
                        </StopMonitoringRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);
        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(xml);
        params.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("false", "false"));
        params.setMaxSize(-1);

        Siri response = handler.handleIncomingSiri(params);
        assertNotNull(response);
        Assertions.assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        Assertions.assertTrue(response.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().isEmpty());
    }

    /**
     * Stop monitoring
     * id producteur
     * useOriginalId false
     * avec datasetId
     * retour rien
     **/
    @Test
    void SM_IdProducer_UseOriginalId_False_DatasetId() throws Exception {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-sm-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-sm-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject2, fileInject2.getPath(), "TEST2");

        assertFalse(stopVisits.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <StopMonitoringRequest version="2.0">
                            <MonitoringRef>TEST1:StopPoint:SP:121:LOC</MonitoringRef>
                        </StopMonitoringRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);
        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(xml);
        params.setMaxSize(-1);
        params.setDatasetId("TEST1");
        params.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("false", "false"));


        Siri response = handler.handleIncomingSiri(params);
        assertNotNull(response);
        Assertions.assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        Assertions.assertTrue(response.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().isEmpty());
    }

    /**
     * Stop monitoring
     * sans id
     * useOriginalId false
     * avec datasetId
     * retour tous les points d'arrêt du datasetId
     **/
    @Test
    void SM_No_Id_UseOriginalId_False_DatasetId() throws Exception {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-sm-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-sm-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject2, fileInject2.getPath(), "TEST2");
        File fileInject3 = new File("src/test/resources/siri-sm-test3.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject3, fileInject1.getPath(), "TEST3");

        File fileInject4 = new File("src/test/resources/siri-sm-test4.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject4, fileInject2.getPath(), "TEST4");

        assertFalse(stopVisits.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <StopMonitoringRequest version="2.0">
                        </StopMonitoringRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);
        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(xml);
        params.setDatasetId("TEST1");
        params.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("false", "false"));
        params.setMaxSize(-1);


        Siri response = handler.handleIncomingSiri(params);
        assertNotNull(response);
        Assertions.assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().getFirst().getMonitoringRef());
    }

    /**
     * Stop monitoring
     * sans id
     * useOriginalId false
     * pas de datasetId
     * retour rien
     **/
    @Test
    void SM_No_Id_UseOriginalId_False_No_DatasetId() throws Exception {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-sm-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-sm-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject2, fileInject2.getPath(), "TEST2");
        File fileInject3 = new File("src/test/resources/siri-sm-test3.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject3, fileInject1.getPath(), "TEST3");

        File fileInject4 = new File("src/test/resources/siri-sm-test4.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject4, fileInject2.getPath(), "TEST4");

        assertFalse(stopVisits.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <StopMonitoringRequest version="2.0">
                        </StopMonitoringRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(xml);
        params.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("false", "false"));
        params.setMaxSize(-1);
        Siri response = handler.handleIncomingSiri(params);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        assertTrue(response.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().isEmpty());
    }

    /**
     * Estimated timetable
     * avec id
     * avec datasetId
     * useOriginalId true
     * retour données producteurs identifiants locaux
     **/
    @Test
    void ET_Id_DatasetId_UseOriginalId_True() throws Exception {
        resetIdProcessings();
        SiriValueTransformer.clearCachedGettersForAdapter();
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-et-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-et-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject2, fileInject2.getPath(), "TEST2");

        assertFalse(estimatedTimetables.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <EstimatedTimetableRequest version="2.0">
                           <Lines>
                               <LineDirection>
                                   <LineRef>1</LineRef>
                               </LineDirection>
                           </Lines>
                        </EstimatedTimetableRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(xml);
        params.setDatasetId("TEST1");
        params.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("true", "false"));
        params.setMaxSize(-1);

        Siri response = handler.handleIncomingSiri(params);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertEquals(1, response.getServiceDelivery().getEstimatedTimetableDeliveries().size());
        assertEquals("1", response.getServiceDelivery().getEstimatedTimetableDeliveries().getFirst().getEstimatedJourneyVersionFrames().getFirst().getEstimatedVehicleJourneies().getFirst().getLineRef().getValue());
        assertEquals("TEST1:VehicleJourney:1:LOC", response.getServiceDelivery().getEstimatedTimetableDeliveries().getFirst().getEstimatedJourneyVersionFrames().getFirst().getEstimatedVehicleJourneies().getFirst().getDatedVehicleJourneyRef().getValue());
        assertEquals("TEST1:Quay:121", response.getServiceDelivery().getEstimatedTimetableDeliveries().getFirst().getEstimatedJourneyVersionFrames().getFirst().getEstimatedVehicleJourneies().getFirst().getEstimatedCalls().getEstimatedCalls().getFirst().getStopPointRef().getValue());
    }

    /**
     * Estimated timetable
     * avec id
     * avec datasetId
     * useOriginalId false
     * retour données MOBIITI
     **/
    @Test
    void ET_Id_DatasetId_UseOriginalId_False() throws Exception {
        resetIdProcessings();
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-et-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-et-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject2, fileInject2.getPath(), "TEST2");

        assertFalse(estimatedTimetables.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <EstimatedTimetableRequest version="2.0">
                           <Lines>
                               <LineDirection>
                                   <LineRef>1</LineRef>
                               </LineDirection>
                           </Lines>
                        </EstimatedTimetableRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(xml);
        params.setDatasetId("TEST1");
        params.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("false", "false"));
        params.setMaxSize(-1);

        Siri response = handler.handleIncomingSiri(params);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertEquals(1, response.getServiceDelivery().getEstimatedTimetableDeliveries().size());
        assertEquals("1", response.getServiceDelivery().getEstimatedTimetableDeliveries().getFirst().getEstimatedJourneyVersionFrames().getFirst().getEstimatedVehicleJourneies().getFirst().getLineRef().getValue());
        assertEquals("TEST1:VehicleJourney:1:LOC", response.getServiceDelivery().getEstimatedTimetableDeliveries().getFirst().getEstimatedJourneyVersionFrames().getFirst().getEstimatedVehicleJourneies().getFirst().getDatedVehicleJourneyRef().getValue());
        assertEquals("MOBIITI:Quay:a", response.getServiceDelivery().getEstimatedTimetableDeliveries().getFirst().getEstimatedJourneyVersionFrames().getFirst().getEstimatedVehicleJourneies().getFirst().getEstimatedCalls().getEstimatedCalls().getFirst().getStopPointRef().getValue());
    }

    /**
     * Estimated timetable
     * avec id
     * sans datasetId
     * useOriginalId true
     * retour rien
     **/
    @Test
    void ET_Id_No_DatasetId_UseOriginalId_True() throws Exception {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-et-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-et-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject2, fileInject2.getPath(), "TEST2");

        assertFalse(estimatedTimetables.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <EstimatedTimetableRequest version="2.0">
                           <Lines>
                               <LineDirection>
                                   <LineRef>TEST1:Line:1:LOC</LineRef>
                               </LineDirection>
                           </Lines>
                        </EstimatedTimetableRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);
        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(xml);
        params.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("true", "false"));
        params.setMaxSize(-1);

        Siri response = handler.handleIncomingSiri(params);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getEstimatedTimetableDeliveries().isEmpty());
        assertTrue(response.getServiceDelivery().getEstimatedTimetableDeliveries().getFirst().getEstimatedJourneyVersionFrames().getFirst().getEstimatedVehicleJourneies().isEmpty());
    }

    /**
     * Estimated timetable
     * avec id
     * sans datasetId
     * useOriginalId false
     * retour rien
     **/
    @Test
    void ET_Id_No_DatasetId_UseOriginalId_False() throws Exception {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-et-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-et-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject2, fileInject2.getPath(), "TEST2");

        assertFalse(estimatedTimetables.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <EstimatedTimetableRequest version="2.0">
                           <Lines>
                               <LineDirection>
                                   <LineRef>TEST1:Line:1:LOC</LineRef>
                               </LineDirection>
                           </Lines>
                        </EstimatedTimetableRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);
        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(xml);
        params.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("false", "false"));
        params.setMaxSize(-1);

        Siri response = handler.handleIncomingSiri(params);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getEstimatedTimetableDeliveries().isEmpty());
        assertTrue(response.getServiceDelivery().getEstimatedTimetableDeliveries().getFirst().getEstimatedJourneyVersionFrames().getFirst().getEstimatedVehicleJourneies().isEmpty());
    }

    /**
     * Estimated timetable
     * sans id
     * useOriginalId false
     * sans datasetId
     * retour rien
     **/
    @Test
    void ET_No_Id_No_DatasetId_UseOriginalId_False() throws Exception {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-et-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-et-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject2, fileInject2.getPath(), "TEST2");

        assertFalse(estimatedTimetables.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <EstimatedTimetableRequest version="2.0">
                        </EstimatedTimetableRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);
        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(xml);
        params.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("false", "false"));
        params.setMaxSize(-1);

        Siri response = handler.handleIncomingSiri(params);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getEstimatedTimetableDeliveries().isEmpty());
        assertTrue(response.getServiceDelivery().getEstimatedTimetableDeliveries().getFirst().getEstimatedJourneyVersionFrames().getFirst().getEstimatedVehicleJourneies().isEmpty());
    }

    /**
     * Estimated timetable
     * sans id
     * useOriginalId false
     * avec datasetId
     * retour tous les ET du datasetId
     **/
    @Test
    void ET_No_Id_DatasetId_UseOriginalId_False() throws Exception {
        resetIdProcessings();
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-et-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-et-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", fileInject2, fileInject2.getPath(), "TEST2");

        assertFalse(estimatedTimetables.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <EstimatedTimetableRequest version="2.0">
                        </EstimatedTimetableRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);
        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(xml);
        params.setDatasetId("TEST1");
        params.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("false", "false"));
        params.setMaxSize(-1);

        Siri response = handler.handleIncomingSiri(params);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertEquals(1, response.getServiceDelivery().getEstimatedTimetableDeliveries().getFirst().getEstimatedJourneyVersionFrames().getFirst().getEstimatedVehicleJourneies().size());
        assertEquals("1", response.getServiceDelivery().getEstimatedTimetableDeliveries().getFirst().getEstimatedJourneyVersionFrames().getFirst().getEstimatedVehicleJourneies().getFirst().getLineRef().getValue());
        assertEquals("TEST1:VehicleJourney:1:LOC", response.getServiceDelivery().getEstimatedTimetableDeliveries().getFirst().getEstimatedJourneyVersionFrames().getFirst().getEstimatedVehicleJourneies().getFirst().getDatedVehicleJourneyRef().getValue());
        assertEquals("MOBIITI:Quay:a", response.getServiceDelivery().getEstimatedTimetableDeliveries().getFirst().getEstimatedJourneyVersionFrames().getFirst().getEstimatedVehicleJourneies().getFirst().getEstimatedCalls().getEstimatedCalls().getFirst().getStopPointRef().getValue());
    }

    /**
     * Vehicle monitoring
     * id producteur
     * avec datasetId
     * retour données producteurs identifiants locaux
     **/
    @Test
    void VM_DatasetId() throws Exception {
        File fileInject1 = new File("src/test/resources/siri-vm-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-vm", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-vm-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-vm", fileInject2, fileInject2.getPath(), "TEST2");

        assertFalse(vehicleActivities.getAll().isEmpty());


        String stringXmlLineRef = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <VehicleMonitoringRequest version="2.0">
                            <LineRef>TEST1::Line::1:LOC</LineRef>
                        </VehicleMonitoringRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xmlLineRef = IOUtils.toInputStream(stringXmlLineRef, StandardCharsets.UTF_8);
        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(xmlLineRef);
        params.setDatasetId("TEST1");
        params.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("true", "false"));
        params.setMaxSize(-1);


        Siri responseLineRef = handler.handleIncomingSiri(params);
        assertNotNull(responseLineRef);
        assertNotNull(responseLineRef.getServiceDelivery());
        assertFalse(responseLineRef.getServiceDelivery().getVehicleMonitoringDeliveries().isEmpty());
        assertEquals(1, responseLineRef.getServiceDelivery().getVehicleMonitoringDeliveries().getFirst().getVehicleActivities().size());
        assertEquals("TEST1::Line::1:LOC", responseLineRef.getServiceDelivery().getVehicleMonitoringDeliveries().getFirst().getVehicleActivities().getFirst().getMonitoredVehicleJourney().getLineRef().getValue());


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
//        assertEquals(1, responseVehicleRef.getServiceDelivery().getVehicleMonitoringDeliveries().getFirst().getVehicleActivities().size());
//        assertEquals("TEST1:VehicleJourney::1:LOC", responseVehicleRef.getServiceDelivery().getVehicleMonitoringDeliveries().getFirst().getVehicleActivities().getFirst().getVehicleMonitoringRef().getValue());
    }

    /**
     * Vehicle monitoring
     * sans datasetId
     * retour rien
     **/
    @Test
    void VM_No_DatasetId() throws Exception {
        File fileInject1 = new File("src/test/resources/siri-vm-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-vm", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-vm-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-vm", fileInject2, fileInject2.getPath(), "TEST2");

        assertFalse(vehicleActivities.getAll().isEmpty());


        String stringXmlLineRef = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <VehicleMonitoringRequest version="2.0">
                            <LineRef>TEST1::Line::1:LOC</LineRef>
                        </VehicleMonitoringRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xmlLineRef = IOUtils.toInputStream(stringXmlLineRef, StandardCharsets.UTF_8);
        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(xmlLineRef);
        params.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("false", "false"));
        params.setMaxSize(-1);

        Siri responseLineRef = handler.handleIncomingSiri(params);
        assertNotNull(responseLineRef);
        assertNotNull(responseLineRef.getServiceDelivery());
        assertFalse(responseLineRef.getServiceDelivery().getVehicleMonitoringDeliveries().isEmpty());
        assertEquals(0, responseLineRef.getServiceDelivery().getVehicleMonitoringDeliveries().getFirst().getVehicleActivities().size());


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
//        assertEquals(0, responseVehicleRef.getServiceDelivery().getVehicleMonitoringDeliveries().getFirst().getVehicleActivities().size());
    }

    /**
     * Situation exchange
     * avec datasetId
     * retour données producteurs identifiants locaux
     **/
    @Test
    @SuppressWarnings("unchecked")
    void SX_DatasetId() throws Exception {

        IMap<SiriObjectStorageKey, PtSituationElement> originalSaved = situations.getSituationElements();
        HazelcastTestMap<PtSituationElement> testMap = new HazelcastTestMap<PtSituationElement>();
        situations.setSituationElements(testMap);

        File fileInject1 = new File("src/test/resources/siri-sx-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sx", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-sx-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sx", fileInject2, fileInject2.getPath(), "TEST2");

        assertFalse(situations.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <SituationExchangeRequest version="2.0">
                        </SituationExchangeRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xmlLine = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);
        IncomingSiriParameters parameters = new IncomingSiriParameters();
        parameters.setIncomingSiriStream(xmlLine);
        parameters.setDatasetId("TEST1");
        parameters.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("false", "false"));
        parameters.setMaxSize(-1);

        Siri response = handler.handleIncomingSiri(parameters);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertEquals(1, response.getServiceDelivery().getSituationExchangeDeliveries().size());
        assertEquals("TEST1:J1", response.getServiceDelivery().getSituationExchangeDeliveries().getFirst().getSituations().getPtSituationElements().getFirst().getSituationNumber().getValue());
        situations.setSituationElements(originalSaved);
    }

    /**
     * Situation exchange
     * sans datasetId
     * retour tout
     **/
    @Test
    @Disabled("No idea why this is disabled")
    @SuppressWarnings("unchecked")
    void SX_No_DatasetId() throws Exception {

        IMap<SiriObjectStorageKey, PtSituationElement> originalSaved = situations.getSituationElements();
        HazelcastTestMap<PtSituationElement> testMap = new HazelcastTestMap<>();
        situations.setSituationElements(testMap);

        File fileInject1 = new File("src/test/resources/siri-sx-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sx", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-sx-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sx", fileInject2, fileInject2.getPath(), "TEST2");

        assertFalse(situations.getAll().isEmpty());
        situations.cleanChangesMap();


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <SituationExchangeRequest version="2.0">
                        </SituationExchangeRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        IncomingSiriParameters parameters = new IncomingSiriParameters();
        parameters.setIncomingSiriStream(xml);
        parameters.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("false", "false"));
        parameters.setMaxSize(-1);

        Siri response = handler.handleIncomingSiri(parameters);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getSituationExchangeDeliveries().isEmpty());
        assertEquals(2, response.getServiceDelivery().getSituationExchangeDeliveries().getFirst().getSituations().getPtSituationElements().size());
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
    void FM_idProducer_DatasetId() throws Exception {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-fm-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-fm", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-fm-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-fm", fileInject2, fileInject2.getPath(), "TEST2");

        assertFalse(facilityMonitoring.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <FacilityMonitoringRequest version="2.0">
                        </FacilityMonitoringRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);


        IncomingSiriParameters parameters = new IncomingSiriParameters();
        parameters.setIncomingSiriStream(xml);
        parameters.setDatasetId("TEST1");
        parameters.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("false", "false"));
        parameters.setMaxSize(-1);

        Siri response = handler.handleIncomingSiri(parameters);
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
    void FM_idProducer_No_DatasetId() throws Exception {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-fm-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-fm", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-fm-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-fm", fileInject2, fileInject2.getPath(), "TEST2");

        assertFalse(facilityMonitoring.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <FacilityMonitoringRequest version="2.0">
                            <MonitoringRef>TEST1:StopPoint:SP:121:LOC</MonitoringRef>
                        </FacilityMonitoringRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        IncomingSiriParameters parameters = new IncomingSiriParameters();
        parameters.setIncomingSiriStream(xml);
        parameters.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("false", "false"));
        parameters.setMaxSize(-1);

        Siri response = handler.handleIncomingSiri(parameters);
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
    void GM_datasetId() throws Exception {
        File fileInject1 = new File("src/test/resources/siri-gm-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-gm", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-gm-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-gm", fileInject2, fileInject2.getPath(), "TEST2");

        assertFalse(generalMessage.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <GeneralMessageRequest version="2.0">
                        </GeneralMessageRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xmlLine = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        IncomingSiriParameters parameters = new IncomingSiriParameters();
        parameters.setIncomingSiriStream(xmlLine);
        parameters.setDatasetId("TEST1");
        parameters.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("false", "false"));
        parameters.setMaxSize(-1);

        Siri response = handler.handleIncomingSiri(parameters);
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
    void GM_No_datasetId() throws Exception {
        File fileInject1 = new File("src/test/resources/siri-gm-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-gm", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-gm-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-gm", fileInject2, fileInject2.getPath(), "TEST2");

        assertFalse(generalMessage.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <GeneralMessageRequest version="2.0">
                        </GeneralMessageRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(createDefaultParameters(xml));
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getGeneralMessageDeliveries().isEmpty());
        assertTrue(response.getServiceDelivery().getGeneralMessageDeliveries().getFirst().getGeneralMessages().isEmpty());
    }


    private IncomingSiriParameters createDefaultParameters(InputStream xml) {
        IncomingSiriParameters parameters = new IncomingSiriParameters();
        parameters.setIncomingSiriStream(xml);
        parameters.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("false", "false"));
        parameters.setMaxSize(-1);
        return parameters;
    }


    /**
     * Stop monitoring
     * avec datasetId
     * retour données du datasetId
     **/
    @Test
    void SM_idMobi_datasetId() throws Exception {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-sm-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-sm-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject2, fileInject2.getPath(), "TEST2");

        assertFalse(stopVisits.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <StopMonitoringRequest version="2.0">
                            <MonitoringRef>MOBIITI:Quay:a</MonitoringRef>
                        </StopMonitoringRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        IncomingSiriParameters parameters = createDefaultParameters(xml);
        parameters.setDatasetId("TEST1");

        Siri response = handler.handleIncomingSiri(parameters);
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().isEmpty());
        assertEquals(1, response.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().size());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().getFirst().getMonitoringRef());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().getFirst().getMonitoringRef());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().getFirst().getMonitoringRef().getValue());
        assertEquals("MOBIITI:Quay:a", response.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().getFirst().getMonitoringRef().getValue());
    }

    /**
     * Stop monitoring
     * sans datasetId
     * retour données de tous les producteurs
     **/
    @Test
    void SM_idMobi_No_datasetId() throws Exception {
        initStopPlaceMapper();
        File fileInject1 = new File("src/test/resources/siri-sm-test1.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject1, fileInject1.getPath(), "TEST1");

        File fileInject2 = new File("src/test/resources/siri-sm-test2.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject2, fileInject2.getPath(), "TEST2");

        assertFalse(stopVisits.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <StopMonitoringRequest version="2.0">
                            <MonitoringRef>MOBIITI:Quay:a</MonitoringRef>
                        </StopMonitoringRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        Siri response = handler.handleIncomingSiri(createDefaultParameters(xml));
        assertNotNull(response);
        assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().isEmpty());
        assertEquals(2, response.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().size());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().getFirst().getMonitoringRef());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().getFirst().getMonitoringRef());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().getFirst().getMonitoringRef().getValue());
        assertEquals("MOBIITI:Quay:a", response.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().getFirst().getMonitoringRef().getValue());
    }

    @Test
    void SM_AltID_DatasetId() throws Exception {
        resetIdProcessings();
        File file = new File("src/test/resources/stops_mapping.csv");
        outputExternalIdsService.feedCacheStopWithFile(file, "TEST1");

        File fileInject = new File("src/test/resources/siri-sm-test1-alt.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject, fileInject.getPath(), "TEST1");

        assertFalse(stopVisits.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <StopMonitoringRequest version="2.0">
                            <MonitoringRef>30</MonitoringRef>
                        </StopMonitoringRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        IncomingSiriParameters parameters = createDefaultParameters(xml);
        parameters.setDatasetId("TEST1");
        parameters.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("false", "true"));

        Siri response = handler.handleIncomingSiri(parameters);
        assertNotNull(response);
        Assertions.assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        assertNotNull(response.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().getFirst().getMonitoringRef());
    }

    @Test
    void SM_AltID_No_DatasetId() throws Exception {
        File file = new File("src/test/resources/stops_mapping.csv");
        outputExternalIdsService.feedCacheStopWithFile(file, "TEST1");

        File fileInject = new File("src/test/resources/siri-sm.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", fileInject, fileInject.getPath(), "TEST1");

        assertFalse(stopVisits.getAll().isEmpty());


        String stringXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                        <StopMonitoringRequest version="2.0">
                            <MonitoringRef>30</MonitoringRef>
                        </StopMonitoringRequest>
                    </ServiceRequest>
                </Siri>
                """;

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        IncomingSiriParameters parameters = createDefaultParameters(xml);
        parameters.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("false", "true"));

        Siri response = handler.handleIncomingSiri(parameters);
        assertNotNull(response);
        Assertions.assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        Assertions.assertTrue(response.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().isEmpty());
    }


    /**
     * Vehicle monitoring
     * avec altId
     * avec datasetId
     * retour données identifiants producteurs locaux
     **/
    @Test
    void VM_AltId_DatasetId() throws Exception {
        File file = new File("src/test/resources/lines_mapping.csv");
        resetIdProcessings();
        outputExternalIdsService.feedCacheLineWithFile(file, "TEST");


        File fileInject = new File("src/test/resources/siri-vm.zip");
        siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-vm", fileInject, fileInject.getPath(), "TEST");

        assertFalse(vehicleActivities.getAll().isEmpty());


        String stringXmlLineRef12 = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                           <VehicleMonitoringRequest version="2.0">
                               <LineRef>12</LineRef>
                           </VehicleMonitoringRequest>
                    </ServiceRequest>
                </Siri>
                """;


        InputStream xmlLineRef12 = IOUtils.toInputStream(stringXmlLineRef12, StandardCharsets.UTF_8);

        IncomingSiriParameters parameters = createDefaultParameters(xmlLineRef12);
        parameters.setDatasetId("TEST");
        parameters.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("false", "true"));

        Siri responseLineRef12 = handler.handleIncomingSiri(parameters);
        assertNotNull(responseLineRef12);
        assertNotNull(responseLineRef12.getServiceDelivery());
        assertFalse(responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().isEmpty());
        assertFalse(responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().getFirst().getVehicleActivities().isEmpty());
        assertNotNull(responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().getFirst().getVehicleActivities().getFirst().getVehicleMonitoringRef());
        assertNotNull(responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().getFirst().getVehicleActivities().getFirst().getVehicleMonitoringRef());
        assertNotNull(responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().getFirst().getVehicleActivities().getFirst().getVehicleMonitoringRef().getValue());
        assertEquals("TEST:VehicleJourney::23:LOC", responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().getFirst().getVehicleActivities().getFirst().getVehicleMonitoringRef().getValue());
        assertEquals("12", responseLineRef12.getServiceDelivery().getVehicleMonitoringDeliveries().getFirst().getVehicleActivities().getFirst().getMonitoredVehicleJourney().getLineRef().getValue());

        String stringXmlLineRef34 = """
                <?xml version="1.0" encoding="utf-8"?>
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" version="2.0">
                    <ServiceRequest>
                        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>
                           <VehicleMonitoringRequest version="2.0">
                               <LineRef>34</LineRef>
                           </VehicleMonitoringRequest>
                    </ServiceRequest>
                </Siri>
                """;


        InputStream xmlLineRef34 = IOUtils.toInputStream(stringXmlLineRef34, StandardCharsets.UTF_8);

        parameters = createDefaultParameters(xmlLineRef34);
        parameters.setDatasetId("TEST");
        parameters.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("false", "true"));

        Siri responseLineRef34 = handler.handleIncomingSiri(parameters);
        assertNotNull(responseLineRef34);
        assertNotNull(responseLineRef34.getServiceDelivery());
        assertFalse(responseLineRef34.getServiceDelivery().getVehicleMonitoringDeliveries().isEmpty());
        assertFalse(responseLineRef34.getServiceDelivery().getVehicleMonitoringDeliveries().getFirst().getVehicleActivities().isEmpty());

        List<VehicleActivityStructure> vehicleActivityStructures = responseLineRef34.getServiceDelivery().getVehicleMonitoringDeliveries().getFirst().getVehicleActivities();
        Comparator<VehicleActivityStructure> vehicleActivityStructureComparator
                = Comparator.comparing(vehicleActivityStructure -> vehicleActivityStructure.getVehicleMonitoringRef().getValue());
        vehicleActivityStructures.sort(vehicleActivityStructureComparator);

        assertNotNull(vehicleActivityStructures.getFirst().getVehicleMonitoringRef());
        assertNotNull(vehicleActivityStructures.getFirst().getVehicleMonitoringRef());
        assertNotNull(vehicleActivityStructures.getFirst().getVehicleMonitoringRef().getValue());
        assertEquals("TEST:VehicleJourney::232:LOC", vehicleActivityStructures.getFirst().getVehicleMonitoringRef().getValue());
        assertEquals("34", vehicleActivityStructures.getFirst().getMonitoredVehicleJourney().getLineRef().getValue());

        assertNotNull(vehicleActivityStructures.get(1).getVehicleMonitoringRef());
        assertNotNull(vehicleActivityStructures.get(1).getVehicleMonitoringRef());
        assertNotNull(vehicleActivityStructures.get(1).getVehicleMonitoringRef().getValue());
        assertEquals("TEST:VehicleJourney::233:LOC", vehicleActivityStructures.get(1).getVehicleMonitoringRef().getValue());
        assertEquals("34", vehicleActivityStructures.get(1).getMonitoredVehicleJourney().getLineRef().getValue());
    }

}
