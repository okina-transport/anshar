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
import no.rutebanken.anshar.config.IncomingSiriParameters;
import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.http.common.HttpMethods;
import org.apache.camel.model.rest.RestParamType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

import static no.rutebanken.anshar.routes.HttpParameter.*;

@SuppressWarnings("unchecked")
@Service
@Configuration
public class Siri20RequestHandlerRoute extends RestRouteBuilder implements CamelContextAware {

    private static final Logger logger = LoggerFactory.getLogger(Siri20RequestHandlerRoute.class);

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private SiriHandler handler;

    @Autowired
    private AnsharConfiguration configuration;

    @Value("${default.use.original.id:false}")
    private boolean defaultUseOriginalId;


    public static final String TRANSFORM_VERSION = "TRANSFORM_VERSION";
    public static final String TRANSFORM_SOAP = "TRANSFORM_SOAP";

    // @formatter:off
    @Override
    public void configure() throws Exception {

        super.configure();

        rest("anshar").tag("siri")
                .consumes(MediaType.TEXT_XML).produces(MediaType.TEXT_XML)

                .post("/anshar/services").to("direct:process.service.request")
                .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response (SIRI ET and VM)").dataType("string").endParam()
                .description("Backwards compatible endpoint used for SIRI ServiceRequest.")

                .post("/anshar/ws/services")
                .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response (SIRI ET and VM)").dataType("string").endParam()
                .description("Backwards compatible endpoint used for SIRI ServiceRequest. SOAP format")
                .to("direct:handle.soap.request")


                .post("/anshar/services/{" + PARAM_DATASET_ID + "}").to("direct:process.service.request")
                .description("Backwards compatible endpoint used for SIRI ServiceRequest limited to single dataprovider.")
                .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.path).description("The id of the Codespace to limit data to").dataType("string").endParam()

                .post("/anshar/subscribe").to("direct:process.subscription.request")
                .description("Backwards compatible endpoint used for SIRI SubscriptionRequest.")

                .post("/anshar/ws/subscribe").to("direct:process.soap.subscription.request")
                .description("Backwards compatible endpoint used for SIRI SubscriptionRequest.")

