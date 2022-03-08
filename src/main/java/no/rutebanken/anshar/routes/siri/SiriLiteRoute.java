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

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.data.MonitoredStopVisits;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.data.VehicleActivities;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.apache.camel.Exchange;
import org.apache.camel.model.rest.RestParamType;
import org.apache.http.HttpHeaders;
import org.entur.protobuf.mapper.SiriMapper;
import org.rutebanken.siri20.util.SiriJson;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import static no.rutebanken.anshar.routes.HttpParameter.PARAM_DATASET_ID;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_EXCLUDED_DATASET_ID;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_LINE_REF;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_MAX_SIZE;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_PREVIEW_INTERVAL;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_STOP_REF;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_USE_ORIGINAL_ID;
import static no.rutebanken.anshar.routes.HttpParameter.getParameterValuesAsList;

@Service
public class SiriLiteRoute extends RestRouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

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

    @Override
    public void configure() throws Exception {
        super.configure();
        rest("/anshar/rest")
                .tag("siri.lite")

                .get("/sx").to("direct:anshar.rest.sx")
                        .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                        .param().required(false).name(PARAM_USE_ORIGINAL_ID).type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                        .param().required(false).name(PARAM_MAX_SIZE).type(RestParamType.query).description("Specify max number of returned elements").dataType("integer").endParam()

                .get("/vm").to("direct:anshar.rest.vm")
                        .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                        .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response").dataType("string").endParam()
                        .param().required(false).name(PARAM_USE_ORIGINAL_ID).type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                        .param().required(false).name(PARAM_MAX_SIZE).type(RestParamType.query).description("Specify max number of returned elements").dataType("integer").endParam()

                .get("/et").to("direct:anshar.rest.et")
                        .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                        .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response").dataType("string").endParam()
                        .param().required(false).name(PARAM_USE_ORIGINAL_ID).type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                        .param().required(false).name(PARAM_MAX_SIZE).type(RestParamType.query).description("Specify max number of returned elements").dataType("integer").endParam()

                .get("/et-monitored").to("direct:anshar.rest.et.monitored")

                .get("/sm").to("direct:anshar.rest.sm")
                        .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                        .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response").dataType("string").endParam()
                        .param().required(false).name(PARAM_USE_ORIGINAL_ID).type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                        .param().required(false).name(PARAM_MAX_SIZE).type(RestParamType.query).description("Specify max number of returned elements").dataType("integer").endParam()
        ;

        // Dataproviders
        from("direct:anshar.rest.sx")
                .log("RequestTracer - Incoming request (SX)")
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
                        int maxSize = datasetId != null ? Integer.MAX_VALUE:configuration.getDefaultMaxSize();

                        if (maxSizeStr != null) {
                            maxSize = maxSizeStr.intValue();
                        }

                        Siri response = situations.createServiceDelivery(requestorId, datasetId, etClientName, maxSize);

                        List<ValueAdapter> outboundAdapters = MappingAdapterPresets.getOutboundAdapters(
                            SiriDataType.SITUATION_EXCHANGE,
                            SiriHandler.getIdMappingPolicy(originalId)
                        );
                        if ("test".equals(originalId)) {
                            outboundAdapters = null;
                        }
                        response = SiriValueTransformer.transform(response, outboundAdapters, false, false);

                        HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                        streamOutput(p, response, out);
                    })
                    .log("RequestTracer - Request done (SX)")
                .otherwise()
                    .to("direct:anshar.invalid.tracking.header.response")
                .routeId("incoming.rest.sx")
        ;

        from("direct:anshar.rest.vm")
                .log("RequestTracer - Incoming request (VM)")
                .to("log:restRequest:" + getClass().getSimpleName() + "?showAll=false&showHeaders=true")
                .choice()
                .when(e -> isTrackingHeaderAcceptable(e))
                    .process(p -> {
                        p.getOut().setHeaders(p.getIn().getHeaders());

                        String datasetId = p.getIn().getHeader(PARAM_DATASET_ID, String.class);
                        String originalId = p.getIn().getHeader(PARAM_USE_ORIGINAL_ID, String.class);
                        String maxSizeStr = p.getIn().getHeader(PARAM_MAX_SIZE, String.class);
                        String lineRef = p.getIn().getHeader(PARAM_LINE_REF, String.class);
                        String etClientName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);
                        List<String> excludedIdList = getParameterValuesAsList(p.getIn(), PARAM_EXCLUDED_DATASET_ID);

                        String requestorId = resolveRequestorId(p.getIn().getBody(HttpServletRequest.class));

                        int maxSize = datasetId != null ? Integer.MAX_VALUE:configuration.getDefaultMaxSize();
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
                            SiriHandler.getIdMappingPolicy(originalId)
                        );
                        if ("test".equals(originalId)) {
                            outboundAdapters = null;
                        }
                        response = SiriValueTransformer.transform(response, outboundAdapters, false, false);

                        HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                        streamOutput(p, response, out);
                    })
                    .log("RequestTracer - Request done (VM)")
                .otherwise()
                    .to("direct:anshar.invalid.tracking.header.response")
                .routeId("incoming.rest.vm")
        ;


        from("direct:anshar.rest.et")
                .log("RequestTracer - Incoming request (ET)")
                .to("log:restRequest:" + getClass().getSimpleName() + "?showAll=false&showHeaders=true")
                .choice()
                .when(e -> isTrackingHeaderAcceptable(e))
                    .process(p -> {
                        p.getOut().setHeaders(p.getIn().getHeaders());

                        String datasetId = p.getIn().getHeader(PARAM_DATASET_ID, String.class);
                        String originalId = p.getIn().getHeader(PARAM_USE_ORIGINAL_ID, String.class);
                        String maxSizeStr = p.getIn().getHeader(PARAM_MAX_SIZE, String.class);
                        String lineRef = p.getIn().getHeader(PARAM_LINE_REF, String.class);
                        String etClientName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);
                        String previewIntervalMinutesStr = p.getIn().getHeader(PARAM_PREVIEW_INTERVAL, String.class);
                        List<String> excludedIdList = getParameterValuesAsList(p.getIn(), PARAM_EXCLUDED_DATASET_ID);

                        String requestorId = resolveRequestorId(p.getIn().getBody(HttpServletRequest.class));

                        int maxSize = datasetId != null ? Integer.MAX_VALUE:configuration.getDefaultMaxSize();
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
                            previewIntervalMillis = minutes*60*1000;
                        }

                        Siri response;
                        if (lineRef != null) {
                            response = estimatedTimetables.createServiceDelivery(lineRef);
                        } else {
                            response = estimatedTimetables.createServiceDelivery(requestorId, datasetId, etClientName, excludedIdList, maxSize, previewIntervalMillis);
                        }

                        List<ValueAdapter> outboundAdapters = MappingAdapterPresets.getOutboundAdapters(
                            SiriDataType.ESTIMATED_TIMETABLE,
                            SiriHandler.getIdMappingPolicy(originalId)
                        );
                        if ("test".equals(originalId)) {
                            outboundAdapters = null;
                        }
                        response = SiriValueTransformer.transform(response, outboundAdapters, false, false);

                        HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                        streamOutput(p, response, out);
                    })
                    .log("RequestTracer - Request done (ET)")
                .otherwise()
                    .to("direct:anshar.invalid.tracking.header.response")
                .routeId("incoming.rest.et")
        ;

        from("direct:anshar.rest.sm")
                .log("RequestTracer - Incoming request (SM)")
                .to("log:restRequest:" + getClass().getSimpleName() + "?showAll=false&showHeaders=true")
                .choice()
                .when(e -> isTrackingHeaderAcceptable(e))
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    String datasetId = p.getIn().getHeader(PARAM_DATASET_ID, String.class);
                    String originalId = p.getIn().getHeader(PARAM_USE_ORIGINAL_ID, String.class);
                    String maxSizeStr = p.getIn().getHeader(PARAM_MAX_SIZE, String.class);
                    String stopRef = p.getIn().getHeader(PARAM_STOP_REF, String.class);
                    String etClientName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);
                    String previewIntervalMinutesStr = p.getIn().getHeader(PARAM_PREVIEW_INTERVAL, String.class);
                    List<String> excludedIdList = getParameterValuesAsList(p.getIn(), PARAM_EXCLUDED_DATASET_ID);

                    String requestorId = resolveRequestorId(p.getIn().getBody(HttpServletRequest.class));

                    int maxSize = datasetId != null ? Integer.MAX_VALUE:configuration.getDefaultMaxSize();
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
                        previewIntervalMillis = minutes*60*1000;
                    }

                    // TODO MHI : prévoir un filtre par stop ref
                    Siri response;
