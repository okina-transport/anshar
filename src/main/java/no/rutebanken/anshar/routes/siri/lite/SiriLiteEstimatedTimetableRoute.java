package no.rutebanken.anshar.routes.siri.lite;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import no.rutebanken.anshar.util.SiriUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;

import java.util.*;

import static no.rutebanken.anshar.routes.HttpParameter.*;

@Service
public class SiriLiteEstimatedTimetableRoute extends RestRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(SiriLiteEstimatedTimetableRoute.class);

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private PrometheusMetricsService metrics;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private SubscriptionConfig subscriptionConfig;


    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:internal.anshar.rest.et")
                .log("RequestTracer - Incoming request (ET)")
                .to("log:restRequest:" + getClass().getSimpleName() + "?showAll=false&showHeaders=true")
                .choice()
                .when(e -> isTrackingHeaderAcceptable(e))
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    String datasetId = p.getIn().getHeader(PARAM_DATASET_ID, String.class);
                    String originalId = p.getIn().getHeader(PARAM_USE_ORIGINAL_ID, String.class);
                    String altId = p.getIn().getHeader(PARAM_USE_ALT_ID, String.class);
                    String maxSizeStr = p.getIn().getHeader(PARAM_MAX_SIZE, String.class);
                    String lineRef = p.getIn().getHeader(PARAM_LINE_REF, String.class);
                    String etClientName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);
                    String previewIntervalMinutesStr = p.getIn().getHeader(PARAM_PREVIEW_INTERVAL, String.class);
                    List<String> excludedIdList = getParameterValuesAsList(p.getIn(), PARAM_EXCLUDED_DATASET_ID);

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
                    Set<String> requestedLines = new HashSet<>();
                    if (lineRef != null) {
                        requestedLines.add(lineRef);
                    }

                    Set<String> datasets = SiriUtils.generateDatasetListFromHeader(datasetId);
                    response = handleEstimatedTimetableMultipleDatasetRequest(requestorId, datasets, etClientName, excludedIdList, maxSize, previewIntervalMillis, requestedLines, originalId, altId);

                    metrics.countOutgoingData(response, SubscriptionSetup.SubscriptionMode.LITE);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                    streamOutput(p, response, out);
                })
                .log("RequestTracer - Request done (ET)")
                .otherwise()
                .to("direct:anshar.invalid.tracking.header.response")
                .routeId("incoming.rest.et")
        ;

        from("direct:internal.anshar.rest.et.monitored")
                .log("RequestTracer - Incoming request (ET)")
                .to("log:restRequest:" + getClass().getSimpleName() + "?showAll=false&showHeaders=true")
                .choice()
                .when(e -> isTrackingHeaderAcceptable(e))
                .process(p -> {


                    logger.info("Fetching monitored ET-data");
                    Siri response = siriObjectFactory.createETServiceDelivery(estimatedTimetables.getAllMonitored());

                    List<ValueAdapter> outboundAdapters = MappingAdapterPresets.getOutboundAdapters(
                            SiriDataType.ESTIMATED_TIMETABLE,
                            OutboundIdMappingPolicy.DEFAULT
                    );

                    logger.info("Transforming monitored ET-data");
                    response = SiriValueTransformer.transform(response, outboundAdapters, false, true);

                    metrics.countOutgoingData(response, SubscriptionSetup.SubscriptionMode.LITE);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);
                    logger.info("Streaming monitored ET-data");
                    streamOutput(p, response, out);
                    logger.info("Done processing monitored ET-data");
                })
                .log("RequestTracer - Request done (ET)")
                .otherwise()
                .to("direct:anshar.invalid.tracking.header.response")
                .routeId("incoming.rest.et.monitored")
        ;

        from("direct:internal.anshar.rest.et.cached")
                .log("RequestTracer - Incoming request (ET)")
                .to("log:restRequest:" + getClass().getSimpleName() + "?showAll=false&showHeaders=true")
                .choice()
                .when(e -> isTrackingHeaderAcceptable(e))
                .process(p -> {
                    String requestorId = resolveRequestorId(p.getIn().getBody(HttpServletRequest.class));
                    String datasetId = p.getIn().getHeader(PARAM_DATASET_ID, String.class);
                    Integer maxSize = p.getIn().getHeader(PARAM_MAX_SIZE, Integer.class);
                    String clientTrackingName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);

                    logger.info("Fetching cached ET-data");
                    Siri response = siriObjectFactory.createETServiceDelivery(estimatedTimetables.getAllCachedUpdates(requestorId,
                            datasetId, clientTrackingName, maxSize
                    ));

                    List<ValueAdapter> outboundAdapters = MappingAdapterPresets.getOutboundAdapters(
                            SiriDataType.ESTIMATED_TIMETABLE,
                            OutboundIdMappingPolicy.DEFAULT
                    );

                    logger.info("Transforming cached ET-data");
                    response = SiriValueTransformer.transform(response, outboundAdapters, false, false);

                    metrics.countOutgoingData(response, SubscriptionSetup.SubscriptionMode.LITE);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                    logger.info("Streaming cached ET-data");
                    streamOutput(p, response, out);
                    logger.info("Done processing cached ET-data");
                })
                .log("RequestTracer - Request done (ET)")
                .otherwise()
                .to("direct:anshar.invalid.tracking.header.response")
                .routeId("incoming.rest.et.cached")
        ;


        from("direct:internal.anshar.rest.et.monitored.cached")
                .log("RequestTracer - Incoming request (ET)")
                .to("log:restRequest:" + getClass().getSimpleName() + "?showAll=false&showHeaders=true")
                .choice()
                .when(e -> isTrackingHeaderAcceptable(e))
                .process(p -> {

                    logger.info("Fetching cached ET-data");

                    String clientTrackingName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);

                    Siri response = siriObjectFactory.createETServiceDelivery(estimatedTimetables.getAllCachedUpdates(null, null, clientTrackingName));

                    List<ValueAdapter> outboundAdapters = MappingAdapterPresets.getOutboundAdapters(
                            SiriDataType.ESTIMATED_TIMETABLE,
                            OutboundIdMappingPolicy.DEFAULT
                    );

                    logger.info("Transforming cached ET-data");
                    response = SiriValueTransformer.transform(response, outboundAdapters, false, true);

                    metrics.countOutgoingData(response, SubscriptionSetup.SubscriptionMode.LITE);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                    logger.info("Streaming cached ET-data");
                    streamOutput(p, response, out);
                    logger.info("Done processing cached ET-data");
                })
                .log("RequestTracer - Request done (ET)")
                .otherwise()
                .to("direct:anshar.invalid.tracking.header.response")
                .routeId("incoming.rest.et.monitored.cached")
        ;


    }

    private String resolveRequestorId(HttpServletRequest request) {
        return request.getParameter("requestorId");
    }


    private Siri handleEstimatedTimetableMultipleDatasetRequest(String requestorId, Set<String> datasets, String etClientName, List<String> excludedIdList, int maxSize, long previewIntervalMillis, Set<String> requestedLines, String originalId, String altId) {
        if (datasets.isEmpty()) {
            return handleEstimatedTimetableSingleDatasetRequest(requestorId, null, etClientName, excludedIdList, maxSize, previewIntervalMillis, requestedLines, originalId, altId);
        }

        Siri globalResults = null;
        for (String dataset : datasets) {
            Siri datasetResult = handleEstimatedTimetableSingleDatasetRequest(requestorId, dataset, etClientName, excludedIdList, maxSize, previewIntervalMillis, requestedLines, originalId, altId);
            globalResults = SiriUtils.mergeSiris(globalResults, datasetResult);
        }
        return globalResults;
    }

    private Siri handleEstimatedTimetableSingleDatasetRequest(String requestorId, String datasetId, String etClientName, List<String> excludedIdList, int maxSize, long previewIntervalMillis, Set<String> requestedLines, String originalId, String altId) {
        Siri response;
        response = estimatedTimetables.createServiceDelivery(requestorId, datasetId, etClientName, excludedIdList, maxSize, previewIntervalMillis, requestedLines);
        List<ValueAdapter> outboundAdapters = new ArrayList<>();
        if (datasetId != null) {
            Map<ObjectType, Optional<IdProcessingParameters>> idParams = subscriptionConfig.buildIdProcessingParamsFromDataset(datasetId);
            outboundAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.ESTIMATED_TIMETABLE, SiriHandler.getIdMappingPolicy(originalId, altId), idParams);
        } else {
            outboundAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.ESTIMATED_TIMETABLE, SiriHandler.getIdMappingPolicy(originalId, altId));
        }


        if ("test".equals(originalId)) {
            outboundAdapters = null;
        }
        response = SiriValueTransformer.transform(response, outboundAdapters, false, false);
        return response;
    }
}
