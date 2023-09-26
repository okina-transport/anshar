/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.routes.siri;

import io.micrometer.core.instrument.util.StringUtils;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.*;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.handlers.outbound.DiscoveryLinesOutbound;
import no.rutebanken.anshar.routes.siri.handlers.outbound.DiscoveryStopPointsOutbound;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.apache.camel.Exchange;
import org.apache.camel.model.rest.RestParamType;
import org.apache.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.VehicleActivityStructure;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static no.rutebanken.anshar.routes.HttpParameter.*;
import static no.rutebanken.anshar.routes.validation.validators.Constants.SIRI_LITE_SERVICE_NAME;

@Service
public class SiriLiteRoute extends RestRouteBuilder {
    private static final Logger logger = LoggerFactory.getLogger(SiriLiteRoute.class);

    private
    Set<String> siriLiteAutorizedServices = new HashSet<>(Set.of("stoppoints-discovery", "lines-discovery", "stop-monitoring", "general-message", "vehicle-monitoring", "situation-exchange", "estimated-timetables", "facility-monitoring"));

    private
    Set<String> siriLiteAutorizedFormats = new HashSet<>(Set.of("json", "xml"));

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private Situations situations;

    @Autowired
    private VehicleActivities vehicleActivities;

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private MonitoredStopVisits monitoredStopVisits;

    @Autowired
    private PrometheusMetricsService metrics;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private GeneralMessages generalMessages;

    @Autowired
    private FacilityMonitoring facilityMonitoring;

    @Autowired
    private DiscoveryStopPointsOutbound discoveryStopPointsOutbound;

    @Autowired
    private DiscoveryLinesOutbound discoveryLinesOutbound;


