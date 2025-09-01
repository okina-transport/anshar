package no.rutebanken.anshar.routes.siri.lite;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.FacilityMonitoring;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import no.rutebanken.anshar.util.SiriUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;

import java.util.*;

import static no.rutebanken.anshar.routes.HttpParameter.*;

@Service
public class SiriLiteFacilityMonitoringRoute extends RestRouteBuilder {


    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private PrometheusMetricsService metrics;

    @Autowired
    private FacilityMonitoring facilityMonitoring;

    @Autowired
    private SubscriptionConfig subscriptionConfig;


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
                    int maxSize = datasetId != null ? Integer.MAX_VALUE : configuration.getDefaultMaxSize();

                    if (maxSizeStr != null) {
                        maxSize = maxSizeStr;
                    }

                    Set<String> datasets = SiriUtils.generateDatasetListFromHeader(datasetId);
                    Siri response = handleFacilityMonitoringMultipleDatasetRequest(requestorId, datasets, etClientName, maxSize, originalId, messageId);

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


    private Siri handleFacilityMonitoringMultipleDatasetRequest(String requestorId, Set<String> datasets, String etClientName, int maxSize, String originalId, String messageId) {
        if (datasets.isEmpty()) {
            return handleFacilityMonitoringSingleDatasetRequest(requestorId, null, etClientName, maxSize, originalId, messageId);
        }
        Siri globalResults = null;
        for (String dataset : datasets) {
            Siri datasetResult = handleFacilityMonitoringSingleDatasetRequest(requestorId, dataset, etClientName, maxSize, originalId, messageId);
            globalResults = SiriUtils.mergeSiris(globalResults, datasetResult);

        }
        return globalResults;
    }


    private Siri handleFacilityMonitoringSingleDatasetRequest(String requestorId, String datasetId, String etClientName, int maxSize, String originalId, String messageId) {
        Siri response = facilityMonitoring.createServiceDelivery(requestorId, datasetId, etClientName, null, maxSize, null, null, null, null, messageId);

        List<ValueAdapter> outboundAdapters = new ArrayList<>();
        if (datasetId != null) {
            Map<ObjectType, Optional<IdProcessingParameters>> idParams = subscriptionConfig.buildIdProcessingParamsFromDataset(datasetId);
            outboundAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.FACILITY_MONITORING, SiriHandler.getIdMappingPolicy(originalId, null), idParams);
        } else {
            outboundAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.FACILITY_MONITORING, SiriHandler.getIdMappingPolicy(originalId, null));
        }

        if ("test".equals(originalId)) {
            outboundAdapters = null;
        }
        response = SiriValueTransformer.transform(response, outboundAdapters, false, false);
        return response;
    }


}