                .post("/anshar/subscribe/{" + PARAM_DATASET_ID + "}").to("direct:process.subscription.request")
                .description("Backwards compatible endpoint used for SIRI SubscriptionRequest limited to single dataprovider.")
                .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.path).description("The id of the Codespace to limit data to").dataType("string").endParam()


                .post("/anshar/siri").to("direct:process.unique.entry.point")
                .description("Backwards compatible endpoint used for SIRI subscriptions or requests")

                .post("/anshar/ws/siri").to("direct:process.soap.unique.entry.point")
                .description("Backwards compatible endpoint used for SIRI subscriptions or requests")




                .post("/services").to("direct:process.service.request")
                .apiDocs(false)
                .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response (SIRI ET and VM)").dataType("string").endParam()
                .description("Endpoint used for SIRI ServiceRequest.")

                .post("/services/{" + PARAM_DATASET_ID + "}").to("direct:process.service.request")
                .description("Endpoint used for SIRI ServiceRequest limited to single dataprovider.")
                .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.path).description("The id of the Codespace to limit data to").dataType("string").endParam()

                // Endpoints that returned cached data
                .post("/services-cache").to("direct:process.service.request.cache")
                .apiDocs(false)
                .post("/services-cache/{" + PARAM_DATASET_ID + "}").to("direct:process.service.request.cache")
                .apiDocs(false)


                .post("/subscribe").to("direct:process.subscription.request")
                .apiDocs(false)
                .description("Endpoint used for SIRI SubscriptionRequest.")

                .post("/subscribe/{" + PARAM_DATASET_ID + "}").to("direct:process.subscription.request")
                .apiDocs(false)
                .description("Endpoint used for SIRI SubscriptionRequest limited to single dataprovider.")
                .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.path).description("The id of the Codespace to limit data to").dataType("string").endParam()

                .post("/{version}/{type}/{vendor}/{" + PARAM_SUBSCRIPTION_ID + "}").to("direct:process.incoming.request")
                .apiDocs(false)

                .post("/{version}/{type}/{vendor}/{" + PARAM_SUBSCRIPTION_ID + "}/{service}").to("direct:process.incoming.request")
                .apiDocs(false)

                .post("/{version}/{type}/{vendor}/{" + PARAM_SUBSCRIPTION_ID + "}/{service}/{operation}").to("direct:process.incoming.request")
                .apiDocs(false)
                .description("Generated dynamically when creating Subscription. Endpoint for incoming data")
                .param().required(false).name("service").endParam()
                .param().required(false).name("operation").endParam()
        ;


        from("direct:handle.soap.request")
                .process(e -> e.getIn().setHeader(TRANSFORM_SOAP, TRANSFORM_SOAP))
                .to("direct:transform.siri")
                .to("direct:process.service.request");


        from("direct:set.soap.header.and.transform")
                .process(e -> e.getIn().setHeader(TRANSFORM_SOAP, TRANSFORM_SOAP))
                .to("direct:transform.siri");


        from("direct:process.incoming.request")
                .removeHeaders("<Siri*") //Since Camel 3, entire body is also included as header
                .to("log:incoming:" + getClass().getSimpleName() + "?showAll=true&multiline=true&showStreams=true&level=DEBUG")
                .choice()
                .when(e -> subscriptionExistsAndIsActive(e))
                //Valid subscription
                .to("seda:async.process.request?waitForTaskToComplete=Never")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .setBody(constant(null))
                .endChoice()
                .otherwise()
                // Invalid subscription
                .log("Ignoring incoming delivery for invalid subscription")
                .removeHeaders("*")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("403")) //403 Forbidden
                .setBody(constant("Subscription is not valid"))
                .endChoice()
                .routeId("process.incoming")
        ;

        from("seda:async.process.request?concurrentConsumers=20")
                .convertBodyTo(String.class)
                .process(p -> {
                    p.getMessage().setBody(p.getIn().getBody());
                    p.getMessage().setHeaders(p.getIn().getHeaders());
                    p.getMessage().setHeader(INTERNAL_SIRI_DATA_TYPE, getSubscriptionDataType(p));
                })
                .to("direct:enqueue.message")
                .routeId("async.process.incoming")
        ;

        from("direct:process.soap.subscription.request")
                .process(e -> e.getIn().setHeader(TRANSFORM_SOAP, TRANSFORM_SOAP))
                .to("direct:transform.siri")
                .process(e -> log.info(" transformé:" + e.getIn().getBody(String.class)))
                .to("direct:process.subscription.request");


        from("direct:process.unique.entry.point")
                .choice()
                .when(this::isSubscriptionMessage)
                    .to("direct:process.subscription.request")
                .otherwise()
                    .to("direct:process.service.request")
                .endChoice()
                .routeId("process.unique.entry.point");


        from("direct:process.soap.unique.entry.point")
                .choice()
                .when(this::isSubscriptionMessage)
                    .to("direct:process.soap.subscription.request")
                .otherwise()
                    .to("direct:handle.soap.request")
                .endChoice()
                .routeId("process.soap.unique.entry.point");


        from("direct:process.subscription.request")
                .to("log:subRequest:" + getClass().getSimpleName() + "?showAll=true&multiline=true&showStreams=true")
                .choice()
                .when(e -> isTrackingHeaderBlocked(e))
                .to("direct:anshar.blocked.tracking.header.response")
                .endChoice()
                .when(e -> isTrackingHeaderAcceptable(e))
                .choice()
                .when().xpath("/siri:Siri/siri:SubscriptionRequest/siri:VehicleMonitoringSubscriptionRequest", nameSpace)
                .to("direct:process.vm.subscription.request")
                .when().xpath("/siri:Siri/siri:SubscriptionRequest/siri:SituationExchangeSubscriptionRequest", nameSpace)
                .to("direct:process.sx.subscription.request")
                .when().xpath("/siri:Siri/siri:SubscriptionRequest/siri:EstimatedTimetableSubscriptionRequest", nameSpace)
                .to("direct:process.et.subscription.request")
                .when().xpath("/siri:Siri/siri:SubscriptionRequest/siri:StopMonitoringSubscriptionRequest", nameSpace)
                .to("direct:process.sm.subscription.request")
                .when().xpath("/siri:Siri/siri:SubscriptionRequest/siri:GeneralMessageSubscriptionRequest", nameSpace)
                .to("direct:process.gm.subscription.request")
                .when().xpath("/siri:Siri/siri:SubscriptionRequest/siri:FacilityMonitoringSubscriptionRequest", nameSpace)
                .to("direct:process.fm.subscription.request")
                .when().xpath("/siri:Siri/siri:TerminateSubscriptionRequest", nameSpace)
                // Forwarding TerminateRequest to all data-instances
                .wireTap("direct:process.et.subscription.request")
                .wireTap("direct:process.vm.subscription.request")
                .wireTap("direct:process.sx.subscription.request")
                .wireTap("direct:process.sm.subscription.request")
                .wireTap("direct:process.gm.subscription.request")
                .wireTap("direct:process.fm.subscription.request")
                .to("direct:internal.handle.subscription") //Build response
                .endChoice()
                .otherwise()
                .to("direct:anshar.invalid.tracking.header.response")
                .routeId("process.subscription")
        ;

        from("direct:internal.handle.subscription")
                .process(p -> {
                    String datasetId = p.getIn().getHeader(PARAM_DATASET_ID, String.class);
                    String clientTrackingName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);

                    InputStream xml = p.getIn().getBody(InputStream.class);
                    String useOriginalId = (String) p.getIn().getHeader(PARAM_USE_ORIGINAL_ID);

                    if (StringUtils.isEmpty(useOriginalId)) {
                        useOriginalId = Boolean.toString(defaultUseOriginalId);
                    }

                    boolean soapTransformation = TRANSFORM_SOAP.equals(p.getIn().getHeader(TRANSFORM_SOAP));

                    IncomingSiriParameters incomingSiriParameters = new IncomingSiriParameters();
                    incomingSiriParameters.setIncomingSiriStream(xml);
                    incomingSiriParameters.setDatasetId(datasetId);
                    incomingSiriParameters.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy(useOriginalId, (String) p.getIn().getHeader(PARAM_USE_ALT_ID)));
                    incomingSiriParameters.setMaxSize(-1);
                    incomingSiriParameters.setClientTrackingName(clientTrackingName);
                    incomingSiriParameters.setSoapTransformation(soapTransformation);
                    incomingSiriParameters.setUseOriginalId(Boolean.valueOf(useOriginalId));

                    Siri response = handler.handleIncomingSiri(incomingSiriParameters);
                    if (response != null) {
                        logger.info("Returning SubscriptionResponse");

                        p.getOut().setBody(response);
                    }

                    p.getOut().setHeader(TRANSFORM_SOAP,p.getIn().getHeader(TRANSFORM_SOAP));

                })
                .choice()
                .when(e -> TRANSFORM_SOAP.equals(e.getIn().getHeader(TRANSFORM_SOAP)))
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .process(e->{
                    e.getIn().setHeader(Exchange.CONTENT_TYPE,MediaType.TEXT_XML);
                })
                .to("xslt-saxon:xsl/siri_raw_soap.xsl")
                .otherwise()
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .process(e->{
                    e.getIn().setHeader(Exchange.CONTENT_TYPE,MediaType.TEXT_XML);
                })
                .end()
                .to("log:subResponse:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
        ;

        from("direct:process.service.request")
                .choice()
                .when().xpath("/siri:Siri/siri:ServiceRequest/siri:VehicleMonitoringRequest", nameSpace)
                .to("direct:process.vm.service.request")
                .when().xpath("/siri:Siri/siri:ServiceRequest/siri:SituationExchangeRequest", nameSpace)
                .to("direct:process.sx.service.request")
                .when().xpath("/siri:Siri/siri:ServiceRequest/siri:EstimatedTimetableRequest", nameSpace)
                .to("direct:process.et.service.request")
                .when().xpath("/siri:Siri/siri:ServiceRequest/siri:StopMonitoringRequest", nameSpace)
                .to("direct:process.sm.service.request")
                .when().xpath("/siri:Siri/siri:ServiceRequest/siri:GeneralMessageRequest", nameSpace)
                .to("direct:process.sm.service.request")
                .when().xpath("/siri:Siri/siri:ServiceRequest/siri:FacilityMonitoringRequest", nameSpace)
                .to("direct:process.fm.service.request")
                .when().xpath("/siri:Siri/siri:StopPointsRequest", nameSpace)
                .to("direct:process.sm.service.request")
                .when().xpath("/siri:Siri/siri:LinesRequest", nameSpace)
                .to("direct:process.vm.service.request")
                .when().xpath("/siri:Siri/siri:CheckStatusRequest", nameSpace)
                .to("direct:internal.process.service.request")
                .endChoice()
        ;
        from("direct:internal.process.service.request")
                .to("log:serRequest:" + getClass().getSimpleName() + "?showAll=true&multiline=true&showStreams=true&level=DEBUG")
                .choice()
                .when(e -> isTrackingHeaderAcceptable(e))
                .process(p -> {
                    Message msg = p.getIn();

                    p.getOut().setHeaders(msg.getHeaders());

                    List<String> excludedIdList = getParameterValuesAsList(msg, PARAM_EXCLUDED_DATASET_ID);
                    String clientTrackingName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);

                    String datasetId = msg.getHeader(PARAM_DATASET_ID, String.class);

                    int maxSize = -1;
                    if (msg.getHeaders().containsKey(PARAM_MAX_SIZE)) {
                        maxSize = Integer.parseInt((String) msg.getHeader(PARAM_MAX_SIZE));
                    }

                    String useOriginalId = msg.getHeader(PARAM_USE_ORIGINAL_ID, String.class);
                    String useAltId = msg.getHeader(PARAM_USE_ALT_ID, String.class);

                    if (StringUtils.isEmpty(useOriginalId)) {
                        useOriginalId = Boolean.toString(defaultUseOriginalId);
                    }

                    IncomingSiriParameters incomingSiriParameters = new IncomingSiriParameters();
                    incomingSiriParameters.setIncomingSiriStream(msg.getBody(InputStream.class));
                    incomingSiriParameters.setDatasetId(datasetId);
                    incomingSiriParameters.setExcludedDatasetIdList(excludedIdList);
                    incomingSiriParameters.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy(useOriginalId, useAltId));
                    incomingSiriParameters.setMaxSize(maxSize);
                    incomingSiriParameters.setClientTrackingName(clientTrackingName);
                    incomingSiriParameters.setSoapTransformation(false);
                    incomingSiriParameters.setUseOriginalId(Boolean.valueOf(useOriginalId));

                    Siri response = handler.handleIncomingSiri(incomingSiriParameters);
                    if (response != null) {
                        logger.debug("Found ServiceRequest-response, streaming response");

                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

                        CustomSiriXml.toXml(response, null, byteArrayOutputStream);
                        p.getOut().setBody(byteArrayOutputStream.toString());
                    }
                })
                .choice()
                .when(e -> TRANSFORM_SOAP.equals(e.getIn().getHeader(TRANSFORM_SOAP)))
                .to("xslt-saxon:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .to("xslt-saxon:xsl/siri_14_20.xsl") // Convert SIRI raw request to SOAP version
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.TEXT_XML)) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .endChoice()
                .to("log:serResponse:" + getClass().getSimpleName() + "?showAll=true&multiline=true&showStreams=true&level=DEBUG")
                .otherwise()
                .to("direct:anshar.invalid.tracking.header.response")
                .routeId("process.service")
        ;

        from("direct:process.service.request.cache")
                .choice()
                .when().xpath("/siri:Siri/siri:ServiceRequest/siri:VehicleMonitoringRequest", nameSpace)
                .to("direct:process.vm.service.request.cache")
                .when().xpath("/siri:Siri/siri:ServiceRequest/siri:SituationExchangeRequest", nameSpace)
                .to("direct:process.sx.service.request.cache")
                .when().xpath("/siri:Siri/siri:ServiceRequest/siri:EstimatedTimetableRequest", nameSpace)
                .to("direct:process.et.service.request.cache")
                .when().xpath("/siri:Siri/siri:ServiceRequest/siri:StopMonitoringRequest", nameSpace)
                .to("direct:process.sm.service.request.cache")
                .when().xpath("/siri:Siri/siri:ServiceRequest/siri:FacilityMonitoringRequest", nameSpace)
                .to("direct:process.fm.service.request.cache")
                .endChoice()
        ;

        from("direct:internal.process.service.request.cache")
                .to("log:serRequest:" + getClass().getSimpleName() + "?showAll=true&multiline=true&showStreams=true")
                .process(p -> {
                    Message msg = p.getIn();

                    String datasetId = msg.getHeader(PARAM_DATASET_ID, String.class);
                    String clientTrackingName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);

                    Siri response = handler.handleSiriCacheRequest(msg.getBody(InputStream.class), datasetId, clientTrackingName);
                    if (response != null) {
                        logger.info("Found ServiceRequest-response, streaming response");
                    }
                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                    streamOutput(p, response, out);
                })
                .to("log:serResponse:" + getClass().getSimpleName() + "?showAll=true&multiline=true&showStreams=true")
                .routeId("process.service.cache")
        ;

    }

    private String getSubscriptionDataType(Exchange e) {
        String subscriptionId = e.getIn().getHeader(PARAM_SUBSCRIPTION_ID, String.class);
        if (subscriptionId == null || subscriptionId.isEmpty()) {
            return null;
        }
        SubscriptionSetup subscriptionSetup = subscriptionManager.get(subscriptionId);

        if (subscriptionSetup == null) {
            return null;
        }
        return subscriptionSetup.getSubscriptionType().name();
    }

    private boolean subscriptionExistsAndIsActive(Exchange e) {
        String subscriptionId = e.getIn().getHeader(PARAM_SUBSCRIPTION_ID, String.class);
        if (subscriptionId == null || subscriptionId.isEmpty()) {
            return false;
        }
        SubscriptionSetup subscriptionSetup = subscriptionManager.get(subscriptionId);

        if (subscriptionSetup == null) {
            return false;
        }

        boolean existsAndIsActive = (subscriptionManager.isSubscriptionRegistered(subscriptionId) &&
                subscriptionSetup.isActive());

        if (existsAndIsActive) {
            e.getOut().setHeaders(e.getIn().getHeaders());
            e.getOut().setBody(e.getIn().getBody());

            if (!"2.0".equals(subscriptionSetup.getVersion())) {
                e.getOut().setHeader(TRANSFORM_VERSION, TRANSFORM_VERSION);
            }

            if (subscriptionSetup.getServiceType() == SubscriptionSetup.ServiceType.SOAP) {
                e.getOut().setHeader(TRANSFORM_SOAP, TRANSFORM_SOAP);
            }
        }

        return existsAndIsActive;
    }
}