    // @formatter:off
    @Override
    public void configure() throws Exception {
        super.configure();


        rest("/siri")
                .tag("siri.lite")
                .get("/{version}/{serviceAndFormat}").to("direct:handle.siri.lite.idf.request")
                .param().required(true).name(PARAM_VERSION).type(RestParamType.path).description("The siri version").dataType("string").endParam()
                .param().required(true).name(PARAM_SERVICE_AND_FORMAT).type(RestParamType.path).description("the requested service and the format").dataType("string").endParam()
                .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response").dataType("string").endParam()
                .param().required(false).name(PARAM_USE_ORIGINAL_ID).type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_USE_ALT_ID).type(RestParamType.query).description("Option to return alternative Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_MAX_SIZE).type(RestParamType.query).description("Specify max number of returned elements").dataType("integer").endParam()
        ;

        rest("/anshar/rest")


                .get("/sx").to("direct:anshar.rest.sx")
                .apiDocs(false)
                .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                .param().required(false).name(PARAM_USE_ORIGINAL_ID).type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_USE_ALT_ID).type(RestParamType.query).description("Option to return alternative Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_MAX_SIZE).type(RestParamType.query).description("Specify max number of returned elements").dataType("integer").endParam()

                .get("/vm").to("direct:anshar.rest.vm")
                .apiDocs(false)
                .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response").dataType("string").endParam()
                .param().required(false).name(PARAM_USE_ORIGINAL_ID).type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_USE_ALT_ID).type(RestParamType.query).description("Option to return alternative Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_MAX_SIZE).type(RestParamType.query).description("Specify max number of returned elements").dataType("integer").endParam()

                .get("/et").to("direct:anshar.rest.et")
                .apiDocs(false)
                .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response").dataType("string").endParam()
                .param().required(false).name(PARAM_USE_ORIGINAL_ID).type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_USE_ALT_ID).type(RestParamType.query).description("Option to return alternative Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_MAX_SIZE).type(RestParamType.query).description("Specify max number of returned elements").dataType("integer").endParam()

                .get("/et-monitored").apiDocs(false).to("direct:anshar.rest.et.monitored")
                .get("/et-monitored-cache").apiDocs(false).to("direct:anshar.rest.et.monitored.cached")
                .get("/sx-cache").apiDocs(false).to("direct:anshar.rest.sx.cached")
                .get("/vm-cache").apiDocs(false).to("direct:anshar.rest.vm.cached")

                .get("/sm").apiDocs(false).to("direct:anshar.rest.sm")
                .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response").dataType("string").endParam()
                .param().required(false).name(PARAM_USE_ORIGINAL_ID).type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_USE_ALT_ID).type(RestParamType.query).description("Option to return alternative Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_MAX_SIZE).type(RestParamType.query).description("Specify max number of returned elements").dataType("integer").endParam()
                .get("/gm").apiDocs(false).to("direct:anshar.rest.gm")
                .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response").dataType("string").endParam()
                .param().required(false).name(PARAM_USE_ORIGINAL_ID).type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_MAX_SIZE).type(RestParamType.query).description("Specify max number of returned elements").dataType("integer").endParam()
                .get("/fm").apiDocs(false).to("direct:anshar.rest.fm")
                .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response").dataType("string").endParam()
                .param().required(false).name(PARAM_USE_ORIGINAL_ID).type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_MAX_SIZE).type(RestParamType.query).description("Specify max number of returned elements").dataType("integer").endParam()


        ;

        from("direct:handle.siri.lite.idf.request")
                .process(e -> {
                    String version = getVersion(e);
                    handleServiceAndFormat(e);
                })
                .choice()
                .when(header(SIRI_LITE_SERVICE_NAME).isEqualTo(simple("stop-monitoring")))
                        .to("direct:anshar.rest.sm")
                .when(header(SIRI_LITE_SERVICE_NAME).isEqualTo(simple("vehicle-monitoring")))
                        .to("direct:anshar.rest.vm")
                .when(header(SIRI_LITE_SERVICE_NAME).isEqualTo(simple("situation-exchange")))
                        .to("direct:anshar.rest.sx")
                .when(header(SIRI_LITE_SERVICE_NAME).isEqualTo(simple("general-message")))
                        .to("direct:anshar.rest.gm")
                .when(header(SIRI_LITE_SERVICE_NAME).isEqualTo(simple("estimated-timetables")))
                        .to("direct:anshar.rest.et")
                .when(header(SIRI_LITE_SERVICE_NAME).isEqualTo(simple("stoppoints-discovery")))
                        .to("direct:anshar.sirilite.stoppoints.discovery")
                .when(header(SIRI_LITE_SERVICE_NAME).isEqualTo(simple("lines-discovery")))
                        .to("direct:anshar.sirilite.lines.discovery")
                .when(header(SIRI_LITE_SERVICE_NAME).isEqualTo(simple("facility-monitoring")))
                        .to("direct:anshar.rest.fm")
                .otherwise()
                .process(e -> {
                    String errorMsg = "Service not yet implemented : " + e.getIn().getHeader(SIRI_LITE_SERVICE_NAME);
                    e.getIn().setBody(errorMsg);
                    throw new IllegalArgumentException(errorMsg);
                })
                .endChoice()
        ;

        from("direct:anshar.sirilite.lines.discovery")
                .process(p -> {
                    String datasetId = p.getIn().getHeader(PARAM_DATASET_ID, String.class);
                    String originalId = p.getIn().getHeader(PARAM_USE_ORIGINAL_ID, String.class);
                    String altId = p.getIn().getHeader(PARAM_USE_ALT_ID, String.class);

                    OutboundIdMappingPolicy mappingPolicy;

                    if (altId != null && Boolean.parseBoolean(altId)){
                        mappingPolicy = OutboundIdMappingPolicy.ALT_ID;
                    }else if (originalId != null && Boolean.parseBoolean(originalId)){
                        mappingPolicy = OutboundIdMappingPolicy.ORIGINAL_ID;
                    }else{
                        mappingPolicy = OutboundIdMappingPolicy.DEFAULT;
                    }

                    Siri response = discoveryLinesOutbound.getDiscoveryLines(datasetId,mappingPolicy);
                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);
                    streamOutput(p, response, out);
                })
        ;


        from("direct:anshar.sirilite.stoppoints.discovery")
                .process(p -> {
                    String datasetId = p.getIn().getHeader(PARAM_DATASET_ID, String.class);
                    String originalId = p.getIn().getHeader(PARAM_USE_ORIGINAL_ID, String.class);
                    String altId = p.getIn().getHeader(PARAM_USE_ALT_ID, String.class);

                    OutboundIdMappingPolicy mappingPolicy;

                    if (altId != null && Boolean.parseBoolean(altId)){
                        mappingPolicy = OutboundIdMappingPolicy.ALT_ID;
                    }else if (originalId != null && Boolean.parseBoolean(originalId)){
                        mappingPolicy = OutboundIdMappingPolicy.ORIGINAL_ID;
                    }else{
                        mappingPolicy = OutboundIdMappingPolicy.DEFAULT;
                    }

                    Siri response = discoveryStopPointsOutbound.getDiscoveryStopPoints(datasetId, mappingPolicy);
                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);
                    streamOutput(p, response, out);
                })
        ;

        // Dataproviders
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
                        maxSize = maxSizeStr.intValue();
                    }

                    Siri response = situations.createServiceDelivery(requestorId, datasetId, etClientName, maxSize);

                    List<ValueAdapter> outboundAdapters = MappingAdapterPresets.getOutboundAdapters(
                            SiriDataType.SITUATION_EXCHANGE,
                            SiriHandler.getIdMappingPolicy(originalId, altId)
                    );
                    if ("test".equals(originalId)) {
                        outboundAdapters = null;
                    }
                    response = SiriValueTransformer.transform(response, outboundAdapters, false, false);

                    metrics.countOutgoingData(response, SubscriptionSetup.SubscriptionMode.LITE);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);
                    streamOutput(p, response, out);
                })
                .log("RequestTracer - Request done (SX)")
                .otherwise()
                .to("direct:anshar.invalid.tracking.header.response")
                .routeId("incoming.rest.sx")
        ;

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

                    Siri response;
                    if (lineRef != null) {
                        response = vehicleActivities.createServiceDelivery(lineRef);
                    } else {
                        response = vehicleActivities.createServiceDelivery(requestorId, datasetId, etClientName, excludedIdList, maxSize);
                    }

                    List<ValueAdapter> outboundAdapters = MappingAdapterPresets.getOutboundAdapters(
                            SiriDataType.VEHICLE_MONITORING,
                            SiriHandler.getIdMappingPolicy(originalId, altId)
                    );
                    if ("test".equals(originalId)) {
                        outboundAdapters = null;
                    }
                    response = SiriValueTransformer.transform(response, outboundAdapters, false, false);

                    metrics.countOutgoingData(response, SubscriptionSetup.SubscriptionMode.LITE);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                    streamOutput(p, response, out);
                })
                .log("RequestTracer - Request done (VM)")
                .otherwise()
                .to("direct:anshar.invalid.tracking.header.response")
                .routeId("incoming.rest.vm")
        ;


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
                        previewIntervalMillis = minutes * 60 * 1000;
                    }

                    Siri response;
                    Set<String> requestedLines = new HashSet<>();
                    if (lineRef != null) {
                        requestedLines.add(lineRef);
                    }
                    response = estimatedTimetables.createServiceDelivery(requestorId, datasetId, etClientName, excludedIdList, maxSize, previewIntervalMillis, requestedLines);

                    List<ValueAdapter> outboundAdapters = MappingAdapterPresets.getOutboundAdapters(
                            SiriDataType.ESTIMATED_TIMETABLE,
                            SiriHandler.getIdMappingPolicy(originalId, altId)
                    );
                    if ("test".equals(originalId)) {
                        outboundAdapters = null;
                    }
                    response = SiriValueTransformer.transform(response, outboundAdapters, false, false);

                    metrics.countOutgoingData(response, SubscriptionSetup.SubscriptionMode.LITE);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                    streamOutput(p, response, out);
                })
                .log("RequestTracer - Request done (ET)")
                .otherwise()
                .to("direct:anshar.invalid.tracking.header.response")
                .routeId("incoming.rest.et")
        ;

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
                        previewIntervalMillis = minutes * 60 * 1000;
                    }


                    Siri response;

                    Set<String> searchedStopIds = new HashSet<>();
                    if (StringUtils.isNotEmpty(stopRef)) {
                        searchedStopIds.add(stopRef);
                    }

                    response = monitoredStopVisits.createServiceDelivery(requestorId, datasetId, etClientName, excludedIdList, maxSize, previewIntervalMillis, searchedStopIds);
                    List<ValueAdapter> outboundAdapters = MappingAdapterPresets.getOutboundAdapters(
                            SiriDataType.STOP_MONITORING,
                            SiriHandler.getIdMappingPolicy(originalId, altId)
                    );
                    if ("test".equals(originalId)) {
                        outboundAdapters = null;
                    }
                    response = SiriValueTransformer.transform(response, outboundAdapters, false, false);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                    streamOutput(p, response, out);
                })
                .log("RequestTracer - Request done (SM)")
                .otherwise()
                .to("direct:anshar.invalid.tracking.header.response")
                .routeId("incoming.rest.sm")
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

        // Dataproviders
        from("direct:internal.anshar.rest.gm")
                .log("RequestTracer - Incoming request (GM)")
                .to("log:restRequest:" + getClass().getSimpleName() + "?showAll=false&showHeaders=true")
                .choice()
                .when(e -> isTrackingHeaderAcceptable(e))
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    String requestorId = resolveRequestorId(p.getIn().getBody(HttpServletRequest.class));

                    String datasetId = p.getIn().getHeader(PARAM_DATASET_ID, String.class);
                    String originalId = p.getIn().getHeader(PARAM_USE_ORIGINAL_ID, String.class);
                    Integer maxSizeStr = p.getIn().getHeader(PARAM_MAX_SIZE, Integer.class);
                    String etClientName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);
                    int maxSize = datasetId != null ? Integer.MAX_VALUE : configuration.getDefaultMaxSize();

                    if (maxSizeStr != null) {
                        maxSize = maxSizeStr.intValue();
                    }

                    Siri response = generalMessages.createServiceDelivery(requestorId, datasetId, etClientName, maxSize, null);

                    List<ValueAdapter> outboundAdapters = MappingAdapterPresets.getOutboundAdapters(
                            SiriDataType.GENERAL_MESSAGE,
                            SiriHandler.getIdMappingPolicy(originalId, null)
                    );
                    if ("test".equals(originalId)) {
                        outboundAdapters = null;
                    }
                    response = SiriValueTransformer.transform(response, outboundAdapters, false, false);

                    metrics.countOutgoingData(response, SubscriptionSetup.SubscriptionMode.LITE);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);
                    streamOutput(p, response, out);
                })
                .log("RequestTracer - Request done (GM)")
                .otherwise()
                .to("direct:anshar.invalid.tracking.header.response")
                .routeId("incoming.rest.gm")
        ;

        // Dataproviders
        from("direct:internal.anshar.rest.fm")
                .log("RequestTracer - Incoming request (FM)")
                .to("log:restRequest:" + getClass().getSimpleName() + "?showAll=false&showHeaders=true")
                .choice()
                .when(e -> isTrackingHeaderAcceptable(e))
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    String requestorId = resolveRequestorId(p.getIn().getBody(HttpServletRequest.class));

                    String datasetId = p.getIn().getHeader(PARAM_DATASET_ID, String.class);
                    String originalId = p.getIn().getHeader(PARAM_USE_ORIGINAL_ID, String.class);
                    Integer maxSizeStr = p.getIn().getHeader(PARAM_MAX_SIZE, Integer.class);
                    String etClientName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);
                    int maxSize = datasetId != null ? Integer.MAX_VALUE : configuration.getDefaultMaxSize();

                    if (maxSizeStr != null) {
                        maxSize = maxSizeStr.intValue();
                    }
                    Siri response = facilityMonitoring.createServiceDelivery(requestorId, datasetId, etClientName, null, maxSize, null, null, null, null);

                    List<ValueAdapter> outboundAdapters = MappingAdapterPresets.getOutboundAdapters(
                            SiriDataType.FACILITY_MONITORING,
                            SiriHandler.getIdMappingPolicy(originalId, null)
                    );
                    if ("test".equals(originalId)) {
                        outboundAdapters = null;
                    }
                    response = SiriValueTransformer.transform(response, outboundAdapters, false, false);

                    metrics.countOutgoingData(response, SubscriptionSetup.SubscriptionMode.LITE);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);
                    streamOutput(p, response, out);
                })
                .log("RequestTracer - Request done (FM)")
                .otherwise()
                .to("direct:anshar.invalid.tracking.header.response")
                .routeId("incoming.rest.fm")
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

        from("direct:internal.anshar.rest.vm.cached")
                .log("RequestTracer - Incoming request (VM)")
                .to("log:restRequest:" + getClass().getSimpleName() + "?showAll=false&showHeaders=true")
                .choice()
                .when(e -> isTrackingHeaderAcceptable(e))
                .process(p -> {
                    String requestorId = resolveRequestorId(p.getIn().getBody(HttpServletRequest.class));
                    String datasetId = p.getIn().getHeader(PARAM_DATASET_ID, String.class);
                    String clientTrackingName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);

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

                    Siri response = siriObjectFactory.createVMServiceDelivery(cachedUpdates);

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

    private void handleServiceAndFormat(Exchange e) {
        String serviceAndFormat = e.getIn().getHeader(PARAM_SERVICE_AND_FORMAT, String.class);
        if (!serviceAndFormat.contains(".")) {
            String errorMsg = "Unsupported service and format :" + serviceAndFormat + ". (should be stop-monitoring.json or vehicle-monitoring.json for example)";
            e.getIn().setBody(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        String[] serviceAndFormatTab = serviceAndFormat.split("\\.");
        String service = serviceAndFormatTab[0];

        if (!siriLiteAutorizedServices.contains(service)) {
            String errorMsg = "Unsupported service:" + service + ". (should be stoppoints-discovery, lines-discovery, stop-monitoring, general-message, vehicle-monitoring, estimated-timetables, situation-exchange)";
            e.getIn().setBody(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        e.getIn().setHeader(SIRI_LITE_SERVICE_NAME, service);

        String format = serviceAndFormatTab[1];
        if (!siriLiteAutorizedFormats.contains(format)) {
            String errorMsg = "Unsupported format:" + format + ". (should be json or xml)";
            e.getIn().setBody(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        String mediaType = "json".equals(format) ? MediaType.APPLICATION_JSON : MediaType.APPLICATION_XML;
        e.getMessage().setHeader(HttpHeaders.CONTENT_TYPE, mediaType);

    }

    private String getVersion(Exchange e) {
        String version = e.getIn().getHeader(PARAM_VERSION, String.class);
        if (!"2.0".equals(version)) {
            String errorMsg = "Unsupported version:" + version;
            e.getIn().setBody(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
        return version;
    }

    /**
     * If http-parameter requestorId is not provided in request, it will be generated based on
     * client IP and requested resource for uniqueness
     *
     * @param request
     * @return
     */
    private String resolveRequestorId(HttpServletRequest request) {
        String requestorId = request.getParameter("requestorId");

//        if (requestorId == null) {
//            // Generating requestorId based on hash from client IP
//            String clientIpAddress = request.getHeader("X-Real-IP");
//            if (clientIpAddress == null) {
//                clientIpAddress = request.getRemoteAddr();
//            }
//            if (clientIpAddress != null) {
//                String uri = request.getRequestURI();
//                requestorId = DigestUtils.sha256Hex(clientIpAddress + uri);
//                logger.info("IP: '{}' and uri '{}' mapped to requestorId: '{}'", clientIpAddress, uri, requestorId);
//            }
//        }
        return requestorId;
    }

}
