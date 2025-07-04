package no.rutebanken.anshar.routes.siri.lite;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.Situations;
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
public class SiriLiteSituationExchangeRoute extends RestRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(SiriLiteSituationExchangeRoute.class);

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private Situations situations;

    @Autowired
    private PrometheusMetricsService metrics;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private SiriObjectFactory siriObjectFactory;


    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:internal.anshar.rest.sx")
                .log("RequestTracer - Incoming request (SX)")
                .to("log:restRequest:" + getClass().getSimpleName() + "?showAll=false&showHeaders=true")
                .choice()
                .when(e -> isTrackingHeaderAcceptable(e))
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    String requestorId = resolveRequestorId(p.getIn().getBody(HttpServletRequest.class));

                    String datasetId = p.getIn().getHeader(PARAM_DATASET_ID, String.class);
                    String originalId = p.getIn().getHeader(PARAM_USE_ORIGINAL_ID, String.class);
                    String altId = p.getIn().getHeader(PARAM_USE_ALT_ID, String.class);
                    Integer maxSizeStr = p.getIn().getHeader(PARAM_MAX_SIZE, Integer.class);
                    String etClientName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);
                    int maxSize = datasetId != null ? Integer.MAX_VALUE : configuration.getDefaultMaxSize();

                    if (maxSizeStr != null) {
                        maxSize = maxSizeStr;
                    }


                    Set<String> datasets = SiriUtils.generateDatasetListFromHeader(datasetId);

                    Siri response = handleSituationExchangeMultipleDatasetRequest(requestorId, datasets, etClientName, maxSize, originalId, altId);

                    metrics.countOutgoingData(response, SubscriptionSetup.SubscriptionMode.LITE);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);
                    streamOutput(p, response, out);
                })
                .log("RequestTracer - Request done (SX)")
                .otherwise()
                .to("direct:anshar.invalid.tracking.header.response")
                .routeId("incoming.rest.sx")
        ;

        from("direct:internal.anshar.rest.sx.cached")
                .log("RequestTracer - Incoming request (SX)")
                .to("log:restRequest:" + getClass().getSimpleName() + "?showAll=false&showHeaders=true")
                .choice()
                .when(e -> isTrackingHeaderAcceptable(e))
                .process(p -> {
                    String requestorId = resolveRequestorId(p.getIn().getBody(HttpServletRequest.class));
                    String datasetId = p.getIn().getHeader(PARAM_DATASET_ID, String.class);
                    String clientTrackingName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);

                    logger.info("Fetching cached SX-data");
                    Siri response = siriObjectFactory.createSXServiceDelivery(situations.getAllCachedUpdates(requestorId,
                            datasetId, clientTrackingName
                    ));

                    List<ValueAdapter> outboundAdapters = MappingAdapterPresets.getOutboundAdapters(
                            SiriDataType.SITUATION_EXCHANGE,
                            OutboundIdMappingPolicy.DEFAULT
                    );

                    logger.info("Transforming cached SX-data");
                    response = SiriValueTransformer.transform(response, outboundAdapters, false, false);

                    metrics.countOutgoingData(response, SubscriptionSetup.SubscriptionMode.LITE);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                    logger.info("Streaming cached SX-data");
                    streamOutput(p, response, out);
                    logger.info("Done processing cached SX-data");
                })
                .log("RequestTracer - Request done (SX)")
                .otherwise()
                .to("direct:anshar.invalid.tracking.header.response")
                .routeId("incoming.rest.sx.cached")
        ;


    }

    private String resolveRequestorId(HttpServletRequest request) {
        return request.getParameter("requestorId");
    }


    private Siri handleSituationExchangeMultipleDatasetRequest(String requestorId, Set<String> datasets, String etClientName, int maxSize, String originalId, String altId) {
        if (datasets.isEmpty()) {
            return handleSituationExchangeSimpleDatasetRequest(requestorId, null, etClientName, maxSize, originalId, altId);
        }

        Siri globalResults = null;
        for (String dataset : datasets) {
            Siri datasetResult = handleSituationExchangeSimpleDatasetRequest(requestorId, dataset, etClientName, maxSize, originalId, altId);
            globalResults = SiriUtils.mergeSiris(globalResults, datasetResult);

        }
        return globalResults;

    }

    private Siri handleSituationExchangeSimpleDatasetRequest(String requestorId, String datasetId, String etClientName, int maxSize, String originalId, String altId) {
        Siri response = situations.createServiceDelivery(requestorId, datasetId, etClientName, maxSize);

        List<ValueAdapter> outboundAdapters = new ArrayList<>();
        if (datasetId != null) {
            Map<ObjectType, Optional<IdProcessingParameters>> idParams = subscriptionConfig.buildIdProcessingParamsFromDataset(datasetId);
            outboundAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.SITUATION_EXCHANGE, SiriHandler.getIdMappingPolicy(originalId, altId), idParams);
        } else {
            outboundAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.SITUATION_EXCHANGE, SiriHandler.getIdMappingPolicy(originalId, altId));
        }

        if ("test".equals(originalId)) {
            outboundAdapters = null;
        }
        response = SiriValueTransformer.transform(response, outboundAdapters, false, false);
        return response;
    }


}
