package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.idTests.TestUtils;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.inbound.StopMonitoringInbound;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.entur.siri.validator.SiriValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.*;

import javax.xml.bind.JAXBException;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class SMsubscriptionTest extends SpringBootBaseTest {

    private ClientAndServer mockServer;


    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;


    @Autowired
    private StopMonitoringInbound stopMonitoringInbound;

    private static final String STOP_REF = "stop1";

    private static final int CHANGE_BEFORE_UPDATE = 30;

    @BeforeEach
    public void startServer() {
        mockServer = startClientAndServer(1080);
        System.out.println("MockServer démarré sur le port 1080");
        serverSubscriptionManager.clearAllOutboundSubscriptions();
    }

    @AfterEach
    public void stopServer() {
        if (mockServer != null) {
            mockServer.stop();
            System.out.println("MockServer arrêté");
        }
    }


    @Test
    public void SM_check_that_high_delay_should_be_transmitted_to_outbound() throws JAXBException, InterruptedException {

        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/incomingSiri")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withBody("{\"message\":\"success\"}")
        );


        OutboundSubscriptionSetup outboundSubscription = createOutboundSMSubscription(false, STOP_REF, CHANGE_BEFORE_UPDATE);
        serverSubscriptionManager.addSubscription(outboundSubscription);
        List<MonitoredStopVisit> stopVisitsToIngest = new ArrayList<>();

        // Creating a MonitoringVisit with a high delay (60 s) between aimedDeparture and expectedDeparture
        MonitoredStopVisit smallDelayVisit = createStopVisit(STOP_REF, 60);
        stopVisitsToIngest.add(smallDelayVisit);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);

        //Attente nécessaire car le post est traité par un thread
        Thread.sleep(5000);

        // Récupérer et tracer les requêtes reçues
        int nbOfReceivedRequests = TestUtils.printReceivedRequests(mockServer);

        // we are expecting 1 because the high delay (60s) is higher than changeBeforeUpdate (30s)
        assertEquals(1, nbOfReceivedRequests);
    }

    @Test
    public void SM_checking_that_cancellations_are_sent_to_customer() throws JAXBException, InterruptedException {

        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/incomingSiri")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withBody("{\"message\":\"success\"}")
        );


        OutboundSubscriptionSetup outboundSubscription = createOutboundSMSubscription(false, STOP_REF, CHANGE_BEFORE_UPDATE);
        serverSubscriptionManager.addSubscription(outboundSubscription);
        List<MonitoredStopVisitCancellation> cancellationToIngest = new ArrayList<>();

        // Creating a MonitoringVisit with a high delay (60 s) between aimedDeparture and expectedDeparture
        MonitoredStopVisitCancellation cancellation = createStopVisitCancellation(STOP_REF);
        cancellationToIngest.add(cancellation);
        stopMonitoringInbound.cancelStopVisits("DAT1", cancellationToIngest);


        //Attente nécessaire car le post est traité par un thread
        Thread.sleep(5000);

        // Récupérer et tracer les requêtes reçues
        int nbOfReceivedRequests = TestUtils.printReceivedRequests(mockServer);

        // we are expecting 1 because the high delay (60s) is higher than changeBeforeUpdate (30s)
        assertEquals(1, nbOfReceivedRequests);
    }

    @Test
    public void SM_check_that_no_delay_SM_is_transmitted_with_changeBeforeUpdate_equal_to_0() throws JAXBException, InterruptedException {

        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/incomingSiri")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withBody("{\"message\":\"success\"}")
        );

        //Creating outbound subscription with changeBeforeUpdate = 0 (means that client did not send changeBeforeUpdate
        OutboundSubscriptionSetup outboundSubscription = createOutboundSMSubscription(false, STOP_REF, 0);
        serverSubscriptionManager.addSubscription(outboundSubscription);
        List<MonitoredStopVisit> stopVisitsToIngest = new ArrayList<>();

        // Creating a MonitoringVisit with no delay between aimedDeparture and expectedDeparture
        MonitoredStopVisit smallDelayVisit = createStopVisit(STOP_REF, 0);
        stopVisitsToIngest.add(smallDelayVisit);
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);

        //Attente nécessaire car le post est traité par un thread
        Thread.sleep(5000);

        // Récupérer et tracer les requêtes reçues
        int nbOfReceivedRequests = TestUtils.printReceivedRequests(mockServer);

        // we are expecting 1 because changeBeforeUpdate is 0
        assertEquals(1, nbOfReceivedRequests);
    }

    private MonitoredStopVisit createStopVisit(String stopId, long delay) {
        System.out.println("creating visit with delay: " + delay);
        MonitoredStopVisit stopVisit = new MonitoredStopVisit();
        MonitoringRefStructure monitoringRefStructure = new MonitoringRefStructure();
        monitoringRefStructure.setValue(stopId);
        stopVisit.setMonitoringRef(monitoringRefStructure);
        stopVisit.setRecordedAtTime(ZonedDateTime.now());
        stopVisit.setItemIdentifier(UUID.randomUUID().toString());
        MonitoredVehicleJourneyStructure monitoredVehicleJourney = new MonitoredVehicleJourneyStructure();
        MonitoredCallStructure monitoredCallStructure = new MonitoredCallStructure();
        ZonedDateTime aimedDeparture = ZonedDateTime.now().plusHours(1);
        System.out.println("aimedDeparture: " + aimedDeparture);
        monitoredCallStructure.setAimedDepartureTime(aimedDeparture);
        ZonedDateTime expectedDeparture = aimedDeparture.plusSeconds(delay);
        System.out.println("expectedDeparture: " + expectedDeparture);
        monitoredCallStructure.setExpectedDepartureTime(expectedDeparture);
        monitoredVehicleJourney.setMonitoredCall(monitoredCallStructure);
        stopVisit.setMonitoredVehicleJourney(monitoredVehicleJourney);
        return stopVisit;
    }

    private MonitoredStopVisitCancellation createStopVisitCancellation(String stopId) {

        MonitoredStopVisitCancellation cancellation = new MonitoredStopVisitCancellation();
        MonitoringRefStructure monitoringRefStructure = new MonitoringRefStructure();
        monitoringRefStructure.setValue(stopId);
        cancellation.setMonitoringRef(monitoringRefStructure);
        cancellation.setRecordedAtTime(ZonedDateTime.now());
        return cancellation;
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


}
