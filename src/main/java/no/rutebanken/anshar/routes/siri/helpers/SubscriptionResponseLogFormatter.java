package no.rutebanken.anshar.routes.siri.helpers;

import uk.org.siri.siri21.ErrorCodeStructure;
import uk.org.siri.siri21.ErrorDescriptionStructure;
import uk.org.siri.siri21.ResponseEndpointStructure;
import uk.org.siri.siri21.ResponseStatus;
import uk.org.siri.siri21.ServiceDeliveryErrorConditionElement;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SubscriptionRefStructure;
import uk.org.siri.siri21.SubscriptionResponseStructure;
import uk.org.siri.siri21.TerminateSubscriptionResponseStructure;
import uk.org.siri.siri21.TerminationResponseStatusStructure;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a compact, human-readable summary of an outgoing SIRI SubscriptionResponse
 * (or TerminateSubscriptionResponse), so that the whole payload does not need to be logged.
 */
public final class SubscriptionResponseLogFormatter {

    private SubscriptionResponseLogFormatter() {
    }

    public static String summarize(Siri response) {
        if (response == null) {
            return "SubscriptionResponse: null";
        }
        if (response.getSubscriptionResponse() != null) {
            return summarizeSubscriptionResponse(response.getSubscriptionResponse());
        }
        if (response.getTerminateSubscriptionResponse() != null) {
            return summarizeTerminateSubscriptionResponse(response.getTerminateSubscriptionResponse());
        }
        return "Siri response without SubscriptionResponse or TerminateSubscriptionResponse";
    }

    private static String summarizeSubscriptionResponse(SubscriptionResponseStructure response) {
        List<ResponseStatus> statuses = response.getResponseStatuses();
        List<String> failed = new ArrayList<>();
        for (ResponseStatus status : statuses) {
            if (!Boolean.TRUE.equals(status.isStatus())) {
                failed.add(subscriptionRef(status.getSubscriptionRef()) + ": " + errorText(status.getErrorCondition()));
            }
        }
        return "SubscriptionResponse" + header(response) + statusCounts(statuses.size(), failed);
    }

    private static String summarizeTerminateSubscriptionResponse(TerminateSubscriptionResponseStructure response) {
        List<TerminationResponseStatusStructure> statuses = response.getTerminationResponseStatuses();
        List<String> failed = new ArrayList<>();
        for (TerminationResponseStatusStructure status : statuses) {
            if (!Boolean.TRUE.equals(status.isStatus())) {
                failed.add(subscriptionRef(status.getSubscriptionRef()) + ": " + errorText(status.getErrorCondition()));
            }
        }
        return "TerminateSubscriptionResponse" + header(response) + statusCounts(statuses.size(), failed);
    }

    private static String header(ResponseEndpointStructure response) {
        return " - timestamp: " + response.getResponseTimestamp()
                + ", responderRef: " + (response.getResponderRef() != null ? response.getResponderRef().getValue() : null)
                + ", requestMessageRef: " + (response.getRequestMessageRef() != null ? response.getRequestMessageRef().getValue() : null);
    }

    private static String statusCounts(int total, List<String> failed) {
        String result = ", statuses: " + total + " (ok: " + (total - failed.size()) + ", failed: " + failed.size() + ")";
        if (!failed.isEmpty()) {
            result += ", failures: " + failed;
        }
        return result;
    }

    private static String subscriptionRef(SubscriptionRefStructure ref) {
        return ref != null ? ref.getValue() : "null";
    }

    private static String errorText(ServiceDeliveryErrorConditionElement error) {
        if (error == null) {
            return "no error condition";
        }
        return errorText(error.getDescription(),
                error.getInvalidDataReferencesError(), error.getServiceNotAvailableError(),
                error.getCapabilityNotSupportedError(), error.getAccessNotAllowedError(),
                error.getParametersIgnoredError(), error.getNoInfoForTopicError(),
                error.getBeyondDataHorizon(), error.getAllowedResourceUsageExceededError(),
                error.getUnknownExtensionsError(), error.getEndpointDeniedAccessError(),
                error.getEndpointNotAvailableAccessError(), error.getUnknownEndpointError(),
                error.getUnknownParticipantError(), error.getUnapprovedKeyAccessError(),
                error.getOtherError());
    }

    private static String errorText(TerminationResponseStatusStructure.ErrorCondition error) {
        if (error == null) {
            return "no error condition";
        }
        return errorText(error.getDescription(),
                error.getUnknownSubscriptionError(), error.getUnknownSubscriberError(),
                error.getCapabilityNotSupportedError(), error.getOtherError());
    }

    /**
     * Prefers the free-text description, then the text of the first error set.
     */
    private static String errorText(ErrorDescriptionStructure description, ErrorCodeStructure... errors) {
        if (description != null && description.getValue() != null) {
            return description.getValue();
        }
        for (ErrorCodeStructure error : errors) {
            if (error != null) {
                return error.getErrorText() != null ? error.getErrorText() : error.getClass().getSimpleName();
            }
        }
        return "unspecified error";
    }
}
