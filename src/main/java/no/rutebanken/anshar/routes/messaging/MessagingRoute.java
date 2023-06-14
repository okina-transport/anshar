package no.rutebanken.anshar.routes.messaging;

import com.sun.xml.bind.v2.schemagen.xmlschema.SchemaTop;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.gtfsrt.ingesters.EstimatedTimetableIngester;
import no.rutebanken.anshar.gtfsrt.ingesters.SituationExchangeIngester;
import no.rutebanken.anshar.gtfsrt.ingesters.StopMonitoringIngester;
import no.rutebanken.anshar.gtfsrt.ingesters.VehicleMonitoringIngester;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.CamelRouteNames;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.admin.AdminRouteHelper;
import no.rutebanken.anshar.routes.external.ExternalDataHandler;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.validation.SiriXmlValidator;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.camel.util.CaseInsensitiveMap;
import org.entur.siri21.util.SiriXml;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;

import java.io.InputStream;
import java.util.Map;

import static no.rutebanken.anshar.routes.HttpParameter.INTERNAL_PUBLISH_TO_KAFKA_FOR_APC_ENRICHMENT;
import static no.rutebanken.anshar.routes.HttpParameter.INTERNAL_SIRI_DATA_TYPE;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_SUBSCRIPTION_ID;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_USE_ORIGINAL_ID;
import static no.rutebanken.anshar.routes.siri.Siri20RequestHandlerRoute.TRANSFORM_SOAP;
import static no.rutebanken.anshar.routes.siri.Siri20RequestHandlerRoute.TRANSFORM_VERSION;
import static no.rutebanken.anshar.routes.validation.validators.Constants.*;

@Service
public class MessagingRoute extends RestRouteBuilder {

    @Autowired
    AnsharConfiguration configuration;

    @Autowired
    private SiriHandler handler;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private PrometheusMetricsService metrics;

    @Autowired
    private SiriXmlValidator siriXmlValidator;

    @Autowired
    private AdminRouteHelper adminRouteHelper;

