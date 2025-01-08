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
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.*;

import javax.xml.bind.UnmarshalException;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class ChangeBeforeUpdateTest extends SpringBootBaseTest {


    @Autowired
    private MonitoredStopVisits monitoredStopVisits;

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    private ClientAndServer mockServer;

    private static final String STOP_REF = "stop1";

    private static final int CHANGE_BEFORE_UPDATE = 30;

    @Autowired
    private StopMonitoringInbound stopMonitoringInbound;


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
     * 2 stopVisits are incoming, we shoould receive 2 notifications in outbound subscription because there is no filter
     *
     * @throws UnmarshalException
     * @throws InterruptedException
     */
    @Test
    public void test_changeBeforeUpdate_0() throws UnmarshalException, InterruptedException {

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
        String itemId = "stop1-L1-" + aimed.toString();
        MonitoredStopVisit smallDelayVisit = createStopVisit(STOP_REF, itemId, aimed, 5);
        stopVisitsToIngest.add(smallDelayVisit);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);

        Thread.sleep(5000);

        stopVisitsToIngest.clear();
        MonitoredStopVisit smallDelayVisit2 = createStopVisit(STOP_REF, itemId, aimed, 8);
        stopVisitsToIngest.add(smallDelayVisit2);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);


        //Attente nécessaire car le post est traité par un thread
        Thread.sleep(5000);

        // Récupérer et tracer les requêtes reçues
        int nbOfReceivedRequests = TestUtils.printReceivedRequests(mockServer);

        // we are expecting 2 because there is no filter (changeBeforeUpdate = 0)
        assertEquals(2, nbOfReceivedRequests);
    }

    /**
     * test with changeBeforeUpdate = 10 and second stopVisit delay = 8
     * Expected result : the second notification SHOULD NOT be sent because delay < chaungeBeforeUpdate
     *
     * @throws UnmarshalException
     * @throws InterruptedException
     */
    @Test
    public void test_changeBeforeUpdate_10() throws UnmarshalException, InterruptedException {

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
        String itemId = "stop1-L1-" + aimed.toString();
        MonitoredStopVisit smallDelayVisit = createStopVisit(STOP_REF, itemId, aimed, 5);
        stopVisitsToIngest.add(smallDelayVisit);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);

        Thread.sleep(5000);

        stopVisitsToIngest.clear();
        MonitoredStopVisit smallDelayVisit2 = createStopVisit(STOP_REF, itemId, aimed, 8);
        stopVisitsToIngest.add(smallDelayVisit2);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);


        //Attente nécessaire car le post est traité par un thread
        Thread.sleep(5000);

        // Récupérer et tracer les requêtes reçues
        int nbOfReceivedRequests = TestUtils.printReceivedRequests(mockServer);

        // we are expecting 1 because the second stopVisit has a delay = 8 and changeBeforeUpdate = 10
        assertEquals(1, nbOfReceivedRequests);
    }

    /**
     * test with changeBeforeUpdate = 10 and second stopVisit delay = 8
     * Expected result : 2 notifications because filter is not applied when status is cancelled
     *
     * @throws UnmarshalException
     * @throws InterruptedException
     */
    @Test
    public void test_changeBeforeUpdate_10_cancelled_status() throws UnmarshalException, InterruptedException {

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
        String itemId = "stop1-L1-" + aimed.toString();
        MonitoredStopVisit smallDelayVisit = createStopVisit(STOP_REF, itemId, aimed, 5);
        stopVisitsToIngest.add(smallDelayVisit);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);

        Thread.sleep(5000);

        stopVisitsToIngest.clear();
        MonitoredStopVisit smallDelayVisit2 = createStopVisit(STOP_REF, itemId, aimed, 8);

        // setting cancelled status on the second notification. Filter should not be applied
        smallDelayVisit2.getMonitoredVehicleJourney().getMonitoredCall().setArrivalStatus(CallStatusEnumeration.CANCELLED);
        stopVisitsToIngest.add(smallDelayVisit2);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);


        //Attente nécessaire car le post est traité par un thread
        Thread.sleep(5000);

        // Récupérer et tracer les requêtes reçues
        int nbOfReceivedRequests = TestUtils.printReceivedRequests(mockServer);

        // we are expecting 2 because filter is not applied when status is cancelled
        assertEquals(2, nbOfReceivedRequests);
    }

    /**
     * test with changeBeforeUpdate = 10 and second stopVisit delay = 8
     * Expected result : 2 notifications because filter is not applied when status is arrived
     *
     * @throws UnmarshalException
     * @throws InterruptedException
     */
    @Test
    public void test_changeBeforeUpdate_10_arrived_status() throws UnmarshalException, InterruptedException {

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
        String itemId = "stop1-L1-" + aimed.toString();
        MonitoredStopVisit smallDelayVisit = createStopVisit(STOP_REF, itemId, aimed, 5);
        stopVisitsToIngest.add(smallDelayVisit);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);

        Thread.sleep(5000);

        stopVisitsToIngest.clear();
        MonitoredStopVisit smallDelayVisit2 = createStopVisit(STOP_REF, itemId, aimed, 8);

        // setting cancelled status on the second notification. Filter should not be applied
        smallDelayVisit2.getMonitoredVehicleJourney().getMonitoredCall().setArrivalStatus(CallStatusEnumeration.ARRIVED);
        stopVisitsToIngest.add(smallDelayVisit2);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);


        //Attente nécessaire car le post est traité par un thread
        Thread.sleep(5000);

        // Récupérer et tracer les requêtes reçues
        int nbOfReceivedRequests = TestUtils.printReceivedRequests(mockServer);

        // we are expecting 2 because filter is not applied when status is arrived
        assertEquals(2, nbOfReceivedRequests);
    }

    /**
     * test with changeBeforeUpdate = 10 and second stopVisit delay = 8
     * Expected result : 2 notifications because filter is not applied when status is departed
     *
     * @throws UnmarshalException
     * @throws InterruptedException
     */
    @Test
    public void test_changeBeforeUpdate_10_departed_status() throws UnmarshalException, InterruptedException {

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
        String itemId = "stop1-L1-" + aimed.toString();
        MonitoredStopVisit smallDelayVisit = createStopVisit(STOP_REF, itemId, aimed, 5);
        stopVisitsToIngest.add(smallDelayVisit);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);

        Thread.sleep(5000);

        stopVisitsToIngest.clear();
        MonitoredStopVisit smallDelayVisit2 = createStopVisit(STOP_REF, itemId, aimed, 8);

        // setting cancelled status on the second notification. Filter should not be applied
        smallDelayVisit2.getMonitoredVehicleJourney().getMonitoredCall().setArrivalStatus(CallStatusEnumeration.DEPARTED);
        stopVisitsToIngest.add(smallDelayVisit2);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);


        //Attente nécessaire car le post est traité par un thread
        Thread.sleep(5000);

        // Récupérer et tracer les requêtes reçues
        int nbOfReceivedRequests = TestUtils.printReceivedRequests(mockServer);

        // we are expecting 2 because filter is not applied when status is departed
        assertEquals(2, nbOfReceivedRequests);
    }

    private OutboundSubscriptionSetup createOutboundSMSubscription(boolean useOriginalId, String stopRef, int changeBeforeUpdate) {

        Map<Class, Set<String>> filterMap = new HashMap<>();
        Set<String> stopToFilter = new HashSet<>();
        stopToFilter.add(stopRef);
        filterMap.put(MonitoringRefStructure.class, stopToFilter);

        String address = "http://localhost:1080/incomingSiri";
        List<ValueAdapter> adapters = new ArrayList<>();
        OutboundSubscriptionSetup subscription = new OutboundSubscriptionSetup(ZonedDateTime.now(),
                SiriDataType.STOP_MONITORING, address, 3600,
                true, changeBeforeUpdate, 0,
                filterMap, adapters,
                "outSubId1", "requestorRef", ZonedDateTime.now().plusHours(1), "DAT1", "clientTrackingName", useOriginalId, SiriValidator.Version.VERSION_2_1);
        return subscription;
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
