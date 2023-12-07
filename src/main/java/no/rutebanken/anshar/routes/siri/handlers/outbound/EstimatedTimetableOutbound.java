package no.rutebanken.anshar.routes.siri.handlers.outbound;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import no.rutebanken.anshar.util.IDUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.EstimatedTimetableRequestStructure;
import uk.org.siri.siri20.LineDirectionStructure;
import uk.org.siri.siri20.ServiceRequest;
import uk.org.siri.siri20.Siri;

import javax.xml.datatype.Duration;
import java.util.*;

@Service
public class EstimatedTimetableOutbound {

    @Autowired
    SubscriptionConfig subscriptionConfig;

    @Autowired
    EstimatedTimetables estimatedTimetables;


    public Siri getEstimatedTimetableServiceDelivery(ServiceRequest serviceRequest, String datasetId, List<String> excludedDatasetIdList, int maxSize, String clientTrackingName, String requestorRef) {
        Duration previewInterval = serviceRequest.getEstimatedTimetableRequests().get(0).getPreviewInterval();
        long previewIntervalInMillis = -1;

        if (previewInterval != null) {
            previewIntervalInMillis = previewInterval.getTimeInMillis(new Date());
        }

        Set<String> requestedLines = getRequestedLinesFromServiceRequest(datasetId, serviceRequest);

        Map<ObjectType, Optional<IdProcessingParameters>> idMap = subscriptionConfig.buildIdProcessingParams(datasetId, requestedLines, ObjectType.LINE);
        Set<String> revertedMonitoringRefs = IDUtils.revertMonitoringRefs(requestedLines, idMap.get(ObjectType.LINE));

        return estimatedTimetables.createServiceDelivery(requestorRef, datasetId, clientTrackingName, excludedDatasetIdList, maxSize, previewIntervalInMillis, revertedMonitoringRefs);
    }


    /**
     * Read service request from user and build a set of lines that must be requested.
     * Lines format is raw (without any prefix or suffix)
     *
     * @param datasetId      dataset on which request is made
     * @param serviceRequest service request from user
     * @return the set of lines that must be requested
     */
    private Set<String> getRequestedLinesFromServiceRequest(String datasetId, ServiceRequest serviceRequest) {

        EstimatedTimetableRequestStructure estimatedTTReq = serviceRequest.getEstimatedTimetableRequests().get(0);
        Set<String> requestedLines = new HashSet<>();

        Optional<IdProcessingParameters> idParamsOpt = subscriptionConfig.getIdParametersForDataset(datasetId, ObjectType.LINE);

        if (estimatedTTReq.getLines() != null && estimatedTTReq.getLines().getLineDirections() != null
                && estimatedTTReq.getLines().getLineDirections().size() > 0) {


            for (LineDirectionStructure lineDirection : estimatedTTReq.getLines().getLineDirections()) {
                if (lineDirection.getLineRef() != null) {

                    String rawLineRef = lineDirection.getLineRef().getValue();

                    if (idParamsOpt.isPresent()) {
                        IdProcessingParameters idParam = idParamsOpt.get();
                        String prefix = idParam.getOutputPrefixToAdd();
                        if (StringUtils.isNotEmpty(prefix) && rawLineRef.startsWith(prefix)) {
                            rawLineRef = rawLineRef.substring(prefix.length());
                        }

                        String suffix = idParam.getOutputSuffixToAdd();
                        if (StringUtils.isNotEmpty(suffix) && rawLineRef.startsWith(suffix)) {
                            rawLineRef = rawLineRef.substring(0, rawLineRef.length() - suffix.length());
                        }
                    }
                    requestedLines.add(rawLineRef);
                }
            }
        }

        return requestedLines;

    }

    public List<ValueAdapter> getValueAdapters(String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy) {
        Map<ObjectType, Optional<IdProcessingParameters>> idMap = subscriptionConfig.buildIdProcessingParamsFromDataset(datasetId);
        return MappingAdapterPresets.getOutboundAdapters(SiriDataType.ESTIMATED_TIMETABLE, outboundIdMappingPolicy, idMap);
    }
}
