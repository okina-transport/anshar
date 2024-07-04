package no.rutebanken.anshar.idTests.situationExchange;


import com.hazelcast.map.IMap;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.HazelcastTestMap;
import no.rutebanken.anshar.data.SiriObjectStorageKey;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.idTests.TestUtils;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.inbound.SituationExchangeInbound;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.PtSituationElement;

import javax.xml.bind.JAXBException;
import java.time.ZonedDateTime;
import java.util.*;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class SX_subcription_tests extends SpringBootBaseTest {

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    @Autowired
    private Situations situations;


    @Autowired
    private SituationExchangeInbound situationExchangeInbound;

    @Autowired
    private SubscriptionConfig subscriptionConfig;


    private ClientAndServer mockServer;

    @BeforeEach
    public void startServer() {
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


    private static String OLD_LINE1_REF = "OLDPREFLINE1:L1:LOC";
    private static String NEW_LINE1_REF = "NEWPREFLINE1:L1:LOC2";

    private static String OLD_STOP1_REF = "OLDPREFSTOP1:stop1:LOC";
    private static String NEW_STOP1_REF = "NEWPREFSTOP1:stop1:LOC2";


    @Test
    public void SX_useOriginalId_true() throws JAXBException, InterruptedException {

        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/incomingSiri")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withBody("{\"message\":\"success\"}")
        );

        String sitNumber1 = "SIT-LIN1";
        resetIdProcessings();
        IMap<SiriObjectStorageKey, PtSituationElement> originalSaved = situations.getSituationElements();
        HazelcastTestMap<PtSituationElement> testMap = new HazelcastTestMap<PtSituationElement>();
        situations.setSituationElements(testMap);

        PtSituationElement situation1 = TestUtils.createSituationForLine(sitNumber1, OLD_LINE1_REF);
        TestUtils.addAffectedStop(situation1, OLD_STOP1_REF);

        OutboundSubscriptionSetup outboundSubscription = createOutboundSubscription(true);
        serverSubscriptionManager.addSubscription(outboundSubscription);

        List<PtSituationElement> situationsToIngest = new ArrayList<>();
        situationsToIngest.add(situation1);


        situationExchangeInbound.ingestSituations("DAT1", situationsToIngest, true);

        //Attente nécessaire car le post est traité par un thread
        Thread.sleep(5000);

        // Récupérer et tracer les requêtes reçues
        TestUtils.printReceivedRequests(mockServer);

        //Faire les vérifications
        TestUtils.verifyStringInResponse(mockServer, "<StopPointRef>" + NEW_STOP1_REF + "</StopPointRef>");
        TestUtils.verifyStringInResponse(mockServer, "<LineRef>" + NEW_LINE1_REF + "</LineRef>");
        situations.setSituationElements(originalSaved);
    }

    @Test
    public void SX_useOriginalId_false() throws JAXBException, InterruptedException {
        initStopPlaceMapper();

        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/incomingSiri")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withBody("{\"message\":\"success\"}")
        );

        String sitNumber1 = "SIT-LIN1";
        resetIdProcessings();
        IMap<SiriObjectStorageKey, PtSituationElement> originalSaved = situations.getSituationElements();
        HazelcastTestMap<PtSituationElement> testMap = new HazelcastTestMap<PtSituationElement>();
        situations.setSituationElements(testMap);

        PtSituationElement situation1 = TestUtils.createSituationForLine(sitNumber1, OLD_LINE1_REF);
        TestUtils.addAffectedStop(situation1, OLD_STOP1_REF);

        OutboundSubscriptionSetup outboundSubscription = createOutboundSubscription(false);
        serverSubscriptionManager.addSubscription(outboundSubscription);

        List<PtSituationElement> situationsToIngest = new ArrayList<>();
        situationsToIngest.add(situation1);


        situationExchangeInbound.ingestSituations("DAT1", situationsToIngest, true);

        //Attente nécessaire car le post est traité par un thread
        Thread.sleep(5000);

        // Récupérer et tracer les requêtes reçues
        TestUtils.printReceivedRequests(mockServer);

        //Faire les vérifications
        TestUtils.verifyStringInResponse(mockServer, "<StopPointRef>MOBIITI:Quay:a</StopPointRef>");
        TestUtils.verifyStringInResponse(mockServer, "<LineRef>" + NEW_LINE1_REF + "</LineRef>");
        situations.setSituationElements(originalSaved);
    }

    public void initStopPlaceMapper() {
        Map<String, Pair<String, String>> stopPlaceMap;

        stopPlaceMap = new HashMap<>();
        stopPlaceMap.put(NEW_STOP1_REF, Pair.of("MOBIITI:Quay:a", "test1"));

        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);

        //Manually adding custom mapping to Spring context
        stopPlaceService.addStopPlaceMappings(stopPlaceMap);

        Set<String> quayRefs = new HashSet<>();
        quayRefs.add(NEW_STOP1_REF);
        stopPlaceService.addStopQuays(quayRefs);

    }

    private void resetIdProcessings() {
        subscriptionConfig.getIdProcessingParameters().clear();

        IdProcessingParameters dat1Line = new IdProcessingParameters();
        dat1Line.setObjectType(ObjectType.LINE);
        dat1Line.setDatasetId("DAT1");
        dat1Line.setInputPrefixToRemove("OLDPREFLINE1:");
        dat1Line.setInputSuffixToRemove(":LOC");
        dat1Line.setOutputPrefixToAdd("NEWPREFLINE1:");
        dat1Line.setOutputSuffixToAdd(":LOC2");
        subscriptionConfig.getIdProcessingParameters().add(dat1Line);

        IdProcessingParameters dat1Stop = new IdProcessingParameters();
        dat1Stop.setObjectType(ObjectType.STOP);
        dat1Stop.setDatasetId("DAT1");
        dat1Stop.setInputPrefixToRemove("OLDPREFSTOP1:");
        dat1Stop.setInputSuffixToRemove(":LOC");
        dat1Stop.setOutputPrefixToAdd("NEWPREFSTOP1:");
        dat1Stop.setOutputSuffixToAdd(":LOC2");
        subscriptionConfig.getIdProcessingParameters().add(dat1Stop);

        IdProcessingParameters dat1Network = new IdProcessingParameters();
        dat1Network.setObjectType(ObjectType.NETWORK);
        dat1Network.setDatasetId("DAT1");
        dat1Network.setInputPrefixToRemove("OLDPREFNETWORK1:");
        dat1Network.setInputSuffixToRemove(":LOC");
        dat1Network.setOutputPrefixToAdd("NEWPREFNETWORK1:");
        dat1Network.setOutputSuffixToAdd(":LOC2");
        subscriptionConfig.getIdProcessingParameters().add(dat1Network);

        IdProcessingParameters dat2Line = new IdProcessingParameters();
        dat2Line.setObjectType(ObjectType.LINE);
        dat2Line.setDatasetId("DAT2");
        dat2Line.setInputPrefixToRemove("OLDPREFLINE2:");
        dat2Line.setInputSuffixToRemove(":LOC");
        dat2Line.setOutputPrefixToAdd("NEWPREFLINE2:");
        dat2Line.setOutputSuffixToAdd(":LOC2");
        subscriptionConfig.getIdProcessingParameters().add(dat2Line);

        IdProcessingParameters dat2Stop = new IdProcessingParameters();
        dat2Stop.setObjectType(ObjectType.STOP);
        dat2Stop.setDatasetId("DAT2");
        dat2Stop.setInputPrefixToRemove("OLDPREFSTOP2:");
        dat2Stop.setInputSuffixToRemove(":LOC");
        dat2Stop.setOutputPrefixToAdd("NEWPREFSTOP2:");
        dat2Stop.setOutputSuffixToAdd(":LOC2");
        subscriptionConfig.getIdProcessingParameters().add(dat2Stop);

        IdProcessingParameters dat2Network = new IdProcessingParameters();
        dat2Network.setObjectType(ObjectType.NETWORK);
        dat2Network.setDatasetId("DAT2");
        dat2Network.setInputPrefixToRemove("OLDPREFNETWORK2:");
        dat2Network.setInputSuffixToRemove(":LOC");
        dat2Network.setOutputPrefixToAdd("NEWPREFNETWORK2:");
        dat2Network.setOutputSuffixToAdd(":LOC2");
        subscriptionConfig.getIdProcessingParameters().add(dat2Network);
    }

    private OutboundSubscriptionSetup createOutboundSubscription(boolean useOriginalId) {

        String address = "http://localhost:1080/incomingSiri";
        List<ValueAdapter> adapters = new ArrayList<>();
        OutboundSubscriptionSetup subscription = new OutboundSubscriptionSetup(ZonedDateTime.now(),
                SiriDataType.SITUATION_EXCHANGE, address, 3600,
                true, 30, 0,
                new HashMap<>(), adapters,
                "outSubId1", "requestorRef", ZonedDateTime.now().plusHours(1), "DAT1", "clientTrackingName", useOriginalId);
        return subscription;
    }
}
