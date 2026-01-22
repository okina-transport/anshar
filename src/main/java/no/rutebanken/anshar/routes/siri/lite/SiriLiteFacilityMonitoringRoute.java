package no.rutebanken.anshar.routes.siri.lite;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.FacilityMonitoring;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.mapping.ParkingIdsService;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.processor.FacilityRefPostProcessor;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import no.rutebanken.anshar.util.SiriUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;

import java.util.*;

import static no.rutebanken.anshar.routes.HttpParameter.*;

@Service
public class SiriLiteFacilityMonitoringRoute extends RestRouteBuilder {

    private final AnsharConfiguration configuration;
    private final PrometheusMetricsService metrics;
    private final FacilityMonitoring facilityMonitoring;
    private final SubscriptionConfig subscriptionConfig;
    private final ParkingIdsService parkingIdsService;

    public SiriLiteFacilityMonitoringRoute(AnsharConfiguration configuration, PrometheusMetricsService metrics, FacilityMonitoring facilityMonitoring, SubscriptionConfig subscriptionConfig, ParkingIdsService parkingIdsService) {
        this.configuration = configuration;
        this.metrics = metrics;
        this.facilityMonitoring = facilityMonitoring;
        this.subscriptionConfig = subscriptionConfig;
        this.parkingIdsService = parkingIdsService;
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:internal.anshar.rest.fm")
                .log("RequestTracer - Incoming request (FM)")
                .to("log:restRequest:" + getClass().getSimpleName() + "?showAll=false&showHeaders=true")
                .choice()
                .when(e -> isTrackingHeaderAcceptable(e))
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    String requestorId = resolveRequestorId(p.getIn().getBody(HttpServletRequest.class));
                    String messageId = p.getIn().getMessageId();

                    String datasetId = p.getIn().getHeader(PARAM_DATASET_ID, String.class);
                    String originalId = p.getIn().getHeader(PARAM_USE_ORIGINAL_ID, String.class);
                    Integer maxSizeStr = p.getIn().getHeader(PARAM_MAX_SIZE, Integer.class);
                    String etClientName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);
                    String facilityRef = p.getIn().getHeader(PARAM_FACILITY_REF, String.class);
                    int maxSize = datasetId != null ? Integer.MAX_VALUE : configuration.getDefaultMaxSize();

                    if (maxSizeStr != null) {
                        maxSize = maxSizeStr;
                    }

                    Set<String> datasets = SiriUtils.generateDatasetListFromHeader(datasetId);
                    Siri response = handleFacilityMonitoringMultipleDatasetRequest(requestorId, datasets, etClientName, maxSize, originalId, messageId, facilityRef);

                    metrics.countOutgoingData(response, SubscriptionSetup.SubscriptionMode.LITE);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);
                    streamOutput(p, response, out);
                })
                .log("RequestTracer - Request done (FM)")
                .otherwise()
                .to("direct:anshar.invalid.tracking.header.response")
                .routeId("incoming.rest.fm")
        ;
    }

    private String resolveRequestorId(HttpServletRequest request) {
        return request.getParameter("requestorId");
    }


    private Siri handleFacilityMonitoringMultipleDatasetRequest(String requestorId, Set<String> datasets, String etClientName, int maxSize, String originalId, String messageId, String facilityRef) {
        if (datasets.isEmpty()) {
            return handleFacilityMonitoringSingleDatasetRequest(requestorId, null, etClientName, maxSize, originalId, messageId, facilityRef);
        }
        Siri globalResults = null;
        for (String dataset : datasets) {
            Siri datasetResult = handleFacilityMonitoringSingleDatasetRequest(requestorId, dataset, etClientName, maxSize, originalId, messageId, facilityRef);
            globalResults = SiriUtils.mergeSiris(globalResults, datasetResult);

        }
        return globalResults;
    }


    private Siri handleFacilityMonitoringSingleDatasetRequest(String requestorId, String datasetId, String etClientName, int maxSize, String originalId, String messageId, String facilityRef) {
        Set<String> facilityRefs = new HashSet<>();
        if (StringUtils.isNotBlank(facilityRef)) {
            // in case facilityRef is not a producter ID, revert it
            parkingIdsService.getOriginalParkingIdByNetexId(facilityRef).ifPresentOrElse(
                    facilityRefs::add,
                    () -> facilityRefs.add(facilityRef));
        }
        Siri response = facilityMonitoring.createServiceDelivery(requestorId, datasetId, etClientName, null, maxSize, null, facilityRefs, null, null, messageId);

        List<ValueAdapter> outboundAdapters;
        OutboundIdMappingPolicy outboundIdMappingPolicy = SiriHandler.getIdMappingPolicy(originalId, null);
        if (datasetId != null) {
            Map<ObjectType, Optional<IdProcessingParameters>> idParams = subscriptionConfig.buildIdProcessingParamsFromDataset(datasetId);
            outboundAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.FACILITY_MONITORING, outboundIdMappingPolicy, idParams);
        } else {
            outboundAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.FACILITY_MONITORING, outboundIdMappingPolicy);
        }
        outboundAdapters.add(new FacilityRefPostProcessor(datasetId, outboundIdMappingPolicy));
        if ("test".equals(originalId)) {
            outboundAdapters = null;
        }
        response = SiriValueTransformer.transform(response, outboundAdapters, false, false);
        return response;
    }


}
