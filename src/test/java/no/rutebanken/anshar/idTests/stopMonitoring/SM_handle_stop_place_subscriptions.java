package no.rutebanken.anshar.idTests.stopMonitoring;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.IncomingSiriParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.MonitoredStopVisits;
import no.rutebanken.anshar.idTests.TestUtils;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.handlers.inbound.StopMonitoringInbound;
import no.rutebanken.anshar.routes.siri.handlers.outbound.StopMonitoringOutbound;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.MonitoredStopVisit;
import uk.org.siri.siri21.MonitoringRefStructure;
import uk.org.siri.siri21.Siri;

import javax.xml.bind.UnmarshalException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.*;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class SM_handle_stop_place_subscriptions extends SpringBootBaseTest {

    @Autowired
    private MonitoredStopVisits monitoredStopVisits;

    @Autowired
    private StopMonitoringOutbound stopMonitoringOutbound;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private SiriHandler handler;

    @Autowired
    private StopMonitoringInbound stopMonitoringInbound;


    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    private ClientAndServer mockServer;


    @BeforeEach
    public void init() {
        monitoredStopVisits.clearAll();
        serverSubscriptionManager.clearAllOutboundSubscriptions();
        mockServer = startClientAndServer(1080);
        System.out.println("MockServer démarré sur le port 1080");
    }

    @AfterEach
    public void stopServer() {
        if (mockServer != null) {
            mockServer.stop();
            System.out.println("MockServer arrêté");
        }
    }

    private static String DATASET = "DAT1";

    private static String QUAY1_REF = DATASET + ":Quay:HBLI1";
    private static String QUAY2_REF = DATASET + ":Quay:HBLI2";

    private static String STOP_PLACE_REF = DATASET + ":StopPlace:HBLI";


    @Test
    public void test_quay_subscription_filter_map_is_correct_MOBIITI_id() throws UnmarshalException {
        initCacheWith2quaysAnd1Stop();
        IncomingSiriParameters incomingSiriParameters = createIncomingSiriParametersForSubscription("MOBIITI:Quay:1", OutboundIdMappingPolicy.DEFAULT);


        Siri response = handler.handleIncomingSiri(incomingSiriParameters);
        Assertions.assertEquals(1, serverSubscriptionManager.getSubscriptions().size());
        OutboundSubscriptionSetup firstSub = (OutboundSubscriptionSetup) serverSubscriptionManager.getSubscriptions().stream().findFirst().get();
        Assertions.assertFalse(firstSub.getFilterMap().isEmpty());
        Set<String> stopRefFilter = firstSub.getFilterMap().get(MonitoringRefStructure.class);
        Assertions.assertEquals(1, stopRefFilter.size());
        Assertions.assertTrue(stopRefFilter.contains("HBLI1"));

    }

    private IncomingSiriParameters createIncomingSiriParametersForSubscription(String stopRef, OutboundIdMappingPolicy outboundPolicy) {

        String stringXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" version=\"2.0\">\n" +
                "    <SubscriptionRequest>\n" +
                "        <RequestorRef>TU-REQ1</RequestorRef>\n" +
                "        <ConsumerAddress>http://localhost:1080/incomingSiri</ConsumerAddress>\n" +
                "        <SubscriptionContext>\n" +
                "            <siri:HeartbeatInterval xmlns:siri=\"http://www.siri.org.uk/siri\">PT5M</siri:HeartbeatInterval>\n" +
                "        </SubscriptionContext>\n" +
                "        <StopMonitoringSubscriptionRequest>\n" +
                "            <SubscriptionIdentifier>TU-SUB1</SubscriptionIdentifier>\n" +
                "            <InitialTerminationTime>2099-10-18T08:02:57.701131Z</InitialTerminationTime>\n" +
                "            <StopMonitoringRequest>\n" +
                "                <siri:RequestTimestamp xmlns:siri=\"http://www.siri.org.uk/siri\">2022-10-17T08:02:57.699257Z</siri:RequestTimestamp>\n" +
                "                <siri:PreviewInterval xmlns:siri=\"http://www.siri.org.uk/siri\">PT3H</siri:PreviewInterval>\n" +
                "                <siri:MonitoringRef xmlns:siri=\"http://www.siri.org.uk/siri\">" + stopRef + "</siri:MonitoringRef>\n" +
                "            </StopMonitoringRequest>\n" +
                "        </StopMonitoringSubscriptionRequest>\n" +
                "\n" +
                "    </SubscriptionRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);


        IncomingSiriParameters incomingSiriParameters = new IncomingSiriParameters();
        incomingSiriParameters.setIncomingSiriStream(xml);
        incomingSiriParameters.setOutboundIdMappingPolicy(outboundPolicy);
        incomingSiriParameters.setMaxSize(-1);
        incomingSiriParameters.setClientTrackingName("clientTrackingName");
        incomingSiriParameters.setSoapTransformation(false);
        incomingSiriParameters.setUseOriginalId(false);
        return incomingSiriParameters;
    }

    @Test
    public void test_quay_subscription_filter_map_is_correct_ORIGINAL_id() throws UnmarshalException {
        initCacheWith2quaysAnd1Stop();
        IncomingSiriParameters incomingSiriParameters = createIncomingSiriParametersForSubscription("DAT1:Quay:HBLI1", OutboundIdMappingPolicy.ORIGINAL_ID);
        incomingSiriParameters.setDatasetId(DATASET);
        Siri response = handler.handleIncomingSiri(incomingSiriParameters);
        Assertions.assertEquals(1, serverSubscriptionManager.getSubscriptions().size());
        OutboundSubscriptionSetup firstSub = (OutboundSubscriptionSetup) serverSubscriptionManager.getSubscriptions().stream().findFirst().get();
        Assertions.assertFalse(firstSub.getFilterMap().isEmpty());
        Set<String> stopRefFilter = firstSub.getFilterMap().get(MonitoringRefStructure.class);
        Assertions.assertEquals(1, stopRefFilter.size());
        Assertions.assertTrue(stopRefFilter.contains("HBLI1"));

    }

    @Test
    public void test_stopPlace_subscription_filter_map_is_correct_MOBIITI_id() throws UnmarshalException {
        initCacheWith2quaysAnd1Stop();
        IncomingSiriParameters incomingSiriParameters = createIncomingSiriParametersForSubscription("MOBIITI:StopPlace:3", OutboundIdMappingPolicy.DEFAULT);
        Siri response = handler.handleIncomingSiri(incomingSiriParameters);
        Assertions.assertEquals(1, serverSubscriptionManager.getSubscriptions().size());
        OutboundSubscriptionSetup firstSub = (OutboundSubscriptionSetup) serverSubscriptionManager.getSubscriptions().stream().findFirst().get();
        Assertions.assertFalse(firstSub.getFilterMap().isEmpty());
        Set<String> stopRefFilter = firstSub.getFilterMap().get(MonitoringRefStructure.class);
        Assertions.assertEquals(1, stopRefFilter.size());
        Assertions.assertTrue(stopRefFilter.contains("HBLI"));
    }

    @Test
    public void test_stopPlace_subscription_filter_map_is_correct_ORIGINAL_id() throws UnmarshalException {
        initCacheWith2quaysAnd1Stop();
        IncomingSiriParameters incomingSiriParameters = createIncomingSiriParametersForSubscription("DAT1:StopPlace:HBLI", OutboundIdMappingPolicy.ORIGINAL_ID);
        incomingSiriParameters.setDatasetId(DATASET);
        Siri response = handler.handleIncomingSiri(incomingSiriParameters);
        Assertions.assertEquals(1, serverSubscriptionManager.getSubscriptions().size());
        OutboundSubscriptionSetup firstSub = (OutboundSubscriptionSetup) serverSubscriptionManager.getSubscriptions().stream().findFirst().get();
        Assertions.assertFalse(firstSub.getFilterMap().isEmpty());
        Set<String> stopRefFilter = firstSub.getFilterMap().get(MonitoringRefStructure.class);
        Assertions.assertEquals(1, stopRefFilter.size());
        Assertions.assertTrue(stopRefFilter.contains("HBLI"));
    }


    @Test
    public void test_stopPlace_notification_MOBIITI_id() throws UnmarshalException, InterruptedException {

        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/incomingSiri")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withBody("{\"message\":\"success\"}")
        );

        initCacheWith2quaysAnd1Stop();
        IncomingSiriParameters incomingSiriParameters = createIncomingSiriParametersForSubscription("MOBIITI:StopPlace:3", OutboundIdMappingPolicy.DEFAULT);
        Siri response = handler.handleIncomingSiri(incomingSiriParameters);


        MonitoredStopVisit elementSp = TestUtils.createMonitoredStopVisit(ZonedDateTime.now().plusMinutes(1), "HBLI");

        List<MonitoredStopVisit> monitoredVisitToIngest = new ArrayList<>();
        monitoredVisitToIngest.add(elementSp);

        stopMonitoringInbound.ingestStopVisits(DATASET, monitoredVisitToIngest);

        //Attente nécessaire car le post est traité par un thread
        Thread.sleep(5000);

        // Récupérer et tracer les requêtes reçues
        TestUtils.printReceivedRequests(mockServer);
        TestUtils.verifyStringInResponse(mockServer, "<MonitoringRef>MOBIITI:StopPlace:3</MonitoringRef>");

    }

    @Test
    public void test_stopPlace_notification_ORIGINAL_id() throws UnmarshalException, InterruptedException {

        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/incomingSiri")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withBody("{\"message\":\"success\"}")
        );

        initCacheWith2quaysAnd1Stop();
        IncomingSiriParameters incomingSiriParameters = createIncomingSiriParametersForSubscription("DAT1:StopPlace:HBLI", OutboundIdMappingPolicy.ORIGINAL_ID);
        incomingSiriParameters.setDatasetId(DATASET);
        Siri response = handler.handleIncomingSiri(incomingSiriParameters);


        MonitoredStopVisit elementSp = TestUtils.createMonitoredStopVisit(ZonedDateTime.now().plusMinutes(1), "HBLI");

        List<MonitoredStopVisit> monitoredVisitToIngest = new ArrayList<>();
        monitoredVisitToIngest.add(elementSp);

        stopMonitoringInbound.ingestStopVisits(DATASET, monitoredVisitToIngest);

        //Attente nécessaire car le post est traité par un thread
        Thread.sleep(5000);

        // Récupérer et tracer les requêtes reçues
        TestUtils.printReceivedRequests(mockServer);
        TestUtils.verifyStringInResponse(mockServer, "<MonitoringRef>DAT1:StopPlace:HBLI</MonitoringRef>");

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
