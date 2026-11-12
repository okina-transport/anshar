package no.rutebanken.anshar.idTests.stopMonitoring;

import jakarta.xml.bind.UnmarshalException;
import no.rutebanken.anshar.config.IncomingSiriParameters;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.ResponseStatus;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SubscriptionResponseStructure;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * DATA-1507: each StopMonitoring subscription of a request containing an invalid stop reference
 * must be rejected with an InvalidDataReferencesError, while subscriptions with a valid stop reference are created.
 */
class SM_reject_subscription_on_invalid_stop_references extends SpringBootBaseTest {

    private static final String DATASET = "DAT1";
    private static final String QUAY1_REF = DATASET + ":Quay:REJ1";
    private static final String VALID_STOP = "MOBIITI:Quay:REJ1";
    private static final String UNKNOWN_STOP = "MOBIITI:Quay:DOES_NOT_EXIST";
    private static final String UNKNOWN_DATASET_STOP = "NAOLIBORG:Quay:NLBR2";
    private static final String REQUEST_MESSAGE_ID = "TU-MSG-1507";

    @Autowired
    private SiriHandler handler;

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    @BeforeEach
    void init() {
        serverSubscriptionManager.clearAllOutboundSubscriptions();
        initStopPlaceMapper();
    }

    @Test
    void valid_stop_reference_creates_subscription() throws UnmarshalException {
        Siri response = handler.handleIncomingSiri(createParameters(List.of(VALID_STOP)));

        Assertions.assertNotNull(response);
        Assertions.assertEquals(1, serverSubscriptionManager.getSubscriptions().size());
        List<ResponseStatus> statuses = assertSubscriptionResponse(response, 1);
        Assertions.assertTrue(statuses.getFirst().isStatus());
    }

    @Test
    void invalid_stop_reference_is_rejected_and_no_subscription_created() throws UnmarshalException {
        Siri response = handler.handleIncomingSiri(createParameters(List.of(UNKNOWN_STOP)));

        Assertions.assertTrue(serverSubscriptionManager.getSubscriptions().isEmpty(), "No subscription must be created");
        ResponseStatus status = assertInvalidDataReferencesResponse(response);
        Assertions.assertTrue(status.getErrorCondition().getDescription().getValue().contains(UNKNOWN_STOP));
    }

    @Test
    void mixed_valid_and_invalid_stop_references_rejects_only_invalid_subscription() throws UnmarshalException {
        Siri response = handler.handleIncomingSiri(createParameters(List.of(VALID_STOP, UNKNOWN_STOP)));

        Assertions.assertEquals(1, serverSubscriptionManager.getSubscriptions().size(), "Subscription on valid stop must be created");

        List<ResponseStatus> statuses = assertSubscriptionResponse(response, 2);

        ResponseStatus validStatus = statuses.getFirst();
        Assertions.assertTrue(validStatus.isStatus(), "Subscription on valid stop must be accepted");
        Assertions.assertNull(validStatus.getErrorCondition());

        ResponseStatus invalidStatus = assertInvalidDataReferencesStatus(statuses.get(1));
        Assertions.assertEquals("TU-SUB-REJ2", invalidStatus.getSubscriptionRef().getValue());
        String description = invalidStatus.getErrorCondition().getDescription().getValue();
        Assertions.assertTrue(description.contains(UNKNOWN_STOP));
        Assertions.assertFalse(description.contains(VALID_STOP), "Valid stop must not be listed as invalid");
        Assertions.assertFalse(description.contains("datasetId"), "No datasetId must be shown when no dataset is provided");
    }

    @Test
    void invalid_then_valid_stop_references_keep_request_order() throws UnmarshalException {
        Siri response = handler.handleIncomingSiri(createParameters(List.of(UNKNOWN_STOP, VALID_STOP)));

        Assertions.assertEquals(1, serverSubscriptionManager.getSubscriptions().size(), "Subscription on valid stop must be created");

        List<ResponseStatus> statuses = assertSubscriptionResponse(response, 2);

        ResponseStatus invalidStatus = assertInvalidDataReferencesStatus(statuses.getFirst());
        Assertions.assertEquals("TU-SUB-REJ1", invalidStatus.getSubscriptionRef().getValue());
        Assertions.assertTrue(invalidStatus.getErrorCondition().getDescription().getValue().contains(UNKNOWN_STOP));

        Assertions.assertTrue(statuses.get(1).isStatus(), "Subscription on valid stop must be accepted");
    }

    @Test
    void multiple_invalid_stop_references_are_all_rejected() throws UnmarshalException {
        Siri response = handler.handleIncomingSiri(createParameters(List.of(UNKNOWN_STOP, UNKNOWN_DATASET_STOP)));

        Assertions.assertTrue(serverSubscriptionManager.getSubscriptions().isEmpty(), "No subscription must be created");

        List<ResponseStatus> statuses = assertSubscriptionResponse(response, 2);
        Assertions.assertTrue(assertInvalidDataReferencesStatus(statuses.getFirst()).getErrorCondition().getDescription().getValue().contains(UNKNOWN_STOP));
        Assertions.assertTrue(assertInvalidDataReferencesStatus(statuses.get(1)).getErrorCondition().getDescription().getValue().contains(UNKNOWN_DATASET_STOP));
    }

    @Test
    void producer_stop_reference_with_unknown_dataset_is_rejected() throws UnmarshalException {
        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);
        Assertions.assertFalse(stopPlaceService.isKnownId(UNKNOWN_DATASET_STOP), "Precondition: stop must be unknown");

        Siri response = handler.handleIncomingSiri(createParameters(List.of(UNKNOWN_DATASET_STOP)));

