package no.rutebanken.anshar.routes.siri.helpers;

import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.ErrorDescriptionStructure;
import uk.org.siri.siri21.InvalidDataReferencesErrorStructure;
import uk.org.siri.siri21.MessageRefStructure;
import uk.org.siri.siri21.RequestorRef;
import uk.org.siri.siri21.ResponseStatus;
import uk.org.siri.siri21.ServiceDeliveryErrorConditionElement;
import uk.org.siri.siri21.ServiceNotAvailableErrorStructure;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SubscriptionRefStructure;
import uk.org.siri.siri21.SubscriptionResponseStructure;
import uk.org.siri.siri21.TerminateSubscriptionResponseStructure;
import uk.org.siri.siri21.TerminationResponseStatusStructure;
import uk.org.siri.siri21.UnknownSubscriptionErrorStructure;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubscriptionResponseLogFormatterTest {

    private static final ZonedDateTime TIMESTAMP = ZonedDateTime.parse("2026-11-25T10:00:00+01:00");
    private static final String HEADER = " - timestamp: " + TIMESTAMP + ", responderRef: anshar, requestMessageRef: msg1";

    @Test
    void nullResponse() {
        assertEquals("SubscriptionResponse: null", SubscriptionResponseLogFormatter.summarize(null));
    }

    @Test
    void responseWithoutSubscriptionContent() {
        assertEquals("Siri response without SubscriptionResponse or TerminateSubscriptionResponse",
                SubscriptionResponseLogFormatter.summarize(new Siri()));
    }

    @Test
    void allSubscriptionsAccepted() {
        SubscriptionResponseStructure response = createSubscriptionResponse();
        response.getResponseStatuses().add(createStatus("sub1", true, null));
        response.getResponseStatuses().add(createStatus("sub2", true, null));

        assertEquals("SubscriptionResponse" + HEADER + ", statuses: 2 (ok: 2, failed: 0)",
                SubscriptionResponseLogFormatter.summarize(wrap(response)));
    }

    @Test
    void rejectedSubscriptionUsesDescriptionFirst() {
        ServiceDeliveryErrorConditionElement error = new ServiceDeliveryErrorConditionElement();
        InvalidDataReferencesErrorStructure invalidData = new InvalidDataReferencesErrorStructure();
        invalidData.setErrorText("InvalidDataReferencesError");
        error.setInvalidDataReferencesError(invalidData);
        ErrorDescriptionStructure description = new ErrorDescriptionStructure();
        description.setValue("Unknown stop: stop3");
        error.setDescription(description);

        SubscriptionResponseStructure response = createSubscriptionResponse();
        response.getResponseStatuses().add(createStatus("sub1", true, null));
        response.getResponseStatuses().add(createStatus("sub3", false, error));

        assertEquals("SubscriptionResponse" + HEADER + ", statuses: 2 (ok: 1, failed: 1)"
                        + ", failures: [sub3: Unknown stop: stop3]",
                SubscriptionResponseLogFormatter.summarize(wrap(response)));
    }

    @Test
    void rejectedSubscriptionFallsBackToErrorText() {
        // Same shape as SiriHandler.generateServerIsIntializingError: no subscriptionRef, no description
        ServiceDeliveryErrorConditionElement error = new ServiceDeliveryErrorConditionElement();
        ServiceNotAvailableErrorStructure notAvailable = new ServiceNotAvailableErrorStructure();
        notAvailable.setErrorText("Server is initializing");
        error.setServiceNotAvailableError(notAvailable);

        SubscriptionResponseStructure response = createSubscriptionResponse();
        response.getResponseStatuses().add(createStatus(null, false, error));

        assertEquals("SubscriptionResponse" + HEADER + ", statuses: 1 (ok: 0, failed: 1)"
                        + ", failures: [null: Server is initializing]",
                SubscriptionResponseLogFormatter.summarize(wrap(response)));
    }

    @Test
    void statusWithoutErrorConditionOrStatusFlag() {
        SubscriptionResponseStructure response = createSubscriptionResponse();
        response.getResponseStatuses().add(createStatus("sub1", null, null));

        assertEquals("SubscriptionResponse" + HEADER + ", statuses: 1 (ok: 0, failed: 1)"
                        + ", failures: [sub1: no error condition]",
                SubscriptionResponseLogFormatter.summarize(wrap(response)));
    }

    @Test
    void terminateSubscriptionResponse() {
        TerminateSubscriptionResponseStructure response = new TerminateSubscriptionResponseStructure();
        response.setResponseTimestamp(TIMESTAMP);
        response.setResponderRef(requestorRef("anshar"));
        response.setRequestMessageRef(messageRef("msg1"));

        TerminationResponseStatusStructure ok = new TerminationResponseStatusStructure();
        ok.setSubscriptionRef(subscriptionRef("sub1"));
        ok.setStatus(true);
        response.getTerminationResponseStatuses().add(ok);

        TerminationResponseStatusStructure.ErrorCondition error = new TerminationResponseStatusStructure.ErrorCondition();
        error.setUnknownSubscriptionError(new UnknownSubscriptionErrorStructure());
        TerminationResponseStatusStructure ko = new TerminationResponseStatusStructure();
        ko.setSubscriptionRef(subscriptionRef("sub2"));
        ko.setStatus(false);
        ko.setErrorCondition(error);
        response.getTerminationResponseStatuses().add(ko);

        Siri siri = new Siri();
        siri.setTerminateSubscriptionResponse(response);

        assertEquals("TerminateSubscriptionResponse" + HEADER + ", statuses: 2 (ok: 1, failed: 1)"
                        + ", failures: [sub2: UnknownSubscriptionErrorStructure]",
                SubscriptionResponseLogFormatter.summarize(siri));
    }

    private static Siri wrap(SubscriptionResponseStructure response) {
        Siri siri = new Siri();
        siri.setSubscriptionResponse(response);
        return siri;
    }

    private static SubscriptionResponseStructure createSubscriptionResponse() {
        SubscriptionResponseStructure response = new SubscriptionResponseStructure();
        response.setResponseTimestamp(TIMESTAMP);
        response.setResponderRef(requestorRef("anshar"));
        response.setRequestMessageRef(messageRef("msg1"));
        return response;
    }

    private static ResponseStatus createStatus(String subscriptionId, Boolean status, ServiceDeliveryErrorConditionElement error) {
        ResponseStatus responseStatus = new ResponseStatus();
        if (subscriptionId != null) {
            responseStatus.setSubscriptionRef(subscriptionRef(subscriptionId));
        }
        responseStatus.setStatus(status);
        responseStatus.setErrorCondition(error);
        return responseStatus;
    }

    private static SubscriptionRefStructure subscriptionRef(String value) {
        SubscriptionRefStructure ref = new SubscriptionRefStructure();
        ref.setValue(value);
        return ref;
    }

    private static RequestorRef requestorRef(String value) {
        RequestorRef ref = new RequestorRef();
        ref.setValue(value);
        return ref;
    }

    private static MessageRefStructure messageRef(String value) {
        MessageRefStructure ref = new MessageRefStructure();
        ref.setValue(value);
        return ref;
    }
}
