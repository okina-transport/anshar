package no.rutebanken.anshar.routes.siri.lite;

import io.micrometer.core.instrument.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.MonitoredStopVisits;
import no.rutebanken.anshar.data.util.CustomStringUtils;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.mapping.OutputExternalIdsService;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import no.rutebanken.anshar.util.IDUtils;
import no.rutebanken.anshar.util.SiriUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;

import java.util.*;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.HttpParameter.*;

@Service
public class SiriLiteStopMonitoringRoute extends RestRouteBuilder {


    @Autowired
    private AnsharConfiguration configuration;


    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private MonitoredStopVisits monitoredStopVisits;

    @Autowired
    StopPlaceUpdaterService stopPlaceUpdaterService;

    @Autowired
    OutputExternalIdsService outputExternalIdsService;


    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:internal.anshar.rest.sm")
                .log("RequestTracer - Incoming request (SM)")
                .to("log:restRequest:" + getClass().getSimpleName() + "?showAll=false&showHeaders=true")
                .choice()
                .when(e -> isTrackingHeaderAcceptable(e))
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    String datasetId = p.getIn().getHeader(PARAM_DATASET_ID, String.class);
                    String originalId = p.getIn().getHeader(PARAM_USE_ORIGINAL_ID, String.class);
                    String altId = p.getIn().getHeader(PARAM_USE_ALT_ID, String.class);
                    String maxSizeStr = p.getIn().getHeader(PARAM_MAX_SIZE, String.class);
                    String stopRef = p.getIn().getHeader(PARAM_STOP_REF, String.class);
                    String lineRef = p.getIn().getHeader(PARAM_LINE_REF, String.class);
                    String etClientName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);
                    String previewIntervalMinutesStr = p.getIn().getHeader(PARAM_PREVIEW_INTERVAL, String.class);
                    List<String> excludedIdList = getParameterValuesAsList(p.getIn(), PARAM_EXCLUDED_DATASET_ID);
                    String messageId = p.getIn().getMessageId();

                    String requestorId = resolveRequestorId(p.getIn().getBody(HttpServletRequest.class));

                    int maxSize = datasetId != null ? Integer.MAX_VALUE : configuration.getDefaultMaxSize();
                    if (maxSizeStr != null) {
                        try {
                            maxSize = Integer.parseInt(maxSizeStr);
                        } catch (NumberFormatException nfe) {
                            //ignore
                        }
                    }
                    long previewIntervalMillis = -1;
                    if (previewIntervalMinutesStr != null) {
                        int minutes = Integer.parseInt(previewIntervalMinutesStr);
                        previewIntervalMillis = (long) minutes * 60 * 1000;
                    }


                    Siri response;

                    Set<String> searchedStopIds = new HashSet<>();
                    if (StringUtils.isNotEmpty(stopRef)) {
                        searchedStopIds.add(stopRef);
                    }

                    Set<String> searchedLineIds = new HashSet<>();
                    if (StringUtils.isNotEmpty(lineRef)) {
                        searchedLineIds.add(lineRef);
                    }

                    Set<String> datasets = SiriUtils.generateDatasetListFromHeader(datasetId);
                    OutboundIdMappingPolicy outboundIdMappingPolicy = SiriHandler.getIdMappingPolicy(originalId, altId);
                    response = handleStopMonitoringMultipleDatasetRequest(outboundIdMappingPolicy, requestorId, datasets, etClientName, excludedIdList, maxSize, previewIntervalMillis, searchedStopIds, searchedLineIds, originalId, altId, messageId);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                    streamOutput(p, response, out);
                })
                .log("RequestTracer - Request done (SM)")
                .otherwise()
                .to("direct:anshar.invalid.tracking.header.response")
                .routeId("incoming.rest.sm")
        ;

    }

    private String resolveRequestorId(HttpServletRequest request) {
        return request.getParameter("requestorId");
    }

    private Siri handleStopMonitoringMultipleDatasetRequest(OutboundIdMappingPolicy outboundIdMappingPolicy, String requestorId, Set<String> datasets, String etClientName, List<String> excludedIdList, int maxSize, long previewIntervalMillis, Set<String> searchedStopIds, Set<String> searchedLineIds, String originalId, String altId, String messageId) {

        if (datasets.isEmpty()) {
            return handleStopMonitoringSingleDatasetRequest(outboundIdMappingPolicy, requestorId, null, etClientName, excludedIdList, maxSize, previewIntervalMillis, searchedStopIds, searchedLineIds, originalId, altId, messageId);
        }

        Siri globalResults = null;

        for (String dataset : datasets) {
            Siri datasetResult = handleStopMonitoringSingleDatasetRequest(outboundIdMappingPolicy, requestorId, dataset, etClientName, excludedIdList, maxSize, previewIntervalMillis, searchedStopIds, searchedLineIds, originalId, altId, messageId);
            globalResults = SiriUtils.mergeSiris(globalResults, datasetResult);
        }
        return globalResults;
    }

    private Siri handleStopMonitoringSingleDatasetRequest(OutboundIdMappingPolicy outboundIdMappingPolicy, String requestorId, String datasetId, String etClientName, List<String> excludedIdList, int maxSize, long previewIntervalMillis, Set<String> searchedStopIds, Set<String> searchedLineIds, String originalId, String altId, String messageId) {
        Siri response;

        Set<String> importedIds = getImportedIds(outboundIdMappingPolicy, searchedStopIds, datasetId);

        Map<ObjectType, Optional<IdProcessingParameters>> idMap = subscriptionConfig.buildIdProcessingParams(datasetId, importedIds, ObjectType.STOP);
        Set<String> revertedMonitoringRefs = IDUtils.revertMonitoringRefs(importedIds, idMap.get(ObjectType.STOP));
        revertedMonitoringRefs = revertedMonitoringRefs.stream().map(CustomStringUtils::revertChouetteIdTransformation).collect(Collectors.toSet());


        if (!revertedMonitoringRefs.isEmpty()) {
            response = monitoredStopVisits.createServiceDelivery(requestorId, datasetId, excludedIdList, maxSize, previewIntervalMillis, revertedMonitoringRefs, searchedLineIds, messageId, false);
        } else {
            response = monitoredStopVisits.createServiceDelivery(requestorId, datasetId, excludedIdList, maxSize, previewIntervalMillis, searchedStopIds, searchedLineIds, messageId, false);
        }

        List<ValueAdapter> outboundAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.STOP_MONITORING, SiriHandler.getIdMappingPolicy(originalId, altId), subscriptionConfig.buildIdProcessingParamsFromDataset(datasetId));
        if ("test".equals(originalId)) {
            outboundAdapters = null;
        }
        return SiriValueTransformer.transform(response, outboundAdapters, false, false);

    }

    public Set<String> getImportedIds(OutboundIdMappingPolicy outboundIdMappingPolicy, Set<String> originalMonitoringRefs, String datasetId) {
        Set<String> importedIds;
        if (OutboundIdMappingPolicy.DEFAULT.equals(outboundIdMappingPolicy)) {
            importedIds = convertToImportedIds(originalMonitoringRefs, datasetId);
        } else if (OutboundIdMappingPolicy.ALT_ID.equals(outboundIdMappingPolicy)) {
            importedIds = convertFromAltIdsToImportedIdsStop(originalMonitoringRefs, datasetId);
        } else if (OutboundIdMappingPolicy.ORIGINAL_ID.equals(outboundIdMappingPolicy) && StringUtils.isEmpty(datasetId)) {
            importedIds = new HashSet<>();
        } else {
            importedIds = originalMonitoringRefs;
        }

        return importedIds;
    }

    public Set<String> convertToImportedIds(Set<String> originalMonitoringRefs, String datasetId) {
        Set<String> importedIds = new HashSet<>();
        for (String originalMonitoringRef : originalMonitoringRefs) {
            if (StringUtils.isNotEmpty(datasetId) && StringUtils.isNotEmpty(originalMonitoringRef) && stopPlaceUpdaterService.canBeReverted(originalMonitoringRef, datasetId)) {
                importedIds.addAll(stopPlaceUpdaterService.getReverse(originalMonitoringRef, datasetId));
            } else if (StringUtils.isEmpty(datasetId) && stopPlaceUpdaterService.canBeRevertedWithoutDatasetId(originalMonitoringRef)) {
                importedIds.addAll(stopPlaceUpdaterService.getReverseWithoutDatasetId(originalMonitoringRef));
            } else {
                return new HashSet<>();
            }
        }

        return importedIds;
    }


    public Set<String> convertFromAltIdsToImportedIdsStop(Set<String> originalMonitoringRefs, String datasetId) {
        Set<String> importedIds = new HashSet<>();
        for (String originalMonitoringRef : originalMonitoringRefs) {
            if (StringUtils.isNotEmpty(datasetId) && StringUtils.isNotEmpty(originalMonitoringRef) && !outputExternalIdsService.getReverseAltIdStop(datasetId, originalMonitoringRef).isEmpty()) {
                importedIds.addAll(outputExternalIdsService.getReverseAltIdStop(datasetId, originalMonitoringRef));
            }
        }
        return importedIds;
    }

}