    @Override
    public void configure() throws Exception {

        String messageQueueCamelRoutePrefix = configuration.getMessageQueueCamelRoutePrefix();

        String queueConsumerParameters = "?concurrentConsumers="+configuration.getConcurrentConsumers();


        final String pubsubQueueSX = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_SX;
        final String pubsubQueueVM = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_VM;
        final String pubsubQueueET = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_ET;
        final String pubsubQueueSM = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_SM;
        final String pubsubQueueGM = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_GM;
        final String pubsubQueueDefault = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_DEFAULT;

        final String externalSiriSMQueue = messageQueueCamelRoutePrefix + "anshar.external.siri.sm.data";
        final String externalSiriSXQueue = messageQueueCamelRoutePrefix + "anshar.external.siri.sx.data";
        final String externalSiriVMQueue = messageQueueCamelRoutePrefix + "anshar.external.siri.vm.data";


        if (messageQueueCamelRoutePrefix.contains("direct")) {
            queueConsumerParameters = "";
        }


        from(messageQueueCamelRoutePrefix + GTFSRT_ET_QUEUE)
                .threads(2)
                .process(e->{
                    String datasetId = e.getMessage().getHeader(DATASET_ID_HEADER_NAME, String.class);
                    e.getIn().setHeader(DATASET_ID_HEADER_NAME, datasetId);

                    String url = e.getMessage().getHeader(URL_HEADER_NAME, String.class);
                    e.getIn().setHeader(URL_HEADER_NAME, url);

                })
                .to("direct:transform.siri")
                .bean(EstimatedTimetableIngester.class, "processIncomingETFromGTFSRT")
        ;

        from(messageQueueCamelRoutePrefix + GTFSRT_SM_QUEUE)
                .threads(3)
                .process(e->{
                    String datasetId = e.getMessage().getHeader(DATASET_ID_HEADER_NAME, String.class);
                    e.getIn().setHeader(DATASET_ID_HEADER_NAME, datasetId);

                    String url = e.getMessage().getHeader(URL_HEADER_NAME, String.class);
                    e.getIn().setHeader(URL_HEADER_NAME, url);

                })
                .to("direct:transform.siri")
                .bean(StopMonitoringIngester.class, "processIncomingSMFromGTFSRT")
        ;

        from(messageQueueCamelRoutePrefix + GTFSRT_SX_QUEUE)
                .threads(2)
                .process(e->{
                    String datasetId = e.getMessage().getHeader(DATASET_ID_HEADER_NAME, String.class);
                    e.getIn().setHeader(DATASET_ID_HEADER_NAME, datasetId);

                    String url = e.getMessage().getHeader(URL_HEADER_NAME, String.class);
                    e.getIn().setHeader(URL_HEADER_NAME, url);

                })
                .to("direct:transform.siri")
                .bean(SituationExchangeIngester.class, "processIncomingSXFromGTFSRT")
        ;

        from(messageQueueCamelRoutePrefix + GTFSRT_VM_QUEUE)
                .threads(3)
                .process(e->{
                    String datasetId = e.getMessage().getHeader(DATASET_ID_HEADER_NAME, String.class);
                    e.getIn().setHeader(DATASET_ID_HEADER_NAME, datasetId);

                    String url = e.getMessage().getHeader(URL_HEADER_NAME, String.class);
                    e.getIn().setHeader(URL_HEADER_NAME, url);

                })
                .to("direct:transform.siri")
                .bean(VehicleMonitoringIngester.class, "processIncomingVMFromGTFSRT")
        ;

        from(externalSiriSMQueue)
                .process(e->{
                    String datasetId = e.getMessage().getHeader(DATASET_ID_HEADER_NAME, String.class);
                    e.getIn().setHeader(DATASET_ID_HEADER_NAME, datasetId);

                    String url = e.getMessage().getHeader(URL_HEADER_NAME, String.class);
                    e.getIn().setHeader(URL_HEADER_NAME, url);
                })
                .to("direct:transform.siri")
                .bean(ExternalDataHandler.class, "processIncomingSiriSM")
                ;

        from(externalSiriSXQueue)
                .process(e->{
                    String datasetId = e.getMessage().getHeader(DATASET_ID_HEADER_NAME, String.class);
                    e.getIn().setHeader(DATASET_ID_HEADER_NAME, datasetId);

                    String url = e.getMessage().getHeader(URL_HEADER_NAME, String.class);
                    e.getIn().setHeader(URL_HEADER_NAME, url);
                })
                .to("direct:transform.siri")
                .bean(ExternalDataHandler.class, "processIncomingSiriSX")
        ;

        from(externalSiriVMQueue)
                .process(e->{
                    String datasetId = e.getMessage().getHeader(DATASET_ID_HEADER_NAME, String.class);
                    e.getIn().setHeader(DATASET_ID_HEADER_NAME,datasetId );

                    String url = e.getMessage().getHeader(URL_HEADER_NAME, String.class);
                    e.getIn().setHeader(URL_HEADER_NAME,url );
                })
                .to("direct:transform.siri")
                .bean(ExternalDataHandler.class, "processIncomingSiriVM")
        ;





        from("direct:process.message.synchronous")
                .convertBodyTo(String.class)
                .to("direct:transform.siri")
                .to("direct:" + CamelRouteNames.PROCESSOR_QUEUE_DEFAULT)
        ;

        from("direct:enqueue.message")
                .convertBodyTo(String.class)
                .to("direct:transform.siri")
                .choice()
                    .when(header(INTERNAL_SIRI_DATA_TYPE).isEqualTo(SiriDataType.ESTIMATED_TIMETABLE.name()))
                        .setHeader("target_topic", simple(pubsubQueueET))
                    .endChoice()
                    .when(header(INTERNAL_SIRI_DATA_TYPE).isEqualTo(SiriDataType.VEHICLE_MONITORING.name()))
                        .setHeader("target_topic", simple(pubsubQueueVM))
                    .endChoice()
                    .when(header(INTERNAL_SIRI_DATA_TYPE).isEqualTo(SiriDataType.SITUATION_EXCHANGE.name()))
                        .setHeader("target_topic", simple(pubsubQueueSX))
                    .endChoice()
                    .when(header(INTERNAL_SIRI_DATA_TYPE).isEqualTo(SiriDataType.STOP_MONITORING.name()))
                        .setHeader("target_topic", simple(pubsubQueueSM))
                    .endChoice()
                    .when(header(INTERNAL_SIRI_DATA_TYPE).isEqualTo(SiriDataType.GENERAL_MESSAGE.name()))
                    .setHeader("target_topic", simple(pubsubQueueGM))
                    .endChoice()
                    .otherwise()
                        // DataReadyNotification is processed immediately
                        .when().xpath("/siri:Siri/siri:DataReadyNotification", nameSpace)
                            .setHeader("target_topic", simple("direct:"+CamelRouteNames.FETCHED_DELIVERY_QUEUE))
                        .endChoice()
                        .otherwise()
                            .to("log:not_processed:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                        .end()
                    .end()
                .end()
                .removeHeaders("*", "subscriptionId", "breadcrumbId", "target_topic", "correlationId")
                .process(p -> {
                    p.getMessage().setHeader(INTERNAL_PUBLISH_TO_KAFKA_FOR_APC_ENRICHMENT, enrichSiriData(p));
                })
                .bean(subscriptionManager, "dataReceived(${header.subscriptionId})")
                .process(convertHeadersToAttributes)
                .to("direct:send.to.queue")
                .end()
                .routeId("add.to.queue")
        ;

        if (configuration.splitDataForProcessing()) {
            log.info("Application configured to split all SIRI-data before processing");
            from("direct:send.to.queue")
                    .autoStartup(true)
                    .choice()
                        .when(header(INTERNAL_PUBLISH_TO_KAFKA_FOR_APC_ENRICHMENT).isEqualTo(Boolean.TRUE))
                        .removeHeader(INTERNAL_PUBLISH_TO_KAFKA_FOR_APC_ENRICHMENT)
                        .log("Sending data to enrichment topic")
                        .to("direct:anshar.enrich.siri.et")
                    .endChoice()
                    .otherwise()
                        .log("Sending split data to topic ${header.target_topic}")
                        .to("xslt-saxon:xsl/split.xsl")
                        .split().tokenizeXML("Siri").streaming()
                        .to("direct:compress.jaxb")
                        .toD("${header.target_topic}")
                    .end()
            ;
        } else {

            from("direct:send.to.queue")
                    .autoStartup(true)
                    .choice()
                    .when(header(INTERNAL_PUBLISH_TO_KAFKA_FOR_APC_ENRICHMENT).isEqualTo(Boolean.TRUE))
                        .removeHeader(INTERNAL_PUBLISH_TO_KAFKA_FOR_APC_ENRICHMENT)
                        .log("Sending data to enrichment topic")
                        .to("direct:anshar.enrich.siri.et")
                    .otherwise()
                        .log("Sending data to topic ${header.target_topic}")
                        .to("direct:compress.jaxb")
                        .toD("${header.target_topic}")
                        .end()
            ;
        }

        from("direct:transform.siri")
                .to("direct:set.mdc.subscriptionId")
                .choice()
                    .when(header(TRANSFORM_SOAP).isEqualTo(simple(TRANSFORM_SOAP)))
                    .log(LoggingLevel.DEBUG, "Transforming SOAP")
                    .to("xslt-saxon:xsl/siri_soap_raw.xsl?allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory") // Extract SOAP version and convert to raw SIRI
                .endChoice()
                .end()
                .choice()
                    .when(header(TRANSFORM_VERSION).isEqualTo(simple(TRANSFORM_VERSION)))
                    .log("Transforming version")
                    .to("xslt-saxon:xsl/siri_14_20.xsl?allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory") // Convert from v1.4 to 2.0
                .endChoice()
                .end()
                .to("direct:process.mapping")
                .to("direct:format.xml")
                .to("direct:clear.mdc.subscriptionId")
        ;


        from("direct:process.mapping")
                .to("direct:set.mdc.subscriptionId")
                .process(p -> {

                    String subscriptionId =  p.getIn().getHeader("subscriptionId", String.class);
                    if (StringUtils.isNotEmpty(subscriptionId)){
                        SubscriptionSetup subscriptionSetup = subscriptionManager.get(p.getIn().getHeader("subscriptionId", String.class));
                        Siri originalInput = siriXmlValidator.parseXml(subscriptionSetup, p.getIn().getBody(String.class));

                        Siri incoming = SiriValueTransformer.transform(originalInput, subscriptionSetup.getMappingAdapters(), false, true);
                        p.getMessage().setHeaders(p.getIn().getHeaders());
                        p.getMessage().setBody(SiriXml.toXml(incoming));
                    }
                })
                .to("direct:clear.mdc.subscriptionId")
        ;

        from("direct:format.xml")
            .to("xslt-saxon:xsl/indent.xsl?allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory")
            .routeId("incoming.format.xml")
        ;

        // When shutdown has been triggered - stop processing data from pubsub
        Predicate readFromPubsub = exchange -> adminRouteHelper.isNotShuttingDown();

        if (configuration.processSX()) {
            from(pubsubQueueSX + queueConsumerParameters)
                    .process(convertAttributesToHeaders)
                    .to("direct:set.mdc.subscriptionId")
                    .choice()
                        .when(readFromPubsub)
                            .log("Processing data from " + pubsubQueueSX)
                            .to("direct:decompress.jaxb")
                            .to("direct:process.queue.default.async")
                        .endChoice()
                    .end()
                    .to("direct:clear.mdc.subscriptionId")
                    .startupOrder(100004)
                    .routeId("incoming.transform.sx")
            ;
        }

        if (configuration.processVM()) {
            from(pubsubQueueVM + queueConsumerParameters)
                    .process(convertAttributesToHeaders)
                    .to("direct:set.mdc.subscriptionId")
                    .choice()
                        .when(readFromPubsub)
                            .log("Processing data from " + pubsubQueueVM)
                            .to("direct:decompress.jaxb")
                            .to("direct:process.queue.default.async")
                        .endChoice()
                    .end()
                    .to("direct:clear.mdc.subscriptionId")
                    .startupOrder(100003)
                    .routeId("incoming.transform.vm")
            ;
        }

        if (configuration.processET()) {
            from(pubsubQueueET + queueConsumerParameters)
                    .process(convertAttributesToHeaders)
                    .to("direct:set.mdc.subscriptionId")
                    .choice()
                        .when(readFromPubsub)
                            .log("Processing data from " + pubsubQueueET)
                            .to("direct:decompress.jaxb")
                            .to("direct:process.queue.default.async")
                        .endChoice()
                    .end()
                    .to("direct:clear.mdc.subscriptionId")
                    .startupOrder(100002)
                    .routeId("incoming.transform.et")
            ;
        }

        if (configuration.processSM()) {
            from(pubsubQueueSM + queueConsumerParameters)
                    .choice().when(readFromPubsub)
                    .to("direct:decompress.jaxb")
                    //.log("Processing data from " + pubsubQueueSM + ", size ${header.Content-Length}")
                    .wireTap("direct:" + CamelRouteNames.PROCESSOR_QUEUE_DEFAULT)
                    .endChoice()
                    .startupOrder(100001)
                    .routeId("incoming.transform.sm")
            ;
        }

        from("direct:process.queue.default.async")
                .wireTap("direct:" + CamelRouteNames.PROCESSOR_QUEUE_DEFAULT)
                .routeId("process.queue.default.async")
        ;

        from("direct:" + CamelRouteNames.PROCESSOR_QUEUE_DEFAULT)
                .to("direct:set.mdc.subscriptionId")
                .process(p -> {

                    String subscriptionId = p.getIn().getHeader("subscriptionId", String.class);
                    String datasetId = null;

                    InputStream xml = p.getIn().getBody(InputStream.class);
                    String useOriginalId = p.getIn().getHeader(PARAM_USE_ORIGINAL_ID, String.class);
                    String useAltId = p.getIn().getHeader(PARAM_USE_ALT_ID, String.class);
                    String clientTrackingName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);

                    handler.handleIncomingSiri(subscriptionId, xml, datasetId, SiriHandler.getIdMappingPolicy(useOriginalId, useAltId), -1, clientTrackingName);

                })
                .to("direct:clear.mdc.subscriptionId")
                .routeId("incoming.processor.default")
        ;

        from("direct:" + CamelRouteNames.FETCHED_DELIVERY_QUEUE)
                .to("direct:set.mdc.subscriptionId")
                .log("Processing fetched delivery")
                .process(p -> {
                    String routeName = null;

                    String subscriptionId = p.getIn().getHeader("subscriptionId", String.class);

                    SubscriptionSetup subscription = subscriptionManager.get(subscriptionId);
                    if (subscription != null) {
                        routeName = subscription.getServiceRequestRouteName();
                    }

                    p.getOut().setHeader("routename", routeName);

                })
                .choice()
                    .when(header("routename").isNotNull())
                        .toD("direct:${header.routename}")
                    .endChoice()
                .end()
                .to("direct:clear.mdc.subscriptionId")
                .routeId("incoming.processor.fetched_delivery")
        ;
    }


    private Boolean enrichSiriData(Exchange e) {
        String subscriptionId = e.getIn().getHeader(PARAM_SUBSCRIPTION_ID, String.class);
        if (subscriptionId == null || subscriptionId.isEmpty()) {
            return false;
        }
        SubscriptionSetup subscriptionSetup = subscriptionManager.get(subscriptionId);

        if (subscriptionSetup == null) {
            return false;
        }
        return subscriptionSetup.enrichSiriData();
    }
}
