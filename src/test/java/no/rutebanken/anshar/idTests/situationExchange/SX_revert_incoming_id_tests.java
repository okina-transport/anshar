package no.rutebanken.anshar.idTests.situationExchange;

import com.hazelcast.map.IMap;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.IncomingSiriParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.HazelcastTestMap;
import no.rutebanken.anshar.data.SiriObjectStorageKey;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.idTests.TestUtils;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.Siri;

import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class SX_revert_incoming_id_tests extends SpringBootBaseTest {


    @Autowired
    private Situations situations;

    @Autowired
    private SiriHandler handler;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private SiriObjectFactory factory;

    private static String OLD_LINE1_REF = "OLDPREFLINE1:L1:LOC";
    private static String NEW_LINE1_REF = "NEWPREFLINE1:L1:LOC2";
    private static String NEW_NETWORK_REF = "NEWPREFNETWORK1:network1:LOC2";

    private static String OLD_STOP1_REF = "OLDPREFSTOP1:stop1:LOC";
    private static String NEW_STOP1_REF = "DAT1:stop1:LOC2";


    @Test
    public void SX_test_NOT_revert_id() throws JAXBException {
        initStopPlaceMapper();
        String sitNumber1 = "SIT-LIN1";
        resetIdProcessings();
        IMap<SiriObjectStorageKey, PtSituationElement> originalSaved = situations.getSituationElements();
        HazelcastTestMap<PtSituationElement> testMap = new HazelcastTestMap<PtSituationElement>();
        situations.setSituationElements(testMap);

        PtSituationElement situation1 = TestUtils.createSituationForLine(sitNumber1, OLD_LINE1_REF);
        TestUtils.addAffectedStop(situation1, OLD_STOP1_REF);
        List<PtSituationElement> situationsToAdd = new ArrayList();
        situationsToAdd.add(situation1);


        //  situations.add("DAT1", situation1);
        SubscriptionSetup subscriptionSetup = new SubscriptionSetup();
        subscriptionSetup.setSubscriptionId("TST:revert-SX");
        subscriptionSetup.setDatasetId("DAT1");
        subscriptionSetup.setSubscriptionType(SiriDataType.SITUATION_EXCHANGE);
        subscriptionSetup.setRevertIds(false);

        subscriptionManager.addSubscription(subscriptionSetup.getSubscriptionId(), subscriptionSetup);


        Siri siri = factory.createSXServiceDelivery(situationsToAdd);
        String incomingXml = SiriXml.toXml(siri);

        handler.handleIncomingSiri(IncomingSiriParameters.buildFromSubscription(subscriptionSetup.getSubscriptionId(), new ByteArrayInputStream(incomingXml.getBytes())));


        assertFalse(situations.getSituationElements().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <SituationExchangeRequest version=\"2.0\">\n" +
                "        </SituationExchangeRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(xml);
        params.setDatasetId("DAT1");
        params.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("true", "false"));
        params.setMaxSize(-1);

        Siri response = handler.handleIncomingSiri(params);

        PtSituationElement result1 = TestUtils.getSituationFromSiri(response, sitNumber1);
        assertEquals(NEW_LINE1_REF, TestUtils.getLineRef(result1));
        assertEquals(NEW_STOP1_REF, TestUtils.getStopRef(result1));


        situations.setSituationElements(originalSaved);
    }

    @Test
    public void SX_test_revert_id() throws JAXBException {
        initStopPlaceMapper();

        String sitNumber1 = "SIT-LIN1";
        resetIdProcessings();
        IMap<SiriObjectStorageKey, PtSituationElement> originalSaved = situations.getSituationElements();
        HazelcastTestMap<PtSituationElement> testMap = new HazelcastTestMap<PtSituationElement>();
        situations.setSituationElements(testMap);

        PtSituationElement situation1 = TestUtils.createSituationForLine(sitNumber1, "MOBIITI:Line:L1:LOC");
        TestUtils.addAffectedStop(situation1, "MOBIITI:Quay:a");
        TestUtils.addAffectedStopInRoute(situation1, "MOBIITI:Quay:a");
        TestUtils.addAffectedNetwork(situation1, "MOBIITI:Network:network1");


        List<PtSituationElement> situationsToAdd = new ArrayList();
        situationsToAdd.add(situation1);


        //  situations.add("DAT1", situation1);
        SubscriptionSetup subscriptionSetup = new SubscriptionSetup();
        subscriptionSetup.setSubscriptionId("TST:revert-SX");
        subscriptionSetup.setDatasetId("DAT1");
        subscriptionSetup.setSubscriptionType(SiriDataType.SITUATION_EXCHANGE);
        subscriptionSetup.setRevertIds(true);

        subscriptionManager.addSubscription(subscriptionSetup.getSubscriptionId(), subscriptionSetup);


        Siri siri = factory.createSXServiceDelivery(situationsToAdd);
        String incomingXml = SiriXml.toXml(siri);

        handler.handleIncomingSiri(IncomingSiriParameters.buildFromSubscription(subscriptionSetup.getSubscriptionId(), new ByteArrayInputStream(incomingXml.getBytes())));


        assertFalse(situations.getSituationElements().isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <SituationExchangeRequest version=\"2.0\">\n" +
                "        </SituationExchangeRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(xml);
        params.setDatasetId("DAT1");
        params.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("true", "false"));
        params.setMaxSize(-1);

        Siri response = handler.handleIncomingSiri(params);

        PtSituationElement result1 = TestUtils.getSituationFromSiri(response, sitNumber1);
        assertEquals(NEW_LINE1_REF, TestUtils.getLineRef(result1));
        assertEquals(NEW_STOP1_REF, TestUtils.getStopRef(result1));
        assertEquals(NEW_STOP1_REF, TestUtils.getStopRefInRoute(result1));
        assertEquals(NEW_NETWORK_REF, TestUtils.getNetworkRef(result1));


        situations.setSituationElements(originalSaved);
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
        dat1Stop.setOutputPrefixToAdd("DAT1:");
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

    public void initStopPlaceMapper() {
        Map<String, Pair<String, String>> stopPlaceMap;

        stopPlaceMap = new HashMap<>();
        stopPlaceMap.put(NEW_STOP1_REF, Pair.of("MOBIITI:Quay:a", "test1"));

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

        Set<String> quayRefs = new HashSet<>();
        quayRefs.add(NEW_STOP1_REF);
        stopPlaceService.addStopQuays(quayRefs);

    }
}
