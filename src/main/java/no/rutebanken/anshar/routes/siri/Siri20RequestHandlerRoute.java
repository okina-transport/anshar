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

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.xml.bind.UnmarshalException;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.DiscoverySubscription;
import no.rutebanken.anshar.config.IncomingSiriParameters;
import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.metrics.InboundTimeProcessor;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.ServiceNotSupportedException;
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.routes.outbound.CompressionFormat;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.OAuthConfigElement;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import no.rutebanken.anshar.util.CompressionUtil;
import no.rutebanken.anshar.util.SiriUtils;
import org.apache.camel.*;
import org.apache.camel.http.common.HttpMethods;
import org.apache.camel.model.rest.RestParamType;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static no.rutebanken.anshar.routes.BaseRouteBuilder.getRequestUrl;
import static no.rutebanken.anshar.routes.HttpParameter.*;
import static no.rutebanken.anshar.subscription.DiscoverySubscriptionCreator.ENDPOINT_URL_HEADER;
import static no.rutebanken.anshar.subscription.DiscoverySubscriptionCreator.SUBSCRIPTION_URL_HEADER;
import static org.springframework.http.HttpHeaders.ACCEPT_ENCODING;

@SuppressWarnings("unchecked")
@Service
@Configuration
public class Siri20RequestHandlerRoute extends RestRouteBuilder implements CamelContextAware {

