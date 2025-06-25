package no.rutebanken.anshar.outbound;

import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.config.IncomingSiriParameters;
import no.rutebanken.anshar.idTests.TestUtils;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.inbound.SituationExchangeInbound;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.awaitility.Durations;
import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.verify.VerificationTimes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import uk.org.siri.siri21.*;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.stream.XMLStreamException;
import java.time.ZonedDateTime;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@Slf4j
class OutboundSubscriptionTest extends SpringBootBaseTest {

    private static final String SITUATION_NUMBER = "57baf40e-02e9-44b2-930a-3e07a3ffa724";
    private static final String LINE_REF = "4465";

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    @Autowired
    private SituationExchangeInbound situationExchangeInbound;

    @Value("${anshar.outbound.heartbeatinterval.minimum}")
    private long minimumHeartbeatInterval = 10000;

    @Value("${anshar.outbound.heartbeatinterval.maximum}")
    private long maximumHeartbeatInterval = 300000;

    private ClientAndServer mockServer;

    @AfterEach
    void stopServer() {
        if (mockServer != null) {
            mockServer.stop();
            log.info("MockServer arrêté");
        }
    }

    @Test
    void testHeartbeatInterval() throws DatatypeConfigurationException {
        final long tooShortDurationInMilliSeconds = minimumHeartbeatInterval - 1;
        final long tooLongDurationInMilliSeconds = maximumHeartbeatInterval + 1;

        SubscriptionRequest subscriptionRequestWithTooShortInterval = getSubscriptionRequest(tooShortDurationInMilliSeconds);
        long heartbeatInterval = serverSubscriptionManager.getHeartbeatInterval(subscriptionRequestWithTooShortInterval);

        assertTrue(tooShortDurationInMilliSeconds < minimumHeartbeatInterval);
        assertEquals(heartbeatInterval, minimumHeartbeatInterval);


        SubscriptionRequest subscriptionRequestWithTooLongInterval = getSubscriptionRequest(tooLongDurationInMilliSeconds);
        heartbeatInterval = serverSubscriptionManager.getHeartbeatInterval(subscriptionRequestWithTooLongInterval);

        assertTrue(tooLongDurationInMilliSeconds > maximumHeartbeatInterval);
        assertEquals(heartbeatInterval, maximumHeartbeatInterval);
    }

    @Test
    void testDuplicateSubscriptionIds() throws JAXBException, XMLStreamException {
        final String subscriptionId = "36dfa2d0-51d7-42fb-b828-44fc07684239";
        String sxSubscription = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<SubscriptionRequest>\n" +
                "\t\t<RequestTimestamp>2019-12-06T14:38:42.2790513Z</RequestTimestamp>\n" +
                "\t\t<ConsumerAddress>https://0.0.0.0/api/siri/sx/36dfa2d0-51d7-42fb-b828-44fc07684239</ConsumerAddress>\n" +
                "\t\t<RequestorRef>GAG-SX-36dfa2d0-51d7-42fb-b828-44fc07684239</RequestorRef>\n" +
                "\t\t<MessageIdentifier>52467cd2-e469-4f37-8841-27c3a9b57b63</MessageIdentifier>\n" +
                "\t\t<SubscriptionContext>\n" +
                "\t\t\t<HeartbeatInterval>PT60M</HeartbeatInterval>\n" +
                "\t\t</SubscriptionContext>\n" +
                "\t\t<SituationExchangeSubscriptionRequest>\n" +
                "\t\t\t<SubscriptionIdentifier>" + subscriptionId + "</SubscriptionIdentifier>\n" +
                "\t\t\t<InitialTerminationTime>2119-12-06T14:38:42.2785096Z</InitialTerminationTime>\n" +
                "\t\t\t<SituationExchangeRequest>\n" +
                "\t\t\t\t<RequestTimestamp>2019-12-06T14:38:42.2787087Z</RequestTimestamp>\n" +
                "\t\t\t</SituationExchangeRequest>\n" +
                "\t\t</SituationExchangeSubscriptionRequest>\n" +
                "\t</SubscriptionRequest>\n" +
                "</Siri>";

        String etSubscription = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<SubscriptionRequest>\n" +
                "\t\t<RequestTimestamp>2019-12-06T14:38:42.5012071Z</RequestTimestamp>\n" +
                "\t\t<ConsumerAddress>https://0.0.0.0/api/siri/et/36dfa2d0-51d7-42fb-b828-44fc07684239</ConsumerAddress>\n" +
                "\t\t<RequestorRef>GAG-ET-36dfa2d0-51d7-42fb-b828-44fc07684239</RequestorRef>\n" +
                "\t\t<MessageIdentifier>444dde0d-3663-4de9-aa94-56ee17d86ba9</MessageIdentifier>\n" +
                "\t\t<SubscriptionContext>\n" +
                "\t\t\t<HeartbeatInterval>PT60M</HeartbeatInterval>\n" +
                "\t\t</SubscriptionContext>\n" +
                "\t\t<EstimatedTimetableSubscriptionRequest version=\"2.0\">\n" +
                "\t\t\t<SubscriptionIdentifier>" + subscriptionId + "</SubscriptionIdentifier>\n" +
                "\t\t\t<InitialTerminationTime>2119-12-06T14:38:42.500977Z</InitialTerminationTime>\n" +
                "\t\t\t<EstimatedTimetableRequest>\n" +
                "\t\t\t\t<RequestTimestamp>2019-12-06T14:38:42.5011831Z</RequestTimestamp>\n" +
                "\t\t\t</EstimatedTimetableRequest>\n" +
                "\t\t</EstimatedTimetableSubscriptionRequest>\n" +
                "\t</SubscriptionRequest>\n" +
                "</Siri>";

        boolean soapTransformation = false;
        IncomingSiriParameters incomingSiriParameters = new IncomingSiriParameters();
        incomingSiriParameters.setSoapTransformation(soapTransformation);
        incomingSiriParameters.setUseOriginalId(true);

        final Siri siriSX = serverSubscriptionManager.handleMultipleSubscriptionsRequest(SiriXml.parseXml(sxSubscription), incomingSiriParameters);
        final Siri siriET = serverSubscriptionManager.handleMultipleSubscriptionsRequest(SiriXml.parseXml(etSubscription), incomingSiriParameters);

        assertNotNull(siriSX);
        assertNotNull(siriET);

        assertNotNull(siriSX.getSubscriptionResponse());
        assertNotNull(siriSX.getSubscriptionResponse().getResponseStatuses());
        assertNotNull(siriSX.getSubscriptionResponse().getResponseStatuses().get(0).isStatus());
        assertTrue(siriSX.getSubscriptionResponse().getResponseStatuses().get(0).isStatus());

        assertNotNull(siriET.getSubscriptionResponse());
        assertNotNull(siriET.getSubscriptionResponse().getResponseStatuses());
        assertNotNull(siriET.getSubscriptionResponse().getResponseStatuses().get(0).isStatus());
        assertFalse(siriET.getSubscriptionResponse().getResponseStatuses().get(0).isStatus());

        serverSubscriptionManager.terminateSubscription(subscriptionId, true);
    }