        Assertions.assertTrue(serverSubscriptionManager.getSubscriptions().isEmpty(), "No subscription must be created");
        ResponseStatus status = assertInvalidDataReferencesResponse(response);
        Assertions.assertTrue(status.getErrorCondition().getDescription().getValue().contains(UNKNOWN_DATASET_STOP));
    }

    @Test
    void missing_monitoring_ref_is_rejected_without_exception() throws UnmarshalException {
        List<String> refs = new ArrayList<>();
        refs.add(null);
        Siri response = handler.handleIncomingSiri(createParameters(refs));

        Assertions.assertTrue(serverSubscriptionManager.getSubscriptions().isEmpty());
        assertInvalidDataReferencesResponse(response);
    }

    private ResponseStatus assertInvalidDataReferencesResponse(Siri response) {
        return assertInvalidDataReferencesStatus(assertSubscriptionResponse(response, 1).getFirst());
    }

    private List<ResponseStatus> assertSubscriptionResponse(Siri response, int expectedStatuses) {
        Assertions.assertNotNull(response);
        Assertions.assertEquals("2.0", response.getVersion(), "Response must carry the request SIRI version");

        SubscriptionResponseStructure subscriptionResponse = response.getSubscriptionResponse();
        Assertions.assertNotNull(subscriptionResponse);
        Assertions.assertEquals(REQUEST_MESSAGE_ID, subscriptionResponse.getRequestMessageRef().getValue());
        Assertions.assertEquals(expectedStatuses, subscriptionResponse.getResponseStatuses().size());
        subscriptionResponse.getResponseStatuses().forEach(status ->
                Assertions.assertEquals(REQUEST_MESSAGE_ID, status.getRequestMessageRef().getValue(), "Every status must reference the request message"));
        return subscriptionResponse.getResponseStatuses();
    }

    private ResponseStatus assertInvalidDataReferencesStatus(ResponseStatus status) {
        Assertions.assertFalse(status.isStatus());
        Assertions.assertEquals(REQUEST_MESSAGE_ID, status.getRequestMessageRef().getValue());
        Assertions.assertNotNull(status.getErrorCondition());
        Assertions.assertNotNull(status.getErrorCondition().getInvalidDataReferencesError());
        return status;
    }

    private IncomingSiriParameters createParameters(List<String> stopRefs) {
        StringBuilder smRequests = new StringBuilder();
        int i = 1;
        for (String stopRef : stopRefs) {
            String monitoringRef = stopRef == null ? "" :
                    "                <siri:MonitoringRef xmlns:siri=\"http://www.siri.org.uk/siri\">" + stopRef + "</siri:MonitoringRef>\n";
            smRequests.append("        <StopMonitoringSubscriptionRequest>\n")
                    .append("            <SubscriptionIdentifier>TU-SUB-REJ").append(i++).append("</SubscriptionIdentifier>\n")
                    .append("            <InitialTerminationTime>2099-10-18T08:02:57.701131Z</InitialTerminationTime>\n")
                    .append("            <StopMonitoringRequest>\n")
                    .append("                <siri:RequestTimestamp xmlns:siri=\"http://www.siri.org.uk/siri\">2022-10-17T08:02:57.699257Z</siri:RequestTimestamp>\n")
                    .append("                <siri:PreviewInterval xmlns:siri=\"http://www.siri.org.uk/siri\">PT3H</siri:PreviewInterval>\n")
                    .append(monitoringRef)
                    .append("            </StopMonitoringRequest>\n")
                    .append("        </StopMonitoringSubscriptionRequest>\n");
        }

        String stringXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" version=\"2.0\">\n" +
                "    <SubscriptionRequest>\n" +
                "        <RequestorRef>TU-REQ-REJ</RequestorRef>\n" +
                "        <MessageIdentifier>" + REQUEST_MESSAGE_ID + "</MessageIdentifier>\n" +
                // localhost consumer addresses are refused by ServerSubscriptionManager (isValidUrlWithoutLocalhost)
                "        <ConsumerAddress>http://consumer.example.test/incomingSiri</ConsumerAddress>\n" +
                "        <SubscriptionContext>\n" +
                "            <siri:HeartbeatInterval xmlns:siri=\"http://www.siri.org.uk/siri\">PT5M</siri:HeartbeatInterval>\n" +
                "        </SubscriptionContext>\n" +
                smRequests +
                "    </SubscriptionRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        IncomingSiriParameters incomingSiriParameters = new IncomingSiriParameters();
        incomingSiriParameters.setIncomingSiriStream(xml);
        incomingSiriParameters.setOutboundIdMappingPolicy(OutboundIdMappingPolicy.DEFAULT);
        incomingSiriParameters.setMaxSize(-1);
        incomingSiriParameters.setClientTrackingName("clientTrackingName");
        incomingSiriParameters.setSoapTransformation(false);
        incomingSiriParameters.setUseOriginalId(false);
        return incomingSiriParameters;
    }

    private void initStopPlaceMapper() {
        Map<String, Pair<String, String>> stopPlaceMap = new HashMap<>();
        stopPlaceMap.put(QUAY1_REF, Pair.of(VALID_STOP, "quay rej 1 name"));

        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);
        stopPlaceService.addStopPlaceMappings(stopPlaceMap);

        Map<String, Set<String>> stopPlaceReverseMap = new HashMap<>();
        stopPlaceReverseMap.put(VALID_STOP, new HashSet<>(Set.of(QUAY1_REF)));
        stopPlaceService.addStopPlaceReverseMappings(stopPlaceReverseMap);

        stopPlaceService.addStopQuays(new HashSet<>(Set.of(QUAY1_REF)));
    }
}