    public static final String TRANSFORM_VERSION = "TRANSFORM_VERSION";
    public static final String TRANSFORM_SOAP = "TRANSFORM_SOAP";
    private static final Logger logger = LoggerFactory.getLogger(Siri20RequestHandlerRoute.class);
    private final NamespacePrefixMapper customNamespacePrefixMapper = new NamespacePrefixMapper() {
        @Override
        public String getPreferredPrefix(String arg0, String arg1, boolean arg2) {
            return "siri";
        }
    };
    private final Processor removeXsiType = (e) -> {
        String originalxml = e.getIn().getBody(String.class);
        String xmlWithoutXsiType = originalxml.replaceAll("xsi:type=\"SubscriptionRefStructure\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"", "");
        logger.debug("xmlWithoutXsiType:" + xmlWithoutXsiType);
        e.getIn().setBody(xmlWithoutXsiType);
    };
    private final Processor addSubscriptionUrlHeader = (e) -> {
        e.getIn().setHeader(SUBSCRIPTION_URL_HEADER, e.getIn().getBody(SubscriptionSetup.class).getUrlMap().get(RequestType.SUBSCRIBE));
    };
    private final Processor addRequestResponseSubscriptionUrlHeader = (e) -> {
        e.getIn().setHeader(SUBSCRIPTION_URL_HEADER, getRequestUrl(e.getIn().getBody(SubscriptionSetup.class)));
    };
    private final Processor addCustomHeaders = (e) -> {
        SubscriptionSetup sub = e.getIn().getBody(SubscriptionSetup.class);
        if (MapUtils.isNotEmpty(sub.getCustomHeaders())) {
            e.getIn().getHeaders().putAll(sub.getCustomHeaders());
        }
    };
    private final Processor oauthHeadersProcess = (e) -> {
        SubscriptionSetup subscriptionSetup = e.getIn().getBody(SubscriptionSetup.class);
        if (subscriptionSetup.getOauth2Config() != null) {
            e.getMessage().setHeader("oauth-client-id", subscriptionSetup.getOauth2Config().get(OAuthConfigElement.CLIENT_ID));
            e.getMessage().setHeader("oauth-client-secret", subscriptionSetup.getOauth2Config().get(OAuthConfigElement.CLIENT_SECRET));
            e.getMessage().setHeader("oauth-grant-type", subscriptionSetup.getOauth2Config().get(OAuthConfigElement.GRANT_TYPE));
            e.getMessage().setHeader("oauth-server", subscriptionSetup.getOauth2Config().get(OAuthConfigElement.SERVER));
            e.getMessage().setHeader("oauth-audience", subscriptionSetup.getOauth2Config().get(OAuthConfigElement.AUDIENCE));
        }
    };
    @Autowired
    private SubscriptionManager subscriptionManager;
    @Autowired
    private SiriHandler handler;
    @Autowired
    private AnsharConfiguration configuration;
    @Value("${default.use.original.id:false}")
    private boolean defaultUseOriginalId;

    @Value("${seda.max.concurrent.consumers:20}")
    private int sedaMaxConcurrentConsumers;


    // @formatter:off
    @Override
    public void configure() throws Exception {

        super.configure();

        System.setProperty("org.apache.camel.xmlconverter.documentBuilderFactory.feature" + ":"
                + "http://xml.org/sax/features/external-general-entities", "false");


        System.setProperty("org.apache.camel.xmlconverter.documentBuilderFactory.feature" + ":"
                + "http://apache.org/xml/features/nonvalidating/load-external-dtd", "false");

        System.setProperty("org.apache.camel.xmlconverter.documentBuilderFactory.feature" + ":"
                + "http://apache.org/xml/features/disallow-doctype-decl", "false");


        System.setProperty("org.apache.camel.xmlconverter.documentBuilderFactory.feature" + ":"
                + "http://xml.org/sax/features/external-parameter-entities", "false");



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

                .post("/anshar/subscribe").to("direct:process.subscription.request1")
                .description("Backwards compatible endpoint used for SIRI SubscriptionRequest.")

                .post("/anshar/ws/subscribe").to("direct:process.soap.subscription.request")
                .description("Backwards compatible endpoint used for SIRI SubscriptionRequest.")

                .post("/anshar/subscribe/{" + PARAM_DATASET_ID + "}").to("direct:process.subscription.request2")
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
                .post("/services-cache").to("direct:process.service.request.cache1")
                .apiDocs(false)
                .post("/services-cache/{" + PARAM_DATASET_ID + "}").to("direct:process.service.request.cache2")
                .apiDocs(false)


                .post("/subscribe").to("direct:process.subscription.request3")
                .apiDocs(false)
                .description("Endpoint used for SIRI SubscriptionRequest.")

                .post("/subscribe/{" + PARAM_DATASET_ID + "}").to("direct:process.subscription.request4")
                .apiDocs(false)
                .description("Endpoint used for SIRI SubscriptionRequest limited to single dataprovider.")
                .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.path).description("The id of the Codespace to limit data to").dataType("string").endParam()
                .post("/{version}/{type}/{vendor}/{" + PARAM_SUBSCRIPTION_ID + "}")
                .to("direct:process.incoming.request")
                .apiDocs(false)

                .post("/{version}/{type}/{vendor}/{" + PARAM_SUBSCRIPTION_ID + "}/{service}").to("direct:process.incoming.request1")
                .apiDocs(false)

                .post("/{version}/{type}/{vendor}/{" + PARAM_SUBSCRIPTION_ID + "}/{service}/{operation}").to("direct:process.incoming.request2")
                .apiDocs(false)
                .description("Generated dynamically when creating Subscription. Endpoint for incoming data")
                .param().required(false).name("service").endParam()
                .param().required(false).name("operation").endParam()
        ;


        from("direct:handle.soap.request")
                .routeId("handle.soap.request")
                .process(e -> e.getIn().setHeader(TRANSFORM_SOAP, TRANSFORM_SOAP))
                .to("direct:transform.siri")
                .to("direct:process.service.request");


        from("direct:set.soap.header.and.transform")
                .process(e -> e.getIn().setHeader(TRANSFORM_SOAP, TRANSFORM_SOAP))
                .to("direct:transform.siri");

        from("direct:process.incoming.request1")
                .routeId("process.incoming.request1")
                        .to("direct:process.incoming.request");

        from("direct:process.incoming.request2")
                .routeId("process.incoming.request2")
                .to("direct:process.incoming.request");

        from("direct:process.incoming.request")
                .threads(300)
                .maxPoolSize(300)
                .removeHeaders("<Siri*") //Since Camel 3, entire body is also included as header
                .choice()
                .when(e -> subscriptionExistsAndIsActive(e))
                    .convertBodyTo(String.class)
                    .process(InboundTimeProcessor::setInboundTime)
                    .process(p -> {
                        String msg = p.getIn().getBody(String.class);
                        String[] msgSplited = msg.split("Envelope");
                        if (msgSplited.length > 3) {
                            log.warn("Found multiple soap enveloppe in one msg. Before seda");
                        }})
                    //Valid subscription
                    .to("seda:async.process.request?size=100000&waitForTaskToComplete=Never")
                    //.wireTap("direct:async.process.request")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                    .setBody(constant(null))
                .endChoice()
                .otherwise()
                    // Invalid subscription
                    .log("Ignoring incoming delivery for invalid subscription")
                    .removeHeaders("*")
                    .setBody(constant("Subscription is not valid"))
                .endChoice()
                .routeId("process.incoming")
        ;

        from("seda:async.process.request?concurrentConsumers=" + sedaMaxConcurrentConsumers + "&limitConcurrentConsumers=false")
                .setExchangePattern(ExchangePattern.InOnly)
                .convertBodyTo(String.class)
                .process(p -> {
                    String msg = p.getIn().getBody(String.class);
                    String[] msgSplited = msg.split("Envelope");
                    if (msgSplited.length > 3) {
                        log.warn("Found multiple soap enveloppe in one msg. After seda");
                    }
                    p.getMessage().setBody(p.getIn().getBody());
                    p.getMessage().setHeaders(p.getIn().getHeaders());
                    p.getMessage().setHeader(INTERNAL_SIRI_DATA_TYPE, getSubscriptionDataType(p));

                    String msgFromGetMsg = p.getMessage().getBody(String.class);
                    String[] msgSplited2 = msgFromGetMsg.split("Envelope");
                    if (msgSplited2.length > 3) {
                        log.warn("Found multiple soap enveloppe in one msg. After getMessage");
                    }
                })
                .to("direct:enqueue.message")
                .routeId("async.process.incoming")
        ;

        from("direct:process.soap.subscription.request")
                .process(e -> e.getIn().setHeader(TRANSFORM_SOAP, TRANSFORM_SOAP))
                .to("direct:transform.siri")
                .process(e -> log.debug(" transformé:" + e.getIn().getBody(String.class)))
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

        from("direct:process.subscription.request1")
                .routeId("process.subscription.request1")
                .to("direct:process.subscription.request");

        from("direct:process.subscription.request2")
                .routeId("process.subscription.request2")
                .to("direct:process.subscription.request");

        from("direct:process.subscription.request3")
                .routeId("process.subscription.request3")
                .to("direct:process.subscription.request");

        from("direct:process.subscription.request4")
                .routeId("process.subscription.request4")
                .to("direct:process.subscription.request");

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
                .process(e->{
                    log.info("Received a terminate subscription request from outbound client:" + e.getIn().getBody(String.class));
                })
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
                    String acceptEncoding = p.getIn().getHeader(ACCEPT_ENCODING, String.class);
                    CompressionFormat acceptedEncoding = CompressionUtil.getCompressionFormatFromHeader(acceptEncoding);
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
                    incomingSiriParameters.setUseOriginalId(Boolean.parseBoolean(useOriginalId));
                    incomingSiriParameters.setCompressionFormat(acceptedEncoding);
                    incomingSiriParameters.setGmSIVSicAQuay(Boolean.parseBoolean(p.getIn().getHeader(PARAM_SIV_GM_SIC_A_QUAY, String.class)));
                    incomingSiriParameters.setMergePublishingActions(Boolean.parseBoolean(p.getIn().getHeader(PARAM_MERGE_PUBLISHING_ACTIONS, String.class)));

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
                .to("direct:process.gm.service.request")
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
                    .to("direct:internal.process.service.request.acceptable.header")
                .otherwise()
                    .to("direct:anshar.invalid.tracking.header.response")
                .routeId("process.service")
        ;

        from("direct:internal.process.service.request.acceptable.header")
                .routeId("internal.process.service.request.acceptable.header")
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

                    boolean isGmSIVSicAQuay = Boolean.parseBoolean(p.getIn().getHeader(PARAM_SIV_GM_SIC_A_QUAY, String.class));

                    Set<String> datasets = SiriUtils.generateDatasetListFromHeader(datasetId);
                    Pair<Siri, String> siriWithVersion = handleIncomingSiriWithMultipleDatasets(msg,datasets, excludedIdList, useOriginalId, useAltId, maxSize, clientTrackingName, isGmSIVSicAQuay);

                    Siri response = siriWithVersion.getLeft();
                    String version = siriWithVersion.getRight();


                    if (response != null) {
                        logger.debug("Found ServiceRequest-response, streaming response");

                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

                        if (!"2.1".equals(version)){
                            uk.org.siri.siri20.Siri siri20response = downgradeSiriVersion(response, version);
                            CustomSiriXml.toXml(siri20response, null, byteArrayOutputStream);
                        }else{
                            CustomSiriXml.toXml(response, null, byteArrayOutputStream);
                        }
                        p.getOut().setBody(byteArrayOutputStream.toString());
                    }
                })
                .choice()
                .when(e -> TRANSFORM_SOAP.equals(e.getIn().getHeader(TRANSFORM_SOAP)))
                .to("xslt-saxon:xsl/siri_raw_soap.xsl")
                // Convert SIRI raw request to SOAP version
                // .to("xslt-saxon:xsl/siri_14_20.xsl") // Convert SIRI raw request to SOAP version
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.TEXT_XML)) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .endChoice()
                .to("log:serResponse:" + getClass().getSimpleName() + "?showAll=true&multiline=true&showStreams=true&level=DEBUG");









        from("direct:process.service.request.cache1")
                .routeId("process.service.request.cache1")
                .to("direct:process.service.request.cache");

        from("direct:process.service.request.cache2")
                .routeId("process.service.request.cache2")
                .to("direct:process.service.request.cache");

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

        from("direct:siri.20.to.siri.rs.14.subscription.preprocess")
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader(Exchange.CONTENT_TYPE, simple("${body.contentType}")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.POST))
                .process(addSubscriptionUrlHeader)
                .process(addCustomHeaders)
                .bean(SiriObjectFactory.class, "createSubscriptionRequest")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .to("xslt-saxon:xsl/siri_20_14.xsl") // Convert from SIRI 2.0 to SIRI 1.4
                .end();

        from("direct:siri.20.to.siri.rs.20.request-response.preprocess")
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader(Exchange.CONTENT_TYPE, simple("${body.contentType}")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.POST))
                .process(addRequestResponseSubscriptionUrlHeader)
                .process(addCustomHeaders)
                .bean(SiriObjectFactory.class, "createServiceRequest")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .end();

        from("direct:siri.20.to.siri.rs.20.subscription.preprocess")
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .process(oauthHeadersProcess)
                .to("direct:oauth2.authorize")
                .setHeader(Exchange.CONTENT_TYPE, simple("${body.contentType}")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.POST))
                .process(addSubscriptionUrlHeader)
                .process(addCustomHeaders)
                .bean(SiriObjectFactory.class, "createSubscriptionRequest")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .end();

        from("direct:siri.20.to.siri.ws.14.request-response.preprocess")
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader(Exchange.CONTENT_TYPE, simple("${body.contentType}")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.POST))
                .process(addRequestResponseSubscriptionUrlHeader)
                .process(addCustomHeaders)
                .process(e -> {
                    e.getIn().setHeader("SOAPAction", getSoapAction(e.getIn().getBody(SubscriptionSetup.class)));
                })
                .setHeader("operatorNamespace", simple("${body.operatorNamespace}")) // Need to make SOAP request with endpoint specific element namespace
                .bean(SiriObjectFactory.class, "createServiceRequest")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .to("xslt-saxon:xsl/siri_20_14.xsl") // Convert SIRI raw request to SOAP version
                .to("xslt-saxon:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .removeHeader("operatorNamespace")
                .end();

        from("direct:siri.20.to.siri.ws.14.subscription.preprocess")
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader(Exchange.CONTENT_TYPE, simple("${body.contentType}")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.POST))
                .process(addSubscriptionUrlHeader)
                .process(addCustomHeaders)
                .setHeader("SOAPAction", constant("Subscribe"))
                .setHeader("operatorNamespace", simple("${body.operatorNamespace}")) // Need to make SOAP request with endpoint specific element namespace
                .bean(SiriObjectFactory.class, "createSubscriptionRequest")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat(customNamespacePrefixMapper))
                .to("xslt-saxon:xsl/siri_20_14.xsl") // Convert from SIRI 2.0 to SIRI 1.4
                .to("xslt-saxon:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .removeHeader("operatorNamespace")
                .end();

        from("direct:siri.20.to.siri.ws.20.request-response.preprocess")
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader(Exchange.CONTENT_TYPE, simple("${body.contentType}")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.POST))
                .process(addRequestResponseSubscriptionUrlHeader)
                .process(addCustomHeaders)
                .process(e -> {
                    e.getIn().setHeader("SOAPAction", getSoapAction(e.getIn().getBody(SubscriptionSetup.class)));
                })
                .setHeader(ENDPOINT_URL_HEADER, header(SUBSCRIPTION_URL_HEADER)) // Need to make SOAP request with endpoint specific element namespace
                .setHeader("operatorNamespace", simple("${body.operatorNamespace}")) // Need to make SOAP request with endpoint specific element namespace
                .bean(SiriObjectFactory.class, "createServiceRequest")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .process(e -> log.debug("========> Request Before transformed to soap siri : {}",e.getIn().getBody(String.class)))
                .setHeader("soapHeaderDisabled", constant(String.valueOf(configuration.isXslSoapHeaderDisabled())))
                .to("xslt-saxon:xsl/siri_raw_soap.xsl?allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory") // Convert SIRI raw request to SOAP version
                .to("xslt-saxon:xsl/siri_14_20.xsl?allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory") // Convert SIRI raw request to SOAP version
                .process(e -> log.debug("========> Request transformed to soap siri : {}", e.getIn().getBody(String.class)))
                .removeHeader(ENDPOINT_URL_HEADER)
                .removeHeader("operatorNamespace")
                .removeHeader("soapHeaderDisabled")
                .end();

        from("direct:siri.20.to.siri.ws.20.subscription.preprocess")
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader(Exchange.CONTENT_TYPE, simple("${body.contentType}")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.POST))
                .process(addSubscriptionUrlHeader)
                .process(addCustomHeaders)
                .setHeader("SOAPAction", constant("Subscribe"))
                .setHeader("operatorNamespace", simple("${body.operatorNamespace}")) // Need to make SOAP request with endpoint specific element namespace
                .setHeader("soapEnvelopeNamespace", simple("${body.soapenvNamespace}")) // Need to make SOAP request with endpoint specific element namespace
                .setHeader(ENDPOINT_URL_HEADER, header(SUBSCRIPTION_URL_HEADER)) // Need to make SOAP request with endpoint specific element namespace
                .bean(SiriObjectFactory.class, "createSubscriptionRequest")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat(customNamespacePrefixMapper))
                .process(removeXsiType)
                .to("xslt-saxon:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .to("xslt-saxon:xsl/siri_14_20.xsl") // Convert SIRI raw request to SOAP version
                .removeHeader(ENDPOINT_URL_HEADER)
                .removeHeader("operatorNamespace")
                .removeHeader("soapEnvelopeNamespace")
                .end();

        from("direct:siri.lite.to.siri.rs.20.request-response.preprocess")
            .removeHeaders("CamelHttp*")
            .setExchangePattern(ExchangePattern.InOut)
            .setHeader(Exchange.CONTENT_TYPE, simple("${body.contentType}"))
            .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.GET))
            .process(addRequestResponseSubscriptionUrlHeader)
            .process(addCustomHeaders)
            .setBody(constant(""))
            .end();
    }

    private Pair<Siri,String> handleIncomingSiriWithMultipleDatasets(Message msg, Set<String> datasets, List<String> excludedIdList, String useOriginalId, String useAltId, int maxSize, String clientTrackingName, boolean isGmSIVSicAQuay) throws UnmarshalException, IOException {
        InputStream originalStream = msg.getBody(InputStream.class);
        if (datasets.isEmpty()) {
            return handleIncomingSiriForSingleDataset(originalStream, null, excludedIdList, useOriginalId, useAltId, maxSize, clientTrackingName, isGmSIVSicAQuay);
        }

        Pair<Siri,String> globalResults = null;


        byte[] data = originalStream.readAllBytes();
        for (String dataset : datasets) {
            Pair<Siri,String> datasetResult = handleIncomingSiriForSingleDataset(new ByteArrayInputStream(data), dataset, excludedIdList, useOriginalId, useAltId, maxSize, clientTrackingName, isGmSIVSicAQuay);
            globalResults = mergeDatasetResult(globalResults, datasetResult);
        }
        return globalResults;
    }

    private Pair<Siri,String> mergeDatasetResult(Pair<Siri,String> globalResults, Pair<Siri,String> datasetResult) {
        if (globalResults == null){
            return datasetResult;
        }

        Siri siri = SiriUtils.mergeSiris(globalResults.getLeft(), datasetResult.getLeft());
        return Pair.of(siri, datasetResult.getRight());

    }

    private Pair<Siri, String> handleIncomingSiriForSingleDataset(InputStream incomingSiriStream, String datasetId, List<String> excludedIdList, String useOriginalId, String useAltId, int maxSize, String clientTrackingName, boolean isGmSIVSicAQuay)throws UnmarshalException{
        IncomingSiriParameters incomingSiriParameters = new IncomingSiriParameters();
        incomingSiriParameters.setIncomingSiriStream(incomingSiriStream);
        incomingSiriParameters.setDatasetId(datasetId);
        incomingSiriParameters.setExcludedDatasetIdList(excludedIdList);
        incomingSiriParameters.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy(useOriginalId, useAltId));
        incomingSiriParameters.setMaxSize(maxSize);
        incomingSiriParameters.setClientTrackingName(clientTrackingName);
        incomingSiriParameters.setSoapTransformation(false);
        incomingSiriParameters.setUseOriginalId(Boolean.valueOf(useOriginalId));
        incomingSiriParameters.setGmSIVSicAQuay(isGmSIVSicAQuay);
        Siri siriResponse = handler.handleIncomingSiri(incomingSiriParameters);
        return Pair.of(siriResponse,incomingSiriParameters.getVersion());
    }

    private String getSoapAction(SubscriptionSetup subscriptionSetup) throws ServiceNotSupportedException {
        if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE &&
                subscriptionSetup.isDataSupplyRequestForInitialDelivery()) {
            return "DataSupplyRequest";
        }
        if (subscriptionSetup.getSubscriptionType() == SiriDataType.ESTIMATED_TIMETABLE) {
            return "GetEstimatedTimetable";
        } else if (subscriptionSetup.getSubscriptionType() == SiriDataType.VEHICLE_MONITORING) {
            return "GetVehicleMonitoring";
        } else if (subscriptionSetup.getSubscriptionType() == SiriDataType.SITUATION_EXCHANGE) {
            return "GetSituationExchange";
        } else if (subscriptionSetup.getSubscriptionType() == SiriDataType.STOP_MONITORING) {
            return "GetStopMonitoring";
        } else if (subscriptionSetup.getSubscriptionType() == SiriDataType.GENERAL_MESSAGE) {
            return "GetGeneralMessage";
        }else {
            throw new ServiceNotSupportedException();
        }
    }

    private String getSubscriptionDataType(Exchange e) {
        String subscriptionId = e.getIn().getHeader(PARAM_SUBSCRIPTION_ID, String.class);
        if (subscriptionId == null || subscriptionId.isEmpty()) {
            return null;
        }

        Optional<DiscoverySubscription> discoverySubscriptionOpt = subscriptionManager.getDiscoverySubscription(subscriptionId);
        if (discoverySubscriptionOpt.isPresent()) {
            return discoverySubscriptionOpt.get().getDiscoveryType().name();
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


        Optional<DiscoverySubscription> discoverySubscriptionOpt = subscriptionManager.getDiscoverySubscription(subscriptionId);
        if (discoverySubscriptionOpt.isPresent()) {
            DiscoverySubscription discoverySub = discoverySubscriptionOpt.get();

            if (!"2.0".equals(discoverySub.getVersion())) {
                e.getMessage().setHeader(TRANSFORM_VERSION, TRANSFORM_VERSION);
            }

            if (discoverySub.getServiceType() == SubscriptionSetup.ServiceType.SOAP) {
                e.getMessage().setHeader(TRANSFORM_SOAP, TRANSFORM_SOAP);
            }

            e.getMessage().setHeaders(e.getIn().getHeaders());
            e.getMessage().setBody(e.getIn().getBody());

            return true;
        }

        SubscriptionSetup subscriptionSetup = subscriptionManager.get(subscriptionId);


        if (subscriptionSetup == null) {
            return false;
        }

        e.getMessage().setHeaders(e.getIn().getHeaders());
        e.getMessage().setBody(e.getIn().getBody());


        if (!"2.0".equals(subscriptionSetup.getVersion())) {
           e.getMessage().setHeader(TRANSFORM_VERSION, TRANSFORM_VERSION);
        }

        if (subscriptionSetup.getServiceType() == SubscriptionSetup.ServiceType.SOAP) {
           e.getMessage().setHeader(TRANSFORM_SOAP, TRANSFORM_SOAP);
        }

        return true;
    }


}