    SubscriptionRequest getSubscriptionRequest(long heartbeatIntervalMillis) throws DatatypeConfigurationException {
        SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
        SubscriptionContextStructure context = new SubscriptionContextStructure();

        context.setHeartbeatInterval(DatatypeFactory.newInstance().newDuration(heartbeatIntervalMillis));

        subscriptionRequest.setSubscriptionContext(context);
        return subscriptionRequest;
    }

    @Test
    @DisplayName("Same input Siri SX received twice should generate only one GM subscription push")
    void ingestSiriSxGeneratingSiriGmSubscriptionPushTest() {
        mockServer = startClientAndServer(1080);
        log.info("MockServer démarré sur le port 1080");
        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/incomingSiri")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withBody("{\"message\":\"success\"}")
        );

        serverSubscriptionManager.addSubscription(TestUtils.createGmOutboundSubscription(true));

        PtSituationElement situation1 = TestUtils.createSituationForLine(SITUATION_NUMBER, LINE_REF);
        RequestorRef requestorRef = new RequestorRef();
        requestorRef.setValue("REF1");
        situation1.setParticipantRef(requestorRef);
        situation1.setCreationTime(ZonedDateTime.now().minusMinutes(2));
        HalfOpenTimestampOutputRangeStructure interval = new HalfOpenTimestampOutputRangeStructure();
        interval.setEndTime(ZonedDateTime.now().plusMinutes(10));
        situation1.getValidityPeriods().add(interval);
        List<PtSituationElement> inputSituations = List.of(situation1);

        situationExchangeInbound.ingestSituations("DAT1", inputSituations, true);

        await()
                .atMost(Durations.TEN_SECONDS)
                .atLeast(Durations.TWO_SECONDS)
                .pollInterval(Durations.ONE_SECOND)
                .pollDelay(Durations.TWO_SECONDS)
                .until(() -> serverSubscriptionManager.getAllSubscriptions(SiriDataType.GENERAL_MESSAGE).size() == 1);

        situationExchangeInbound.ingestSituations("DAT1", inputSituations, true);

        await()
                .atMost(Durations.TEN_SECONDS)
                .atLeast(Durations.TWO_SECONDS)
                .pollInterval(Durations.ONE_SECOND)
                .pollDelay(Durations.TWO_SECONDS)
                .until(() -> serverSubscriptionManager.getAllSubscriptions(SiriDataType.GENERAL_MESSAGE).size() == 1);

        mockServer.verify(
                request()
                        .withMethod("POST")
                        .withPath("/incomingSiri"),
                VerificationTimes.exactly(1)
        );
    }

    // TODO MHI : test for SM subscription
}
