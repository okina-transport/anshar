package no.rutebanken.anshar.routes.siri.handlers.outbound;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.ServiceRequest;
import uk.org.siri.siri20.Siri;

import javax.xml.datatype.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EstimatedTimetableOutbound {

    @Autowired
    SubscriptionConfig subscriptionConfig;

    @Autowired
    EstimatedTimetables estimatedTimetables;


    public Siri getEstimatedTimetableServiceDelivery(String datasetId, List<String> excludedDatasetIdList, int maxSize, String clientTrackingName, ServiceRequest serviceRequest, String requestorRef) {
        Duration previewInterval = serviceRequest.getEstimatedTimetableRequests().get(0).getPreviewInterval();
        long previewIntervalInMillis = -1;

        if (previewInterval != null) {
            previewIntervalInMillis = previewInterval.getTimeInMillis(new Date());
        }

        return estimatedTimetables.createServiceDelivery(requestorRef, datasetId, clientTrackingName, excludedDatasetIdList, maxSize, previewIntervalInMillis);
    }

    public List<ValueAdapter> getValueAdapters(String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy){
        Map<ObjectType, Optional<IdProcessingParameters>> idMap = subscriptionConfig.buildIdProcessingParamsFromDataset(datasetId);
        return MappingAdapterPresets.getOutboundAdapters(SiriDataType.ESTIMATED_TIMETABLE, outboundIdMappingPolicy, idMap);
    }
}
