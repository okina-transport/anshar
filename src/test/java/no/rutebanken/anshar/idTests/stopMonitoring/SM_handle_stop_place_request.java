package no.rutebanken.anshar.idTests.stopMonitoring;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.IncomingSiriParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.DiscoveryCache;
import no.rutebanken.anshar.data.MonitoredStopVisits;
import no.rutebanken.anshar.idTests.TestUtils;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.handlers.outbound.StopMonitoringOutbound;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.*;

import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class SM_handle_stop_place_request extends SpringBootBaseTest {

    @Autowired
    private MonitoredStopVisits monitoredStopVisits;

    @Autowired
    private StopMonitoringOutbound stopMonitoringOutbound;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private DiscoveryCache discoveryCache;

    @Autowired
    private SiriHandler handler;

    @BeforeEach
    public void init() {
        monitoredStopVisits.clearAll();
    }

    private static String DATASET = "DAT1";

    private static String QUAY1_REF = DATASET + ":Quay:HBLI1";
    private static String QUAY2_REF = DATASET + ":Quay:HBLI2";

    private static String STOP_PLACE_REF = DATASET + ":StopPlace:HBLI";


    /**
     * Request on quay 1 with MOBIITI id, and dataset specified.
     * Should return only one result with MOBIITI:Quay:1 monitoringRef
     */
    @Test
    public void test_request_on_quay1_v1() {
        initCacheWith2quaysAnd1Stop();

        Assertions.assertEquals(3, monitoredStopVisits.getAll().size());

        ServiceRequest serviceRequest1 = createStopMonitoringRequestForRef("MOBIITI:Quay:1");


        // Request  : DATASET : DAT1, "MOBIITI:Quay:1", OutboundIdMappingPolicy.DEFAULT
        Siri res = stopMonitoringOutbound.getStopMonitoringServiceDelivery(serviceRequest1, OutboundIdMappingPolicy.DEFAULT, DATASET, "req", "clientTrackingName", 1500000);
        List<MonitoredStopVisit> extractedMonitoredStopVisit = extractMonitoredStopVisits(res);
        Assertions.assertEquals(1, extractedMonitoredStopVisit.size());
        Assertions.assertEquals("MOBIITI:Quay:1", extractedMonitoredStopVisit.get(0).getMonitoringRef().getValue());

    }


    /**
     * Request on quay 1 with MOBIITI id, and dataset not specified.
     * Should return only one result with MOBIITI:Quay:1 monitoringRef
     */
    @Test
    public void test_request_on_quay1_v2() {
        initCacheWith2quaysAnd1Stop();

        ServiceRequest serviceRequest1 = createStopMonitoringRequestForRef("MOBIITI:Quay:1");

        // Request  : DATASET : null, "MOBIITI:Quay:1", OutboundIdMappingPolicy.DEFAULT
        Siri res = stopMonitoringOutbound.getStopMonitoringServiceDelivery(serviceRequest1, OutboundIdMappingPolicy.DEFAULT, null, "req", "clientTrackingName", 1500000);
        List<MonitoredStopVisit> extractedMonitoredStopVisit = extractMonitoredStopVisits(res);
        Assertions.assertEquals(1, extractedMonitoredStopVisit.size());
        Assertions.assertEquals("MOBIITI:Quay:1", extractedMonitoredStopVisit.get(0).getMonitoringRef().getValue());
    }


    /**
     * Request on stop place with MOBIITI id, and dataset specified.
     * Should return only one result with MOBIITI:StopPlace:3 monitoringRef
     */
    @Test
    public void test_request_on_StopPlace_v1() {
        initCacheWith2quaysAnd1Stop();

        ServiceRequest serviceRequest1 = createStopMonitoringRequestForRef("MOBIITI:StopPlace:3");

        // Request  : DATASET : DAT1, "MOBIITI:StopPlace:3", OutboundIdMappingPolicy.DEFAULT
        Siri res = stopMonitoringOutbound.getStopMonitoringServiceDelivery(serviceRequest1, OutboundIdMappingPolicy.DEFAULT, DATASET, "req", "clientTrackingName", 1500000);
        List<MonitoredStopVisit> extractedMonitoredStopVisit = extractMonitoredStopVisits(res);
        Assertions.assertEquals(1, extractedMonitoredStopVisit.size());
        Assertions.assertEquals("MOBIITI:StopPlace:3", extractedMonitoredStopVisit.get(0).getMonitoringRef().getValue());
    }


    /**
     * Request on stop place with MOBIITI id, and dataset not specified.
     * Should return only one result with MOBIITI:StopPlace:3 monitoringRef
     */
    @Test
    public void test_request_on_StopPlace_v2() {
        initCacheWith2quaysAnd1Stop();

        ServiceRequest serviceRequest1 = new ServiceRequest();
        RequestorRef reqRef = new RequestorRef();
        reqRef.setValue("req");
        serviceRequest1.setRequestorRef(reqRef);
        StopMonitoringRequestStructure stopMonReq = new StopMonitoringRequestStructure();
        stopMonReq.setVersion("2.1");
        MonitoringRefStructure monitoringRefStructure = new MonitoringRefStructure();
        monitoringRefStructure.setValue("MOBIITI:StopPlace:3");
        stopMonReq.setMonitoringRef(monitoringRefStructure);
        serviceRequest1.getStopMonitoringRequests().add(stopMonReq);


        // Request  : DATASET : null, "MOBIITI:StopPlace:3", OutboundIdMappingPolicy.DEFAULT
        Siri res = stopMonitoringOutbound.getStopMonitoringServiceDelivery(serviceRequest1, OutboundIdMappingPolicy.DEFAULT, null, "req", "clientTrackingName", 1500000);
        List<MonitoredStopVisit> extractedMonitoredStopVisit = extractMonitoredStopVisits(res);
        Assertions.assertEquals(1, extractedMonitoredStopVisit.size());
        Assertions.assertEquals("MOBIITI:StopPlace:3", extractedMonitoredStopVisit.get(0).getMonitoringRef().getValue());
    }


    /**
     * Request on stop place with provider id, and dataset specified.
     * Should return only one result with DAT1:StopPlace:HBLI monitoringRef
     */
    @Test
    public void test_request_on_StopPlace_v3() {
        initCacheWith2quaysAnd1Stop();

        ServiceRequest serviceRequest1 = new ServiceRequest();
        RequestorRef reqRef = new RequestorRef();
        reqRef.setValue("req");
        serviceRequest1.setRequestorRef(reqRef);
        StopMonitoringRequestStructure stopMonReq = new StopMonitoringRequestStructure();
        stopMonReq.setVersion("2.1");
        MonitoringRefStructure monitoringRefStructure = new MonitoringRefStructure();
        monitoringRefStructure.setValue("DAT1:StopPlace:HBLI");
        stopMonReq.setMonitoringRef(monitoringRefStructure);
        serviceRequest1.getStopMonitoringRequests().add(stopMonReq);

        // Request  : DATASET : DAT1, "DAT1:StopPlace:HBLI", OutboundIdMappingPolicy.DEFAULT
        Siri res = stopMonitoringOutbound.getStopMonitoringServiceDelivery(serviceRequest1, OutboundIdMappingPolicy.ORIGINAL_ID, DATASET, "req", "clientTrackingName", 1500000);
        List<MonitoredStopVisit> extractedMonitoredStopVisit = extractMonitoredStopVisits(res);
        Assertions.assertEquals(1, extractedMonitoredStopVisit.size());
        Assertions.assertEquals("DAT1:StopPlace:HBLI", extractedMonitoredStopVisit.get(0).getMonitoringRef().getValue());
    }


    @Test
    public void stopPointsDiscoveryTest_ORIGINAL() throws JAXBException, IOException {
        discoveryCache.clearDiscoveryStops();

        initStopPlaceMapper();
        initIdProcessingParameters();

        discoveryCache.addStop(DATASET, "HBLI");
        discoveryCache.addStop(DATASET, "HBLI1");
        discoveryCache.addStop(DATASET, "HBLI2");

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File("src/test/resources/discoveryTest/stop_points_discovery_test.xml");

        try {
            IncomingSiriParameters params = new IncomingSiriParameters();
            params.setIncomingSiriStream(new ByteArrayInputStream(FileUtils.readFileToByteArray(file)));
            params.setDatasetId(DATASET);
            params.setOutboundIdMappingPolicy(OutboundIdMappingPolicy.ORIGINAL_ID);
            params.setMaxSize(-1);
            params.setSoapTransformation(false);

            Siri result = handler.handleIncomingSiri(params);
            assertNotNull(result.getStopPointsDelivery());
            assertNotNull(result.getStopPointsDelivery().getAnnotatedStopPointReves());
            assertEquals(3, result.getStopPointsDelivery().getAnnotatedStopPointReves().size());
            List<String> expectedPointRef = Arrays.asList("DAT1:Quay:HBLI1", "DAT1:Quay:HBLI2", "DAT1:StopPlace:HBLI");
            for (AnnotatedStopPointStructure annotatedStopPointReve : result.getStopPointsDelivery().getAnnotatedStopPointReves()) {
                assertTrue(expectedPointRef.contains(annotatedStopPointReve.getStopPointRef().getValue()), "Point not in expectedStops:" + annotatedStopPointReve.getStopPointRef().getValue());
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    public void stopPointsDiscoveryTest_MOBIITI() throws JAXBException, IOException {
        discoveryCache.clearDiscoveryStops();

        initStopPlaceMapper();
        initIdProcessingParameters();

        discoveryCache.addStop(DATASET, "HBLI");
        discoveryCache.addStop(DATASET, "HBLI1");
        discoveryCache.addStop(DATASET, "HBLI2");

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File("src/test/resources/discoveryTest/stop_points_discovery_test.xml");

        try {
            IncomingSiriParameters params = new IncomingSiriParameters();
            params.setIncomingSiriStream(new ByteArrayInputStream(FileUtils.readFileToByteArray(file)));
            params.setOutboundIdMappingPolicy(OutboundIdMappingPolicy.DEFAULT);
            params.setMaxSize(-1);
            params.setSoapTransformation(false);

            Siri result = handler.handleIncomingSiri(params);
            assertNotNull(result.getStopPointsDelivery());
            assertNotNull(result.getStopPointsDelivery().getAnnotatedStopPointReves());
            assertEquals(3, result.getStopPointsDelivery().getAnnotatedStopPointReves().size());
            List<String> expectedPointRef = Arrays.asList("MOBIITI:Quay:1", "MOBIITI:Quay:2", "MOBIITI:StopPlace:3");
            for (AnnotatedStopPointStructure annotatedStopPointReve : result.getStopPointsDelivery().getAnnotatedStopPointReves()) {
                assertTrue(expectedPointRef.contains(annotatedStopPointReve.getStopPointRef().getValue()), "Point not in expectedStops:" + annotatedStopPointReve.getStopPointRef().getValue());
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }


    private ServiceRequest createStopMonitoringRequestForRef(String ref) {
        ServiceRequest serviceRequest1 = new ServiceRequest();
        RequestorRef reqRef = new RequestorRef();
        reqRef.setValue("req");
        serviceRequest1.setRequestorRef(reqRef);
        StopMonitoringRequestStructure stopMonReq = new StopMonitoringRequestStructure();
        stopMonReq.setVersion("2.1");
        MonitoringRefStructure monitoringRefStructure = new MonitoringRefStructure();
        monitoringRefStructure.setValue(ref);
        stopMonReq.setMonitoringRef(monitoringRefStructure);
        serviceRequest1.getStopMonitoringRequests().add(stopMonReq);
        return serviceRequest1;
    }

    private void initCacheWith2quaysAnd1Stop() {
        initStopPlaceMapper();
        initIdProcessingParameters();

        MonitoredStopVisit elementq1 = TestUtils.createMonitoredStopVisit(ZonedDateTime.now().plusMinutes(1), "HBLI1");
        MonitoredStopVisit elementq2 = TestUtils.createMonitoredStopVisit(ZonedDateTime.now().plusMinutes(1), "HBLI2");
        MonitoredStopVisit elementSp = TestUtils.createMonitoredStopVisit(ZonedDateTime.now().plusMinutes(1), "HBLI");

        monitoredStopVisits.add(DATASET, elementq1);
        monitoredStopVisits.add(DATASET, elementq2);
        monitoredStopVisits.add(DATASET, elementSp);

        Assertions.assertEquals(3, monitoredStopVisits.getAll().size());
    }

    private List<MonitoredStopVisit> extractMonitoredStopVisits(Siri res) {
        if (res == null || res.getServiceDelivery() == null || res.getServiceDelivery().getStopMonitoringDeliveries() == null || res.getServiceDelivery().getStopMonitoringDeliveries().size() == 0) {
            return Collections.emptyList();
        }

        List<MonitoredStopVisit> monitoredStopVisits = new ArrayList<>();

        for (StopMonitoringDeliveryStructure stopMonitoringDelivery : res.getServiceDelivery().getStopMonitoringDeliveries()) {
            monitoredStopVisits.addAll(stopMonitoringDelivery.getMonitoredStopVisits());
        }
        return monitoredStopVisits;
    }

    private void initIdProcessingParameters() {
        IdProcessingParameters quayDat1 = new IdProcessingParameters();
        quayDat1.setOutputPrefixToAdd(DATASET + ":Quay:");
        quayDat1.setDatasetId(DATASET);
        quayDat1.setObjectType(ObjectType.STOP);
        subscriptionConfig.getIdProcessingParameters().add(quayDat1);

    }

    public void initStopPlaceMapper() {
        Map<String, Pair<String, String>> stopPlaceMap;

        stopPlaceMap = new HashMap<>();
        stopPlaceMap.put(QUAY1_REF, Pair.of("MOBIITI:Quay:1", "quay 1 name"));
        stopPlaceMap.put(QUAY2_REF, Pair.of("MOBIITI:Quay:2", "quay 2 name"));
        stopPlaceMap.put(STOP_PLACE_REF, Pair.of("MOBIITI:StopPlace:3", "SP name"));

        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);

        //Manually adding custom mapping to Spring context
        stopPlaceService.addStopPlaceMappings(stopPlaceMap);
        Map<String, List<String>> stopPlaceReverseMap = new HashMap<>();

        for (Map.Entry<String, Pair<String, String>> mappingEntry : stopPlaceMap.entrySet()) {
            List<String> providerIds = new ArrayList<>();
            providerIds.add(mappingEntry.getKey());
            stopPlaceReverseMap.put(mappingEntry.getValue().getLeft(), providerIds);
        }
        stopPlaceService.addStopPlaceReverseMappings(stopPlaceReverseMap);

        Set<String> stopRefs = new HashSet<>();
        stopRefs.add(QUAY1_REF);
        stopRefs.add(QUAY2_REF);
        stopRefs.add(STOP_PLACE_REF);
        stopPlaceService.addStopQuays(stopRefs);

    }
}
