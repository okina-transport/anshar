package no.rutebanken.anshar.routes.siri.lite;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.VehicleActivities;
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
import uk.org.siri.siri21.VehicleActivityStructure;

import java.util.*;

import static no.rutebanken.anshar.routes.HttpParameter.*;

@Service
public class SiriLiteVehicleMonitoringRoute extends RestRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(SiriLiteVehicleMonitoringRoute.class);

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private VehicleActivities vehicleActivities;

    @Autowired
    private PrometheusMetricsService metrics;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private SiriObjectFactory siriObjectFactory;


    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:internal.anshar.rest.vm")
                .log("RequestTracer - Incoming request (VM)")
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
                    String messageId = p.getIn().getMessageId();
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


                    Set<String> datasets = SiriUtils.generateDatasetListFromHeader(datasetId);
                    Siri response = handleVehicleMonitoringMultipleDatasetRequest(lineRef, requestorId, datasets, etClientName, excludedIdList, maxSize, originalId, altId, messageId);

                    metrics.countOutgoingData(response, SubscriptionSetup.SubscriptionMode.LITE);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                    streamOutput(p, response, out);
                })
                .log("RequestTracer - Request done (VM)")
                .otherwise()
                .to("direct:anshar.invalid.tracking.header.response")
                .routeId("incoming.rest.vm")
        ;

        from("direct:internal.anshar.rest.vm.cached")
                .log("RequestTracer - Incoming request (VM)")
                .to("log:restRequest:" + getClass().getSimpleName() + "?showAll=false&showHeaders=true")
                .choice()
                .when(e -> isTrackingHeaderAcceptable(e))
                .process(p -> {
                    String requestorId = resolveRequestorId(p.getIn().getBody(HttpServletRequest.class));
                    String datasetId = p.getIn().getHeader(PARAM_DATASET_ID, String.class);
                    String clientTrackingName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);
                    String messageId = p.getIn().getMessageId();

                    logger.info("Fetching cached VM-data");
                    final Collection<VehicleActivityStructure> cachedUpdates = vehicleActivities
                            .getAllCachedUpdates(requestorId, datasetId, clientTrackingName);
                    List<String> excludedIdList = getParameterValuesAsList(p.getIn(), PARAM_EXCLUDED_DATASET_ID);

                    if (excludedIdList != null && !excludedIdList.isEmpty()) {
                        cachedUpdates.removeIf(vehicle -> {
                            if (vehicle.getMonitoredVehicleJourney() != null &&
                                    vehicle.getMonitoredVehicleJourney().getDataSource() != null) {
                                // Return 'true' if codespaceId should be excluded
                                return excludedIdList.contains(vehicle.getMonitoredVehicleJourney().getDataSource());
                            }
                            return false;
                        });
                    }

                    Siri response = siriObjectFactory.createVMServiceDelivery(cachedUpdates, requestorId, messageId);

                    List<ValueAdapter> outboundAdapters = MappingAdapterPresets.getOutboundAdapters(
                            SiriDataType.VEHICLE_MONITORING,
                            OutboundIdMappingPolicy.DEFAULT
                    );

                    logger.info("Transforming cached VM-data");
                    response = SiriValueTransformer.transform(response, outboundAdapters, false, false);

                    metrics.countOutgoingData(response, SubscriptionSetup.SubscriptionMode.LITE);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                    logger.info("Streaming cached VM-data");
                    streamOutput(p, response, out);
                    logger.info("Done processing cached VM-data");
                })
                .log("RequestTracer - Request done (VM)")
                .otherwise()
                .to("direct:anshar.invalid.tracking.header.response")
                .routeId("incoming.rest.vm.cached")
        ;

    }

    private String resolveRequestorId(HttpServletRequest request) {
        return request.getParameter("requestorId");
    }


    private Siri handleVehicleMonitoringMultipleDatasetRequest(String lineRef, String requestorId, Set<String> datasets, String etClientName, List<String> excludedIdList, int maxSize, String originalId, String altId, String messageId) {
        if (datasets.isEmpty()) {
            return handleVehicleMonitoringSingleDatasetRequest(lineRef, requestorId, null, etClientName, excludedIdList, maxSize, originalId, altId, messageId);
        }
        Siri globalResults = null;
        for (String dataset : datasets) {
            Siri datasetResult = handleVehicleMonitoringSingleDatasetRequest(lineRef, requestorId, dataset, etClientName, excludedIdList, maxSize, originalId, altId, messageId);
            globalResults = SiriUtils.mergeSiris(globalResults, datasetResult);

        }
        return globalResults;
    }


    private Siri handleVehicleMonitoringSingleDatasetRequest(String lineRef, String requestorId, String datasetId, String etClientName, List<String> excludedIdList, int maxSize, String originalId, String altId, String messageId) {
        Siri response;

        if (lineRef != null) {
            response = vehicleActivities.createServiceDelivery(lineRef, messageId);
        } else {
            response = vehicleActivities.createServiceDelivery(requestorId, datasetId, etClientName, excludedIdList, maxSize, messageId);
        }

        List<ValueAdapter> outboundAdapters = new ArrayList<>();
        if (datasetId != null) {
            Map<ObjectType, Optional<IdProcessingParameters>> idParams = subscriptionConfig.buildIdProcessingParamsFromDataset(datasetId);
            outboundAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.VEHICLE_MONITORING, SiriHandler.getIdMappingPolicy(originalId, altId), idParams);
        } else {
            outboundAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.VEHICLE_MONITORING, SiriHandler.getIdMappingPolicy(originalId, altId));
        }


        if ("test".equals(originalId)) {
            outboundAdapters = null;
        }
        response = SiriValueTransformer.transform(response, outboundAdapters, false, false);

        return response;
    }

}
