package no.rutebanken.anshar.routes.siri.helpers;

import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.EstimatedTimetableSubscriptionStructure;
import uk.org.siri.siri21.MonitoringRefStructure;
import uk.org.siri.siri21.RequestorRef;
import uk.org.siri.siri21.StopMonitoringRequestStructure;
import uk.org.siri.siri21.StopMonitoringSubscriptionStructure;
import uk.org.siri.siri21.SubscriptionRequest;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionRequestLogFormatterTest {

    private static final ZonedDateTime TIMESTAMP = ZonedDateTime.parse("2026-11-25T10:00:00+01:00");

    @Test
    void nullRequest() {
        assertEquals("SubscriptionRequest: null", SubscriptionRequestLogFormatter.summarize(null));
    }

    @Test
    void stopMonitoringWithSameValuesOnAllStops() {
        SubscriptionRequest request = createRequest();
        for (String stop : new String[]{"stop1", "stop2", "stop3", "stop4"}) {
            request.getStopMonitoringSubscriptionRequests().add(createSmSubscription(stop, null, false));
        }

        assertEquals("StopMonitoringSubscriptionRequest - timestamp: " + TIMESTAMP
                        + ", requestorRef: client1, stops (4): stop1, stop2, stop3, stop4"
                        + ", previewInterval: null, incrementalUpdates: false",
                SubscriptionRequestLogFormatter.summarize(request));
    }

    @Test
    void stopMonitoringWithDifferentValuesListsDistinctValues() throws Exception {
        Duration oneHour = DatatypeFactory.newInstance().newDuration("PT1H");
        SubscriptionRequest request = createRequest();
        request.getStopMonitoringSubscriptionRequests().add(createSmSubscription("stop1", oneHour, true));
        request.getStopMonitoringSubscriptionRequests().add(createSmSubscription("stop2", null, false));
        request.getStopMonitoringSubscriptionRequests().add(createSmSubscription("stop1", oneHour, true));

        String summary = SubscriptionRequestLogFormatter.summarize(request);

        assertTrue(summary.contains("stops (2): stop1, stop2"), summary);
        assertTrue(summary.contains("previewInterval: [PT1H, null]"), summary);
        assertTrue(summary.contains("incrementalUpdates: [true, false]"), summary);
    }

    @Test
    void stopMonitoringWithoutRequestIsSkipped() {
        SubscriptionRequest request = createRequest();
        request.getStopMonitoringSubscriptionRequests().add(new StopMonitoringSubscriptionStructure());

        String summary = SubscriptionRequestLogFormatter.summarize(request);

        assertTrue(summary.contains("stops (0): "), summary);
        assertTrue(summary.contains("previewInterval: null"), summary);
    }

    @Test
    void estimatedTimetableSummary() {
        SubscriptionRequest request = createRequest();
        EstimatedTimetableSubscriptionStructure et = new EstimatedTimetableSubscriptionStructure();
        et.setIncrementalUpdates(true);
        request.getEstimatedTimetableSubscriptionRequests().add(et);
        request.getEstimatedTimetableSubscriptionRequests().add(et);

        assertEquals("EstimatedTimetableSubscriptionRequest - timestamp: " + TIMESTAMP
                        + ", requestorRef: client1, subscriptions: 2, incrementalUpdates: true",
                SubscriptionRequestLogFormatter.summarize(request));
    }

    @Test
    void emptyRequestWithoutRequestorRef() {
        SubscriptionRequest request = new SubscriptionRequest();
        request.setRequestTimestamp(TIMESTAMP);

        String summary = SubscriptionRequestLogFormatter.summarize(request);

        assertEquals("SubscriptionRequest - timestamp: " + TIMESTAMP
                + ", requestorRef: null, no supported subscription", summary);
        assertFalse(summary.contains("StopMonitoring"));
    }

    private static SubscriptionRequest createRequest() {
        SubscriptionRequest request = new SubscriptionRequest();
        request.setRequestTimestamp(TIMESTAMP);
        RequestorRef requestorRef = new RequestorRef();
        requestorRef.setValue("client1");
        request.setRequestorRef(requestorRef);
        return request;
    }

    private static StopMonitoringSubscriptionStructure createSmSubscription(String stop, Duration previewInterval, Boolean incrementalUpdates) {
        MonitoringRefStructure monitoringRef = new MonitoringRefStructure();
        monitoringRef.setValue(stop);

        StopMonitoringRequestStructure smRequest = new StopMonitoringRequestStructure();
        smRequest.setMonitoringRef(monitoringRef);
        smRequest.setPreviewInterval(previewInterval);

        StopMonitoringSubscriptionStructure subscription = new StopMonitoringSubscriptionStructure();
        subscription.setStopMonitoringRequest(smRequest);
        subscription.setIncrementalUpdates(incrementalUpdates);
        return subscription;
    }
}
