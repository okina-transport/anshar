package no.rutebanken.anshar.routes.siri.helpers;

import uk.org.siri.siri21.AbstractSubscriptionStructure;
import uk.org.siri.siri21.EstimatedTimetableSubscriptionStructure;
import uk.org.siri.siri21.FacilityMonitoringSubscriptionStructure;
import uk.org.siri.siri21.SituationExchangeSubscriptionStructure;
import uk.org.siri.siri21.StopMonitoringRequestStructure;
import uk.org.siri.siri21.StopMonitoringSubscriptionStructure;
import uk.org.siri.siri21.SubscriptionRequest;
import uk.org.siri.siri21.VehicleMonitoringSubscriptionStructure;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Builds a compact, human-readable summary of an incoming SIRI SubscriptionRequest,
 * so that the whole payload does not need to be logged.
 */
public final class SubscriptionRequestLogFormatter {

    private SubscriptionRequestLogFormatter() {
    }

    public static String summarize(SubscriptionRequest request) {
        if (request == null) {
            return "SubscriptionRequest: null";
        }

        List<String> parts = new ArrayList<>();

        if (!request.getStopMonitoringSubscriptionRequests().isEmpty()) {
            parts.add(summarizeStopMonitoring(request));
        }
        if (!request.getEstimatedTimetableSubscriptionRequests().isEmpty()) {
            parts.add(summarizeGeneric("EstimatedTimetableSubscriptionRequest", request,
                    request.getEstimatedTimetableSubscriptionRequests(),
                    EstimatedTimetableSubscriptionStructure::isIncrementalUpdates));
        }
        if (!request.getVehicleMonitoringSubscriptionRequests().isEmpty()) {
            parts.add(summarizeGeneric("VehicleMonitoringSubscriptionRequest", request,
                    request.getVehicleMonitoringSubscriptionRequests(),
                    VehicleMonitoringSubscriptionStructure::isIncrementalUpdates));
        }
        if (!request.getSituationExchangeSubscriptionRequests().isEmpty()) {
            parts.add(summarizeGeneric("SituationExchangeSubscriptionRequest", request,
                    request.getSituationExchangeSubscriptionRequests(),
                    SituationExchangeSubscriptionStructure::isIncrementalUpdates));
        }
        if (!request.getGeneralMessageSubscriptionRequests().isEmpty()) {
            parts.add(summarizeGeneric("GeneralMessageSubscriptionRequest", request,
                    request.getGeneralMessageSubscriptionRequests(),
                    s -> null));
        }
        if (!request.getFacilityMonitoringSubscriptionRequests().isEmpty()) {
            parts.add(summarizeGeneric("FacilityMonitoringSubscriptionRequest", request,
                    request.getFacilityMonitoringSubscriptionRequests(),
                    FacilityMonitoringSubscriptionStructure::isIncrementalUpdates));
        }

        if (parts.isEmpty()) {
            return "SubscriptionRequest - timestamp: " + request.getRequestTimestamp()
                    + ", requestorRef: " + requestorRef(request) + ", no supported subscription";
        }
        return String.join(" | ", parts);
    }

    private static String summarizeStopMonitoring(SubscriptionRequest request) {
        List<StopMonitoringSubscriptionStructure> subscriptions = request.getStopMonitoringSubscriptionRequests();

        Set<String> stops = new LinkedHashSet<>();
        Set<String> previewIntervals = new LinkedHashSet<>();
        for (StopMonitoringSubscriptionStructure subscription : subscriptions) {
            StopMonitoringRequestStructure smRequest = subscription.getStopMonitoringRequest();
            if (smRequest == null) {
                continue;
            }
            if (smRequest.getMonitoringRef() != null) {
                stops.add(smRequest.getMonitoringRef().getValue());
            }
            previewIntervals.add(Objects.toString(smRequest.getPreviewInterval()));
        }

        return "StopMonitoringSubscriptionRequest"
                + " - timestamp: " + request.getRequestTimestamp()
                + ", requestorRef: " + requestorRef(request)
                + ", stops (" + stops.size() + "): " + String.join(", ", stops)
                + ", previewInterval: " + joinDistinct(previewIntervals)
                + ", incrementalUpdates: " + distinct(subscriptions, StopMonitoringSubscriptionStructure::isIncrementalUpdates);
    }

    private static <T extends AbstractSubscriptionStructure> String summarizeGeneric(String type,
                                                                                     SubscriptionRequest request,
                                                                                     List<T> subscriptions,
                                                                                     Function<T, Boolean> incrementalUpdates) {
        return type
                + " - timestamp: " + request.getRequestTimestamp()
                + ", requestorRef: " + requestorRef(request)
                + ", subscriptions: " + subscriptions.size()
                + ", incrementalUpdates: " + distinct(subscriptions, incrementalUpdates);
    }

    private static <T> String distinct(List<T> subscriptions, Function<T, ?> extractor) {
        Set<String> values = new LinkedHashSet<>();
        for (T subscription : subscriptions) {
            values.add(Objects.toString(extractor.apply(subscription)));
        }
        return joinDistinct(values);
    }

    private static String joinDistinct(Set<String> values) {
        if (values.isEmpty()) {
            return "null";
        }
        return values.size() == 1 ? values.iterator().next() : values.toString();
    }

    private static String requestorRef(SubscriptionRequest request) {
        return request.getRequestorRef() != null ? request.getRequestorRef().getValue() : null;
    }
}
