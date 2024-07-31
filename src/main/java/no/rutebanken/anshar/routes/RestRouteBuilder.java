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

package no.rutebanken.anshar.routes;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.frGeneralMessageStructure.Content;
import no.rutebanken.anshar.data.util.CustomSiriXml;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.builder.Namespaces;
import org.apache.http.HttpHeaders;
import org.eclipse.jetty.io.EofException;
import org.entur.protobuf.mapper.SiriMapper;
import org.entur.siri21.util.SiriJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import uk.org.siri.siri20.*;
import uk.org.siri.siri21.GeneralMessage;
import uk.org.siri.siri21.GeneralMessageDeliveryStructure;
import uk.org.siri.siri21.Siri;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.Map;

import static no.rutebanken.anshar.routes.HttpParameter.SIRI_VERSION_HEADER_NAME;

public class RestRouteBuilder extends RouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(RestRouteBuilder.class);

    protected Namespaces nameSpace = new Namespaces("siri", "http://www.siri.org.uk/siri")
            .add("xsd", "http://www.w3.org/2001/XMLSchema");


    @Value("${anshar.data.handler.baseurl.vm:}")
    protected String vmHandlerBaseUrl;

    @Value("${anshar.data.handler.baseurl.et:}")
    protected String etHandlerBaseUrl;

    @Value("${anshar.data.handler.baseurl.sx:}")
    protected String sxHandlerBaseUrl;

    @Value("${anshar.data.handler.baseurl.sm:}")
    protected String smHandlerBaseUrl;

    @Value("${anshar.data.handler.baseurl.gm:}")
    protected String gmHandlerBaseUrl;

    @Value("${anshar.data.handler.baseurl.fm:}")
    protected String fmHandlerBaseUrl;

    @Autowired
    private AnsharConfiguration configuration;

    private static boolean isDataHandlersInitialized = false;

    @Override
    public void configure() throws Exception {

        restConfiguration()
                .component("jetty")
                .port(configuration.getInboundPort())
                .apiContextPath("anshar/swagger.json")
//                .endpointProperty("httpBindingRef", "#contentEncodingRequestFilter")
                .apiProperty("api.title", "Realtime").apiProperty("api.version", "1.0")
                .apiProperty("cors", "true")
                .enableCORS(true)
                .corsAllowCredentials(true)
                .corsHeaderProperty("Access-Control-Allow-Origin", "*")
                .corsHeaderProperty("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers, Authorization")
        ;

        onException(ConnectException.class)
                .maximumRedeliveries(10)
                .redeliveryDelay(10000)
                .useExponentialBackOff();

        onException(UnmarshalException.class, InvalidPayloadException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("400"))
                .setBody(simple("Invalid XML"))
        ;

        onException(AccessDeniedException.class)
                .handled(true)
                .setHeader("WWW-Authenticate", simple("Basic")) // Request login
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("401"))
                .setBody(simple("Unauthorized"))
        ;

        onException(EofException.class)
                .process(e -> {
                    logger.error("EOF error body:" + e.getIn().getBody(String.class));
                });

        onException(IllegalArgumentException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("400"))
        ;

        errorHandler(defaultErrorHandler()
                .log(logger)
                .loggingLevel(LoggingLevel.INFO)
        );

        if (!isDataHandlersInitialized) {
            isDataHandlersInitialized = true;
            createClientRequestRoutes();
        }

        from("direct:anshar.invalid.tracking.header.response")
                .log("invalid")
                .removeHeaders("*")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("400")) //400 Bad request
                .setBody(constant("Missing required header (" + configuration.getTrackingHeaderName() + ")"))
                .routeId("reject.request.missing.header")
        ;

        from("direct:anshar.blocked.tracking.header.response")
                .removeHeaders("*")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("400")) //400 Bad request
                .setBody(constant(""))
                .routeId("reject.request.blocked.header")
        ;

    }

    /*
     * Creates routes to handle routing of incoming requests based on the mode the instance is started with
     *
     * PROXY redirects requests to et/vm/sx-instances
     */
    protected void createClientRequestRoutes() {

        if (configuration.processET()) {
            from("direct:process.et.subscription.request")
                    .to("direct:internal.handle.subscription")
            ;
            from("direct:process.et.service.request")
                    .to("direct:internal.process.service.request")
            ;
            from("direct:process.et.service.request.cache")
                    .to("direct:internal.process.service.request.cache")
            ;
            //REST
            from("direct:anshar.rest.et")
                    .to("direct:internal.anshar.rest.et")
            ;
            from("direct:anshar.rest.et.cached")
                    .to("direct:internal.anshar.rest.et.cached")
            ;
            from("direct:anshar.rest.et.monitored")
                    .to("direct:internal.anshar.rest.et.monitored")
            ;
            from("direct:anshar.rest.et.monitored.cached")
                    .to("direct:internal.anshar.rest.et.monitored.cached")
            ;
        } else {
            from("direct:process.et.subscription.request")
                    .to("direct:redirect.request.et")
            ;
            from("direct:process.et.service.request")
                    .to("direct:redirect.request.et")
            ;
            from("direct:process.et.service.request.cache")
                    .to("direct:redirect.request.et")
            ;
            //REST
            from("direct:anshar.rest.et")
                    .to("direct:redirect.request.et")
            ;
            from("direct:anshar.rest.et.cached")
                    .to("direct:redirect.request.et")
            ;
            from("direct:anshar.rest.et.monitored")
                    .to("direct:redirect.request.et")
            ;
            from("direct:anshar.rest.et.monitored.cached")
                    .to("direct:redirect.request.et")
            ;
            if (!configuration.processAdmin()) {
                // Data-instances should never redirect requests
                from("direct:redirect.request.et")
                        .log("Ignore redirect")
                ;

            } else {
                from("direct:redirect.request.et")
                        // Setting default encoding if none is set
                        .choice().when(header("Content-Type").isEqualTo(""))
                        .setHeader("Content-Type", simple(MediaType.TEXT_XML))
                        .end()

                        //Force forwarding parameters - if used in query
                        .choice().when(header("CamelHttpQuery").isNull())
                        .toD(etHandlerBaseUrl + "${header.CamelHttpUri}?Content-Type=${header.Content-Type}&bridgeEndpoint=true")
                        .otherwise()
                        .toD(etHandlerBaseUrl + "${header.CamelHttpUri}?Content-Type=${header.Content-Type}&bridgeEndpoint=true&${header.CamelHttpQuery}")
                        .endChoice()
                ;
            }
        }

        if (configuration.processVM()) {
            from("direct:process.vm.subscription.request")
                    .to("direct:internal.handle.subscription")
            ;
            from("direct:process.vm.service.request")
                    .to("direct:internal.process.service.request")
            ;
            from("direct:process.vm.service.request.cache")
                    .to("direct:internal.process.service.request.cache")
            ;
            //REST
            from("direct:anshar.rest.vm")
                    .to("direct:internal.anshar.rest.vm")
            ;
            from("direct:anshar.rest.vm.cached")
                    .to("direct:internal.anshar.rest.vm.cached")
            ;

        } else {
            from("direct:process.vm.subscription.request")
                    .to("direct:redirect.request.vm")
            ;
            from("direct:process.vm.service.request")
                    .to("direct:redirect.request.vm")
            ;
            from("direct:process.vm.service.request.cache")
                    .to("direct:redirect.request.vm")
            ;
            from("direct:anshar.rest.vm")
                    .to("direct:redirect.request.vm")
            ;
            from("direct:anshar.rest.vm.cached")
                    .to("direct:redirect.request.vm")
            ;

            if (!configuration.processAdmin()) {
                // Data-instances should never redirect requests
                from("direct:redirect.request.vm")
                        .log("Ignore redirect")
                ;

            } else {
                from("direct:redirect.request.vm")
                        // Setting default encoding if none is set
                        .choice().when(header("Content-Type").isEqualTo(""))
                        .setHeader("Content-Type", simple(MediaType.TEXT_XML))
                        .end()

                        //Force forwarding parameters - if used in query
                        .choice().when(header("CamelHttpQuery").isNull())
                        .toD(vmHandlerBaseUrl + "${header.CamelHttpUri}?Content-Type=${header.Content-Type}&bridgeEndpoint=true")
                        .otherwise()
                        .toD(vmHandlerBaseUrl + "${header.CamelHttpUri}?Content-Type=${header.Content-Type}&bridgeEndpoint=true&${header.CamelHttpQuery}")
                        .endChoice()
                ;
            }
        }

        if (configuration.processSX()) {
            from("direct:process.sx.subscription.request")
                    .log("sx direct")
                    .to("direct:internal.handle.subscription")
            ;
            from("direct:process.sx.service.request")
                    .to("direct:internal.process.service.request")
            ;
            from("direct:process.sx.service.request.cache")
                    .to("direct:internal.process.service.request.cache")
            ;

            //REST
            from("direct:anshar.rest.sx")
                    .to("direct:internal.anshar.rest.sx")
            ;
            from("direct:anshar.rest.sx.cached")
                    .to("direct:internal.anshar.rest.sx.cached")
            ;
        } else {
            from("direct:process.sx.subscription.request")
                    .to("direct:redirect.request.sx")
            ;
            from("direct:process.sx.service.request")
                    .to("direct:redirect.request.sx")
            ;
            from("direct:process.sx.service.request.cache")
                    .to("direct:redirect.request.sx")
            ;
            from("direct:anshar.rest.sx")
                    .to("direct:redirect.request.sx")
            ;
            from("direct:anshar.rest.sx.cached")
                    .to("direct:redirect.request.sx")
            ;

            if (!configuration.processAdmin()) {
                // Data-instances should never redirect requests
                from("direct:redirect.request.sx")
                        .log("Ignore redirect")
                ;

            } else {
                from("direct:redirect.request.sx")
                        // Setting default encoding if none is set
                        .choice().when(header("Content-Type").isEqualTo(""))
                        .setHeader("Content-Type", simple(MediaType.TEXT_XML))
                        .end()

                        //Force forwarding parameters - if used in query
                        .choice().when(header("CamelHttpQuery").isNull())
                        .toD(sxHandlerBaseUrl + "${header.CamelHttpUri}?Content-Type=${header.Content-Type}&bridgeEndpoint=true")
                        .otherwise()
                        .toD(sxHandlerBaseUrl + "${header.CamelHttpUri}?Content-Type=${header.Content-Type}&bridgeEndpoint=true&${header.CamelHttpQuery}")
                        .endChoice()
                ;
            }
        }


        if (configuration.processSM()) {
            from("direct:process.sm.subscription.request")
                    .to("direct:internal.handle.subscription")
            ;
            from("direct:process.sm.service.request")
                    .to("direct:internal.process.service.request")
            ;
            from("direct:process.sm.service.request.cache")
                    .to("direct:internal.process.service.request.cache")
            ;
            //REST
            from("direct:anshar.rest.sm")
                    .to("direct:internal.anshar.rest.sm")
            ;
            from("direct:anshar.rest.sm.cached")
                    .to("direct:internal.anshar.rest.sm.cached")
            ;

        } else {
            from("direct:process.sm.subscription.request")
                    .to("direct:redirect.request.sm")
            ;
            from("direct:process.sm.service.request")
                    .to("direct:redirect.request.sm")
            ;
            from("direct:process.sm.service.request.cache")
                    .to("direct:redirect.request.sm")
            ;
            from("direct:anshar.rest.sm")
                    .to("direct:redirect.request.sm")
            ;
            from("direct:anshar.rest.sm.cached")
                    .to("direct:redirect.request.sm")
            ;

            if (!configuration.processAdmin()) {
                // Data-instances should never redirect requests
                from("direct:redirect.request.sm")
                        .log("Ignore redirect")
                ;

            } else {
                from("direct:redirect.request.sm")
                        // Setting default encoding if none is set
                        .choice().when(header("Content-Type").isEqualTo(""))
                        .setHeader("Content-Type", simple(MediaType.TEXT_XML))
                        .end()

                        //Force forwarding parameters - if used in query
                        .choice().when(header("CamelHttpQuery").isNull())
                        .toD(smHandlerBaseUrl + "${header.CamelHttpUri}?Content-Type=${header.Content-Type}&bridgeEndpoint=true")
                        .otherwise()
                        .toD(smHandlerBaseUrl + "${header.CamelHttpUri}?Content-Type=${header.Content-Type}&bridgeEndpoint=true&${header.CamelHttpQuery}")
                        .endChoice()
                ;
            }
        }


        if (configuration.processGM()) {
            from("direct:process.gm.subscription.request")
                    .log("process GM direct")
                    .to("direct:internal.handle.subscription")
            ;
            from("direct:process.gm.service.request")
                    .to("direct:internal.process.service.request")
            ;
            from("direct:process.gm.service.request.cache")
                    .to("direct:internal.process.service.request.cache")
            ;
            //REST
            from("direct:anshar.rest.gm")
                    .to("direct:internal.anshar.rest.gm")
            ;
            from("direct:anshar.rest.gm.cached")
                    .to("direct:internal.anshar.rest.gm.cached")
            ;

        } else {
            from("direct:process.gm.subscription.request")
                    .log("process GM redir")
                    .to("direct:redirect.request.gm")
            ;
            from("direct:process.gm.service.request")
                    .to("direct:redirect.request.gm")
            ;
            from("direct:process.gm.service.request.cache")
                    .to("direct:redirect.request.gm")
            ;
            from("direct:anshar.rest.gm")
                    .to("direct:redirect.request.gm")
            ;
            from("direct:anshar.rest.gm.cached")
                    .to("direct:redirect.request.gm")
            ;

            if (!configuration.processAdmin()) {
                // Data-instances should never redirect requests
                from("direct:redirect.request.gm")
                        .log("Ignore redirect")
                ;

            } else {
                from("direct:redirect.request.gm")
                        // Setting default encoding if none is set
                        .choice().when(header("Content-Type").isEqualTo(""))
                        .setHeader("Content-Type", simple(MediaType.TEXT_XML))
                        .end()

                        //Force forwarding parameters - if used in query
                        .choice().when(header("CamelHttpQuery").isNull())
                        .toD(gmHandlerBaseUrl + "${header.CamelHttpUri}?Content-Type=${header.Content-Type}&bridgeEndpoint=true")
                        .otherwise()
                        .toD(gmHandlerBaseUrl + "${header.CamelHttpUri}?Content-Type=${header.Content-Type}&bridgeEndpoint=true&${header.CamelHttpQuery}")
                        .endChoice()
                ;
            }
        }

        if (configuration.processFM()) {
            from("direct:process.fm.subscription.request")
                    .log("process FM direct")
                    .to("direct:internal.handle.subscription")
            ;
            from("direct:process.fm.service.request")
                    .to("direct:internal.process.service.request")
            ;
            from("direct:process.fm.service.request.cache")
                    .to("direct:internal.process.service.request.cache")
            ;
            //REST
            from("direct:anshar.rest.fm")
                    .to("direct:internal.anshar.rest.fm")
            ;
            from("direct:anshar.rest.fm.cached")
                    .to("direct:internal.anshar.rest.fm.cached")
            ;

        } else {
            from("direct:process.fm.subscription.request")
                    .log("process FM redir")
                    .to("direct:redirect.request.fm")
            ;
            from("direct:process.fm.service.request")
                    .to("direct:redirect.request.fm")
            ;
            from("direct:process.fm.service.request.cache")
                    .to("direct:redirect.request.fm")
            ;
            from("direct:anshar.rest.fm")
                    .to("direct:redirect.request.fm")
            ;
            from("direct:anshar.rest.fm.cached")
                    .to("direct:redirect.request.fm")
            ;

            if (!configuration.processAdmin()) {
                // Data-instances should never redirect requests
                from("direct:redirect.request.fm")
                        .log("Ignore redirect")
                ;

            } else {
                from("direct:redirect.request.fm")
                        // Setting default encoding if none is set
                        .choice().when(header("Content-Type").isEqualTo(""))
                        .setHeader("Content-Type", simple(MediaType.TEXT_XML))
                        .end()

                        //Force forwarding parameters - if used in query
                        .choice().when(header("CamelHttpQuery").isNull())
                        .toD(fmHandlerBaseUrl + "${header.CamelHttpUri}?Content-Type=${header.Content-Type}&bridgeEndpoint=true")
                        .otherwise()
                        .toD(fmHandlerBaseUrl + "${header.CamelHttpUri}?Content-Type=${header.Content-Type}&bridgeEndpoint=true&${header.CamelHttpQuery}")
                        .endChoice()
                ;
            }
        }
    }

    /**
     * Determines if the current incoming message is related to a subscription request or termination
     *
     * @param e exchange with incoming data
     * @return true : the current message is related to subscription
     * false : the currect message is not related to subscription
     */
    protected boolean isSubscriptionMessage(Exchange e) {

        String incomingMsg = e.getIn().getBody(String.class);
        return incomingMsg.contains("SubscriptionRequest") || incomingMsg.contains("DeleteSubscription");
    }

    protected boolean isTrackingHeaderAcceptable(Exchange e) {
        String camelHttpMethod = (String) e.getIn().getHeader("CamelHttpMethod");

        String header = e.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);
        if (header != null && configuration.getBlockedEtClientNames().contains(header)) {
            logger.info("Blocked request from {} = {}", configuration.getTrackingHeaderName(), header);
            return false;
        }
        if (isTrackingRequired(camelHttpMethod)) {
            return header != null && !header.isEmpty();
        }
        return true;
    }

    /**
     * Returns true if Et-Client-header is blocked - request should be ignored
     *
     * @param e
     * @return
     */
    protected boolean isTrackingHeaderBlocked(Exchange e) {

        String header = (String) e.getIn().getHeader(configuration.getTrackingHeaderName());
        if (header != null && configuration.getBlockedEtClientNames().contains(header)) {
            logger.info("Blocked request from {} = {}", configuration.getTrackingHeaderName(), header);
            return true;
        }
        return false;
    }

    private boolean isTrackingRequired(String httpMethod) {
        if ("POST".equals(httpMethod) && configuration.isTrackingHeaderRequiredforPost()) {
            return true;
        }
        if ("GET".equals(httpMethod) && configuration.isTrackingHeaderRequiredForGet()) {
            return true;
        }
        return false;
    }

    protected String getSubscriptionIdFromPath(String path) {
        if (configuration.getIncomingPathPattern().startsWith("/")) {
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
        } else {
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
        }


        Map<String, String> values = calculatePathVariableMap(path);
        logger.trace("Incoming delivery {}", values);

        return values.get("subscriptionId");
    }

    private Map<String, String> calculatePathVariableMap(String path) {
        String[] parameters = path.split("/");
        String[] parameterNames = configuration.getIncomingPathPattern().split("/");

        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < parameterNames.length; i++) {

            String value = (parameters.length > i ? parameters[i] : null);

            if (parameterNames[i].startsWith("{")) {
                parameterNames[i] = parameterNames[i].substring(1);
            }
            if (parameterNames[i].endsWith("}")) {
                parameterNames[i] = parameterNames[i].substring(0, parameterNames[i].lastIndexOf("}"));
            }

            values.put(parameterNames[i], value);
        }

        return values;
    }

    protected void streamOutput(Exchange p, Siri response, HttpServletResponse out) throws IOException, JAXBException, XMLStreamException {

        boolean siri21Version = false;
        uk.org.siri.siri20.Siri siri20Response = null;
        if ("2.1".equals(p.getIn().getHeader(SIRI_VERSION_HEADER_NAME))) {
            siri21Version = true;
        } else {
            siri20Response = downgradeSiriVersion(response);
        }

        if (MediaType.APPLICATION_JSON.equals(p.getIn().getHeader(HttpHeaders.CONTENT_TYPE)) |
                MediaType.APPLICATION_JSON.equals(p.getIn().getHeader(HttpHeaders.ACCEPT))) {
            p.getMessage().setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            out.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            if (siri21Version) {
                SiriJson.toJson(response, out.getOutputStream());
            } else {
                org.rutebanken.siri20.util.SiriJson.toJson(siri20Response, out.getOutputStream());
            }
        } else if ("application/x-protobuf".equals(p.getIn().getHeader(HttpHeaders.CONTENT_TYPE)) |
                "application/x-protobuf".equals(p.getIn().getHeader(HttpHeaders.ACCEPT))) {
            try {
                final byte[] bytes = SiriMapper.mapToPbf(siri20Response).toByteArray();
                p.getMessage().setHeader(HttpHeaders.CONTENT_TYPE, "application/x-protobuf");
                out.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-protobuf");
                p.getMessage().setHeader(HttpHeaders.CONTENT_LENGTH, "" + bytes.length);
                out.getOutputStream().write(bytes);
            } catch (NullPointerException npe) {
                File file = new File("ET-" + System.currentTimeMillis() + ".xml");
                log.error("Caught NullPointerException, data written to " + file.getAbsolutePath(), npe);
                CustomSiriXml.toXml(response, null, new FileOutputStream(file));
            }
        } else {
            p.getMessage().setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_XML);
            out.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_XML);

            if (siri21Version) {
                CustomSiriXml.toXml(response, null, out.getOutputStream());
            } else {
                CustomSiriXml.toXml(
                        siri20Response,
                        null,
                        out.getOutputStream()
                );
            }
        }
        p.getMessage().setBody(out.getOutputStream());
    }

    public static uk.org.siri.siri20.Siri downgradeSiriVersion(Siri response) throws JAXBException, XMLStreamException {
        uk.org.siri.siri20.Siri siri20Response;

        Map<String, Content> savedContent = saveGMContent(response);
        String siri2_0Xml = CustomSiriXml.toXml(response);
        siri20Response = CustomSiriXml.parseSiri20Xml(siri2_0Xml);
        restoreContent(siri20Response, savedContent);
        siri20Response.setVersion("2.0");
        ServiceDelivery serviceDelivery = siri20Response.getServiceDelivery();
        if (serviceDelivery != null) {
            if (!serviceDelivery.getEstimatedTimetableDeliveries().isEmpty()) {
                for (EstimatedTimetableDeliveryStructure delivery : serviceDelivery.getEstimatedTimetableDeliveries()) {
                    delivery.setVersion("2.0");
                }
            }
            if (!serviceDelivery.getVehicleMonitoringDeliveries().isEmpty()) {
                for (VehicleMonitoringDeliveryStructure delivery : serviceDelivery.getVehicleMonitoringDeliveries()) {
                    delivery.setVersion("2.0");
                }
            }
            if (!serviceDelivery.getSituationExchangeDeliveries().isEmpty()) {
                for (SituationExchangeDeliveryStructure delivery : serviceDelivery.getSituationExchangeDeliveries()) {
                    delivery.setVersion("2.0");
                }
            }

            if (!serviceDelivery.getStopMonitoringDeliveries().isEmpty()) {
                for (StopMonitoringDeliveryStructure stopMonitoringDelivery : serviceDelivery.getStopMonitoringDeliveries()) {
                    stopMonitoringDelivery.setVersion("2.0");
                }
            }

            if (!serviceDelivery.getGeneralMessageDeliveries().isEmpty()) {
                for (uk.org.siri.siri20.GeneralMessageDeliveryStructure generalMessageDelivery : serviceDelivery.getGeneralMessageDeliveries()) {
                    generalMessageDelivery.setVersion("2.0");
                }
            }

            if (!serviceDelivery.getFacilityMonitoringDeliveries().isEmpty()) {
                for (FacilityMonitoringDeliveryStructure facilityMonitoringDelivery : serviceDelivery.getFacilityMonitoringDeliveries()) {
                    facilityMonitoringDelivery.setVersion("2.0");
                }
            }

        }
        return siri20Response;
    }

    private static void restoreContent(uk.org.siri.siri20.Siri siri20Response, Map<String, Content> savedContent) {
        if (siri20Response.getServiceDelivery() == null || siri20Response.getServiceDelivery().getGeneralMessageDeliveries() == null || siri20Response.getServiceDelivery().getGeneralMessageDeliveries().isEmpty()) {
            return;
        }

        for (uk.org.siri.siri20.GeneralMessageDeliveryStructure generalMessageDelivery : siri20Response.getServiceDelivery().getGeneralMessageDeliveries()) {
            for (uk.org.siri.siri20.GeneralMessage generalMessage : generalMessageDelivery.getGeneralMessages()) {
                String itemIdentifier = generalMessage.getItemIdentifier();
                if (savedContent.containsKey(itemIdentifier)) {
                    generalMessage.setContent(savedContent.get(itemIdentifier));
                }
            }
        }
    }

    private static Map<String, Content> saveGMContent(Siri response) {
        Map<String, Content> contentMap = new HashMap<>();

        if (response.getServiceDelivery() == null || response.getServiceDelivery().getGeneralMessageDeliveries() == null || response.getServiceDelivery().getGeneralMessageDeliveries().isEmpty()) {
            return contentMap;
        }


        for (GeneralMessageDeliveryStructure generalMessageDelivery : response.getServiceDelivery().getGeneralMessageDeliveries()) {
            for (GeneralMessage generalMessage : generalMessageDelivery.getGeneralMessages()) {
                if (generalMessage.getContent() == null || !(generalMessage.getContent() instanceof Content)) {
                    continue;
                }

                contentMap.put(generalMessage.getItemIdentifier(), (Content) generalMessage.getContent());
                generalMessage.setContent(null);
            }

        }

        return contentMap;
    }

}

// To be removed according to task ROR-521
//@Component
//class ContentEncodingRequestFilter extends JettyRestHttpBinding {
//
//    private static final String headerToRemove = "Content-Encoding";
//    private static final String headerValueToRemove = "iso-8859-15";
//
//    @Override
//    public void readRequest(HttpServletRequest request, HttpMessage message) {
//        if (((Request) request).getHttpFields().contains(headerToRemove, headerValueToRemove)) {
//            ((Request) request).getHttpFields().remove(headerToRemove);
//        }
//        super.readRequest(request, message);
//    }
//}
