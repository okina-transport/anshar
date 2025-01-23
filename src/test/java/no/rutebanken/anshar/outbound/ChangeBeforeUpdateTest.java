package no.rutebanken.anshar.outbound;

import no.rutebanken.anshar.data.MonitoredStopVisits;
import no.rutebanken.anshar.idTests.TestUtils;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.inbound.StopMonitoringInbound;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.entur.siri.validator.SiriValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.*;

import jakarta.xml.bind.UnmarshalException;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.with;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

class ChangeBeforeUpdateTest extends SpringBootBaseTest {

    private static final Logger logger = LoggerFactory.getLogger(ChangeBeforeUpdateTest.class);

    private static final String STOP_REF = "stop1";

    @Autowired
    private MonitoredStopVisits monitoredStopVisits;

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    @Autowired
    private StopMonitoringInbound stopMonitoringInbound;

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


    /**
     * Default case with changeBefore update = 0
     * 2 stopVisits are incoming, we should receive 2 notifications in outbound subscription because there is no filter
     *
     */
    @Test
    void test_changeBeforeUpdate_0() {

        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/incomingSiri")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withBody("{\"message\":\"success\"}")
        );

        OutboundSubscriptionSetup outboundSubscription = createOutboundSMSubscription(false, STOP_REF, 0);
        serverSubscriptionManager.addSubscription(outboundSubscription);
        List<MonitoredStopVisit> stopVisitsToIngest = new ArrayList<>();