//                    if (stopRef != null) {
//                        response = monitoredStopVisits.createServiceDelivery(stopRef);
//                    } else {
                        response = monitoredStopVisits.createServiceDelivery(requestorId, datasetId, etClientName, excludedIdList, maxSize, previewIntervalMillis, new HashSet<>());
//                    }

                    List<ValueAdapter> outboundAdapters = MappingAdapterPresets.getOutboundAdapters(
                            SiriDataType.STOP_MONITORING,
                            SiriHandler.getIdMappingPolicy(originalId)
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


        from("direct:anshar.rest.et.monitored")
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

    }

    private void streamOutput(Exchange p, Siri response, HttpServletResponse out) throws IOException, JAXBException {

        metrics.countOutgoingData(response, SubscriptionSetup.SubscriptionMode.LITE);

        if (MediaType.APPLICATION_JSON.equals(p.getIn().getHeader(HttpHeaders.CONTENT_TYPE)) |
                MediaType.APPLICATION_JSON.equals(p.getIn().getHeader(HttpHeaders.ACCEPT))) {
            out.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            SiriJson.toJson(response, out.getOutputStream());
        } else if ("application/x-protobuf".equals(p.getIn().getHeader(HttpHeaders.CONTENT_TYPE)) |
                "application/x-protobuf".equals(p.getIn().getHeader(HttpHeaders.ACCEPT))) {
            final byte[] bytes = SiriMapper.mapToPbf(response).toByteArray();
            out.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-protobuf");
            out.setHeader(HttpHeaders.CONTENT_LENGTH, ""+bytes.length);
            out.getOutputStream().write(bytes);
        } else {
            out.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML);
            SiriXml.toXml(response, null, out.getOutputStream());
        }
        p.getMessage().setBody(out.getOutputStream());
    }

    /**
     * If http-parameter requestorId is not provided in request, it will be generated based on
     * client IP and requested resource for uniqueness
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
