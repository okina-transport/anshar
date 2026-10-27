package no.rutebanken.anshar.routes.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.collection.ISet;
import com.sun.net.httpserver.HttpServer;
import no.rutebanken.anshar.api.FlowStatus;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LivenessReadinessRouteTest {

    private static final String CHECK_STATUS_OK = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Siri xmlns="http://www.siri.org.uk/siri" version="2.0">
              <CheckStatusResponse>
                <ResponseTimestamp>2026-01-01T00:00:00Z</ResponseTimestamp>
                <Status>true</Status>
                <ServiceStartedTime>2026-01-01T00:00:00Z</ServiceStartedTime>
              </CheckStatusResponse>
            </Siri>""";

    private static final String RETRIEVER_RESPONSE = """
            [
              {"id": "27", "status": "OK", "lastUpdate": 1000, "dataset": "TEST", "url": "http://feed/ok", "type": "GTFS"},
              {"id": "29", "status": "EMPTY_FEED", "lastUpdate": 2000, "dataset": "TEST", "url": "http://feed/empty", "type": "GTFS"}
            ]""";

    private final List<HttpServer> servers = new ArrayList<>();
    private SubscriptionConfig subscriptionConfig;

    @BeforeEach
    void setUp() {
        subscriptionConfig = mock(SubscriptionConfig.class);
        when(subscriptionConfig.getSubscriptions()).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void siriDataTypeShortNames() {
        assertEquals("SX", SiriDataType.SITUATION_EXCHANGE.getShortName());
        assertEquals("VM", SiriDataType.VEHICLE_MONITORING.getShortName());
        assertEquals("ET", SiriDataType.ESTIMATED_TIMETABLE.getShortName());
        assertEquals("SM", SiriDataType.STOP_MONITORING.getShortName());
        assertEquals("GM", SiriDataType.GENERAL_MESSAGE.getShortName());
        assertEquals("FM", SiriDataType.FACILITY_MONITORING.getShortName());
    }

    @Test
    void incomingFlowTypeIsSerializedWithItsCode() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        assertEquals("\"GTFS-RT\"", objectMapper.writeValueAsString(IncomingFlowType.GTFS));
        assertEquals("\"SIRI\"", objectMapper.writeValueAsString(IncomingFlowType.SIRI));
    }

    @Test
    void siriTypeContainsTheService() throws IOException {
        String url = startServer(200, CHECK_STATUS_OK);
        when(subscriptionConfig.getSubscriptions()).thenReturn(List.of(
                subscription("sm-1", SiriDataType.STOP_MONITORING, "DATASET", url)));

        List<IncomingFlowStatus> statuses = createRoute("").getIncomingDataHealth();

        assertEquals(1, statuses.size());
        assertEquals("sm-1", statuses.getFirst().getId());
        assertEquals("SIRI-SM", statuses.getFirst().getType());
        assertEquals(FlowStatus.OK.name(), statuses.getFirst().getStatus());
    }

    @Test
    void siriSubscriptionsSharingAnUrlListAllServices() throws IOException {
        String url = startServer(200, CHECK_STATUS_OK);
        when(subscriptionConfig.getSubscriptions()).thenReturn(List.of(
                subscription("vm-1", SiriDataType.VEHICLE_MONITORING, "DATASET", url),
                subscription("et-1", SiriDataType.ESTIMATED_TIMETABLE, "DATASET", url),
                subscription("et-2", SiriDataType.ESTIMATED_TIMETABLE, "DATASET", url)));

        List<IncomingFlowStatus> statuses = createRoute("").getIncomingDataHealth();

        assertEquals(1, statuses.size());
        assertEquals("vm-1,et-1,et-2", statuses.getFirst().getId());
        assertEquals("SIRI-ET,SIRI-VM", statuses.getFirst().getType());
    }

    @Test
    void noGtfsRtStatusWhenRetrieverUrlIsNotConfigured() throws IOException {
        String url = startServer(200, CHECK_STATUS_OK);
        when(subscriptionConfig.getSubscriptions()).thenReturn(List.of(
                subscription("sx-1", SiriDataType.SITUATION_EXCHANGE, "DATASET", url)));

        List<IncomingFlowStatus> statuses = createRoute("").getIncomingDataHealth();

        assertEquals(1, statuses.size());
        assertEquals("SIRI-SX", statuses.getFirst().getType());
    }

    @Test
    void gtfsRtStatusesComeFromRetrieverBeforeSiri() throws IOException {
        String retrieverUrl = startServer(200, RETRIEVER_RESPONSE);
        String siriUrl = startServer(200, CHECK_STATUS_OK);
        when(subscriptionConfig.getSubscriptions()).thenReturn(List.of(
                subscription("gm-1", SiriDataType.GENERAL_MESSAGE, "DATASET", siriUrl)));

        List<IncomingFlowStatus> statuses = createRoute(retrieverUrl + "/").getIncomingDataHealth();

        assertEquals(3, statuses.size());
        IncomingFlowStatus ok = statuses.get(0);
        assertEquals("27", ok.getId());
        assertEquals("OK", ok.getStatus());
        assertEquals(1000, ok.getLastUpdate());
        assertEquals("TEST", ok.getDataset());
        assertEquals("http://feed/ok", ok.getUrl());
        assertEquals("GTFS-RT", ok.getType());
        assertEquals("EMPTY_FEED", statuses.get(1).getStatus());
        assertEquals("GTFS-RT", statuses.get(1).getType());
        assertEquals("SIRI-GM", statuses.get(2).getType());
    }

    @Test
    void gtfsRtRetrieverErrorIsReported() throws IOException {
        String retrieverUrl = startServer(500, "boom");

        assertRetrieverInError(createRoute(retrieverUrl).getIncomingDataHealth(), retrieverUrl);
    }

    @Test
    void gtfsRtRetrieverInvalidResponseIsReported() throws IOException {
        String retrieverUrl = startServer(200, "not json");

        assertRetrieverInError(createRoute(retrieverUrl).getIncomingDataHealth(), retrieverUrl);
    }

    @Test
    void gtfsRtRetrieverUnreachableIsReported() throws IOException {
        String retrieverUrl = startServer(200, RETRIEVER_RESPONSE);
        servers.getLast().stop(0);

        assertRetrieverInError(createRoute(retrieverUrl).getIncomingDataHealth(), retrieverUrl);
    }

    private void assertRetrieverInError(List<IncomingFlowStatus> statuses, String retrieverUrl) {
        assertEquals(1, statuses.size());
        IncomingFlowStatus status = statuses.getFirst();
        assertEquals("gtfs-rt-retriever", status.getId());
        assertEquals(FlowStatus.ERROR.name(), status.getStatus());
        assertEquals("GTFS-RT", status.getType());
        assertEquals(retrieverUrl + "/incomingdatahealth", status.getUrl());
        assertTrue(status.getLastUpdate() > 0);
    }

    @SuppressWarnings("unchecked")
    private LivenessReadinessRoute createRoute(String gtfsRtRetrieverUrl) {
        return new LivenessReadinessRoute("source", "fail", "fail", "success", "success", "template",
                LocalTime.MIN, LocalTime.MAX, "", false, gtfsRtRetrieverUrl,
                mock(ISet.class), mock(HealthManager.class), mock(SubscriptionManager.class),
                mock(PrometheusMetricsService.class), subscriptionConfig, mock(IncomingDataHealthService.class));
    }

    private SubscriptionSetup subscription(String subscriptionId, SiriDataType type, String datasetId, String url) {
        SubscriptionSetup subscription = new SubscriptionSetup();
        subscription.setSubscriptionId(subscriptionId);
        subscription.setSubscriptionType(type);
        subscription.setDatasetId(datasetId);
        subscription.setSubscriptionMode(SubscriptionSetup.SubscriptionMode.SUBSCRIBE);
        subscription.setServiceType(SubscriptionSetup.ServiceType.REST);
        subscription.setContentType("text/xml;charset=UTF-8");
        subscription.setVersion("2.0");
        subscription.setRequestorRef("test");
        subscription.setActive(true);
        subscription.setUrlMap(Map.of(RequestType.SUBSCRIBE, url));
        return subscription;
    }

    /**
     * Starts a local HTTP server answering every request with the given status and body.
     *
     * @return the base url of the server
     */
    private String startServer(int statusCode, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });
        server.start();
        servers.add(server);
        return "http://localhost:" + server.getAddress().getPort();
    }
}