        // Creating a MonitoringVisit with a small delay (5s) between aimedDeparture and expectedDeparture
        ZonedDateTime aimed = ZonedDateTime.now().plusHours(1);
        String itemId = "stop1-L1-" + aimed;
        MonitoredStopVisit smallDelayVisit = createStopVisit(STOP_REF, itemId, aimed, 5);
        stopVisitsToIngest.add(smallDelayVisit);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);
        with()
                .pollDelay(5, SECONDS)
                .await()
                .atMost(6, TimeUnit.SECONDS).until(
                        () -> {
                            stopVisitsToIngest.clear();
                            return true;
                        }
                );

        stopVisitsToIngest.clear();
        MonitoredStopVisit smallDelayVisit2 = createStopVisit(STOP_REF, itemId, aimed, 8);
        stopVisitsToIngest.add(smallDelayVisit2);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);

        with()
            //Attente nécessaire car le post est traité par un thread
            .pollDelay(2, SECONDS)
            .await()
            .atMost(6, TimeUnit.SECONDS).until(
                    () -> {
                        // Récupérer et tracer les requêtes reçues
                        int nbOfReceivedRequests = TestUtils.printReceivedRequests(mockServer);

                        // we are expecting 2 because there is no filter (changeBeforeUpdate = 0)
                        return nbOfReceivedRequests == 2;
                    }
            );
    }

    /**
     * test with changeBeforeUpdate = 10 and second stopVisit delay = 8
     * Expected result : the second notification SHOULD NOT be sent because delay < chaungeBeforeUpdate
     *
     */
    @Test
    void test_changeBeforeUpdate_10() {

        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/incomingSiri")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withBody("{\"message\":\"success\"}")
        );

        OutboundSubscriptionSetup outboundSubscription = createOutboundSMSubscription(false, STOP_REF, 10);
        serverSubscriptionManager.addSubscription(outboundSubscription);
        List<MonitoredStopVisit> stopVisitsToIngest = new ArrayList<>();


        ZonedDateTime aimed = ZonedDateTime.now().plusHours(1);
        String itemId = "stop1-L1-" + aimed;
        MonitoredStopVisit smallDelayVisit = createStopVisit(STOP_REF, itemId, aimed, 5);
        stopVisitsToIngest.add(smallDelayVisit);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);

        with()
                .pollDelay(5, SECONDS)
                .await()
                .atMost(6, TimeUnit.SECONDS).until(
                        () -> {
                            stopVisitsToIngest.clear();
                            return true;
                        }
                );

        MonitoredStopVisit smallDelayVisit2 = createStopVisit(STOP_REF, itemId, aimed, 8);
        stopVisitsToIngest.add(smallDelayVisit2);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);

        with()
            //Attente nécessaire car le post est traité par un thread
            .pollDelay(2, SECONDS)
            .await()
            .atMost(6, TimeUnit.SECONDS).until(
                    () -> {
                        // Récupérer et tracer les requêtes reçues
                        int nbOfReceivedRequests = TestUtils.printReceivedRequests(mockServer);
                        logger.info("nb requests {}", nbOfReceivedRequests);
                        // we are expecting 1 because the second stopVisit has a delay = 8 and changeBeforeUpdate = 10
                        return nbOfReceivedRequests == 1;
                    }
            );
    }

    /**
     * test with changeBeforeUpdate = 10 and second stopVisit delay = 8
     * Expected result : 2 notifications because filter is not applied when status is cancelled
     *
     */
    @Test
    void test_changeBeforeUpdate_10_cancelled_status() {

        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/incomingSiri")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withBody("{\"message\":\"success\"}")
        );

        OutboundSubscriptionSetup outboundSubscription = createOutboundSMSubscription(false, STOP_REF, 10);
        serverSubscriptionManager.addSubscription(outboundSubscription);
        List<MonitoredStopVisit> stopVisitsToIngest = new ArrayList<>();


        ZonedDateTime aimed = ZonedDateTime.now().plusHours(1);
        String itemId = "stop1-L1-" + aimed;
        MonitoredStopVisit smallDelayVisit = createStopVisit(STOP_REF, itemId, aimed, 5);
        stopVisitsToIngest.add(smallDelayVisit);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);

        with()
            .pollDelay(5, SECONDS)
            .await()
            .atMost(6, TimeUnit.SECONDS).until(
                    () -> {
                        stopVisitsToIngest.clear();
                        return true;
                    }
            );

        MonitoredStopVisit smallDelayVisit2 = createStopVisit(STOP_REF, itemId, aimed, 8);

        // setting cancelled status on the second notification. Filter should not be applied
        smallDelayVisit2.getMonitoredVehicleJourney().getMonitoredCall().setArrivalStatus(CallStatusEnumeration.CANCELLED);
        stopVisitsToIngest.add(smallDelayVisit2);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);

        with()
            //Attente nécessaire car le post est traité par un thread
            .pollDelay(2, SECONDS)
            .await()
            .atMost(6, TimeUnit.SECONDS).until(
                    () -> {
                        // Récupérer et tracer les requêtes reçues
                        int nbOfReceivedRequests = TestUtils.printReceivedRequests(mockServer);

                        // we are expecting 2 because filter is not applied when status is cancelled
                        return nbOfReceivedRequests == 2;
                    }
            );
    }

    /**
     * test with changeBeforeUpdate = 10 and second stopVisit delay = 8
     * Expected result : 2 notifications because filter is not applied when status is arrived
     *
     */
    @Test
    void test_changeBeforeUpdate_10_arrived_status() {

        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/incomingSiri")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withBody("{\"message\":\"success\"}")
        );

        OutboundSubscriptionSetup outboundSubscription = createOutboundSMSubscription(false, STOP_REF, 10);
        serverSubscriptionManager.addSubscription(outboundSubscription);
        List<MonitoredStopVisit> stopVisitsToIngest = new ArrayList<>();


        ZonedDateTime aimed = ZonedDateTime.now().plusHours(1);
        String itemId = "stop1-L1-" + aimed;
        MonitoredStopVisit smallDelayVisit = createStopVisit(STOP_REF, itemId, aimed, 5);
        stopVisitsToIngest.add(smallDelayVisit);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);

        with()
            .pollDelay(5, SECONDS)
            .await()
            .atMost(6, TimeUnit.SECONDS).until(
                    () -> {
                        stopVisitsToIngest.clear();
                        return true;
                    }
            );

        MonitoredStopVisit smallDelayVisit2 = createStopVisit(STOP_REF, itemId, aimed, 8);

        // setting cancelled status on the second notification. Filter should not be applied
        smallDelayVisit2.getMonitoredVehicleJourney().getMonitoredCall().setArrivalStatus(CallStatusEnumeration.ARRIVED);
        stopVisitsToIngest.add(smallDelayVisit2);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);

        with()
            //Attente nécessaire car le post est traité par un thread
            .pollDelay(2, SECONDS)
            .await()
            .atMost(6, TimeUnit.SECONDS).until(
                    () -> {
                        // Récupérer et tracer les requêtes reçues
                        int nbOfReceivedRequests = TestUtils.printReceivedRequests(mockServer);

                        // we are expecting 2 because filter is not applied when status is arrived
                        return nbOfReceivedRequests == 2;
                    }
            );
    }

    /**
     * test with changeBeforeUpdate = 10 and second stopVisit delay = 8
     * Expected result : 2 notifications because filter is not applied when status is departed
     *
     */
    @Test
    void test_changeBeforeUpdate_10_departed_status() {

        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/incomingSiri")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withBody("{\"message\":\"success\"}")
        );

        OutboundSubscriptionSetup outboundSubscription = createOutboundSMSubscription(false, STOP_REF, 10);
        serverSubscriptionManager.addSubscription(outboundSubscription);
        List<MonitoredStopVisit> stopVisitsToIngest = new ArrayList<>();


        ZonedDateTime aimed = ZonedDateTime.now().plusHours(1);
        String itemId = "stop1-L1-" + aimed;
        MonitoredStopVisit smallDelayVisit = createStopVisit(STOP_REF, itemId, aimed, 5);
        stopVisitsToIngest.add(smallDelayVisit);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);

        with()
            .pollDelay(5, SECONDS)
            .await()
            .atMost(6, TimeUnit.SECONDS).until(
                    () -> {
                        stopVisitsToIngest.clear();
                        return true;
                    }
            );

        MonitoredStopVisit smallDelayVisit2 = createStopVisit(STOP_REF, itemId, aimed, 8);

        // setting cancelled status on the second notification. Filter should not be applied
        smallDelayVisit2.getMonitoredVehicleJourney().getMonitoredCall().setArrivalStatus(CallStatusEnumeration.DEPARTED);
        stopVisitsToIngest.add(smallDelayVisit2);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);

        with()
            //Attente nécessaire car le post est traité par un thread
            .pollDelay(2, SECONDS)
            .await()
            .atMost(6, TimeUnit.SECONDS).until(
                    () -> {
                        // Récupérer et tracer les requêtes reçues
                        int nbOfReceivedRequests = TestUtils.printReceivedRequests(mockServer);

                        // we are expecting 2 because filter is not applied when status is departed
                        return nbOfReceivedRequests == 2;
                    }
            );
    }

    private OutboundSubscriptionSetup createOutboundSMSubscription(boolean useOriginalId, String stopRef, int changeBeforeUpdate) {

        Map<Class, Set<String>> filterMap = new HashMap<>();
        Set<String> stopToFilter = new HashSet<>();
        stopToFilter.add(stopRef);
        filterMap.put(MonitoringRefStructure.class, stopToFilter);

        String address = "http://localhost:1080/incomingSiri";
        List<ValueAdapter> adapters = new ArrayList<>();
        return new OutboundSubscriptionSetup(ZonedDateTime.now(),
                SiriDataType.STOP_MONITORING, address, 3600,
                true, changeBeforeUpdate, 0,
                filterMap, adapters,
                "outSubId1", "requestorRef", ZonedDateTime.now().plusHours(1), "DAT1", "clientTrackingName", useOriginalId, SiriValidator.Version.VERSION_2_1);
    }


    private MonitoredStopVisit createStopVisit(String stopId, String itemIdentifier, ZonedDateTime aimed, long delay) {
        System.out.println("creating visit with delay: " + delay);
        MonitoredStopVisit stopVisit = new MonitoredStopVisit();
        MonitoringRefStructure monitoringRefStructure = new MonitoringRefStructure();
        monitoringRefStructure.setValue(stopId);
        stopVisit.setMonitoringRef(monitoringRefStructure);
        stopVisit.setRecordedAtTime(ZonedDateTime.now());
        stopVisit.setItemIdentifier(itemIdentifier);
        MonitoredVehicleJourneyStructure monitoredVehicleJourney = new MonitoredVehicleJourneyStructure();
        MonitoredCallStructure monitoredCallStructure = new MonitoredCallStructure();


        monitoredCallStructure.setAimedArrivalTime(aimed);
        monitoredCallStructure.setAimedDepartureTime(aimed);

        ZonedDateTime expected = aimed.plusSeconds(delay);
        System.out.println("expectedDeparture: " + expected);
        monitoredCallStructure.setExpectedDepartureTime(expected);
        monitoredCallStructure.setExpectedArrivalTime(expected);
        monitoredVehicleJourney.setMonitoredCall(monitoredCallStructure);
        stopVisit.setMonitoredVehicleJourney(monitoredVehicleJourney);
        return stopVisit;
    }


}
