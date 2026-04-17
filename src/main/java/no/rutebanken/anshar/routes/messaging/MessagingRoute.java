package no.rutebanken.anshar.routes.messaging;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.DiscoverySubscription;
import no.rutebanken.anshar.config.IncomingSiriParameters;
import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.data.util.SOAPSplitProcessor;
import no.rutebanken.anshar.data.util.TimingTracer;
import no.rutebanken.anshar.gtfsrt.ingesters.EstimatedTimetableIngester;
import no.rutebanken.anshar.gtfsrt.ingesters.SituationExchangeIngester;
import no.rutebanken.anshar.gtfsrt.ingesters.StopMonitoringIngester;
import no.rutebanken.anshar.gtfsrt.ingesters.VehicleMonitoringIngester;
import no.rutebanken.anshar.metrics.InboundTimeProcessor;
import no.rutebanken.anshar.routes.CamelRouteNames;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.admin.AdminRouteHelper;
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.routes.external.ExternalDataHandler;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.transformer.SiriJsonTransformer;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.validation.SiriXmlValidator;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.entur.siri21.util.SiriXml;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static no.rutebanken.anshar.routes.HttpParameter.*;
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
    private ServerSubscriptionManager outboundSubscriptionManager;

    @Autowired
    private SiriXmlValidator siriXmlValidator;


    @Autowired
    private AdminRouteHelper adminRouteHelper;

    @Autowired
    private SOAPSplitProcessor soapSplitProcessor;


    @Value("${default.use.original.id:false}")
    private boolean defaultUseOriginalId;

    @Value("${anshar.external.sm.queue}")
    private String externalSMQueue;

    @Value("${anshar.external.et.queue}")
    private String externalETQueue;

    @Value("${anshar.internal.gtfsrt.stop.monitoring}")
    private String internalGtfsrtSMQueue;


    @Value("${anshar.initial.delivery.estimated.timetables.queue.name}")
    private String initialDeliveryETQueueName;

    @Value("${anshar.initial.delivery.stop.monitoring.queue.name}")
    private String initialDeliverySMQueueName;

    @Value("${anshar.initial.delivery.general.message.queue.name}")
    private String initialDeliveryGMQueueName;

    @Value("${anshar.initial.delivery.facility.monitoring.queue.name}")
    private String initialDeliveryFMQueueName;

    @Value("${anshar.initial.delivery.situation.exchange.queue.name}")
    private String initialDeliverySXQueueName;

    @Value("${anshar.initial.delivery.vehicle.monitoring.queue.name}")
    private String initialDeliveryVMQueueName;

    private Map<String, SubscriptionSetup> discoveryFirstChild = new HashMap<>();

    @Value("${anshar.external.sm.threads:200}")
    private int externalSMThreads;


    @Override
    // @formatter:off
    public void configure() throws Exception {

        String messageQueueCamelRoutePrefix = configuration.getMessageQueueCamelRoutePrefix();

        String queueConsumerParameters = "?concurrentConsumers=" + configuration.getConcurrentConsumers();


        final String pubsubQueueSX = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_SX;
        final String pubsubQueueVM = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_VM;
        final String pubsubQueueET = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_ET;
        final String pubsubQueueSM = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_SM;
        final String pubsubQueueGM = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_GM;
        final String pubsubQueueFM = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_FM;
        final String pubsubQueueDefault = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_DEFAULT;

        final String externalSiriSMQueue = messageQueueCamelRoutePrefix + externalSMQueue;
        final String externalSiriSXQueue = messageQueueCamelRoutePrefix + "anshar.external.siri.sx.data";
        final String externalSiriVMQueue = messageQueueCamelRoutePrefix + "anshar.external.siri.vm.data";
        final String externalSiriETQueue = messageQueueCamelRoutePrefix + externalETQueue;


        if (messageQueueCamelRoutePrefix.contains("direct")) {
            queueConsumerParameters = "";
        }

        onException(IllegalStateException.class)
                .process(e->{
                    log.error("error msg ===>" +  e.getIn().getBody(String.class));
                })
        ;


        from(messageQueueCamelRoutePrefix + GTFSRT_ET_QUEUE)
                .routeId("gtfsrt.et.queue")
                .threads(100)
                .maxPoolSize(100)
                .process(InboundTimeProcessor::setInboundTime)
                .process(e -> {
                    String datasetId = e.getMessage().getHeader(DATASET_ID_HEADER_NAME, String.class);
                    e.getIn().setHeader(DATASET_ID_HEADER_NAME, datasetId);

                    String url = e.getMessage().getHeader(URL_HEADER_NAME, String.class);
                    e.getIn().setHeader(URL_HEADER_NAME, url);

                })
                .to("direct:transform.siri")
                .bean(EstimatedTimetableIngester.class, "processIncomingETFromGTFSRT")
        ;

        from(internalGtfsrtSMQueue)
                .routeId("gtfsrt.sm.queue")
                .threads(100)
                .maxPoolSize(100)
                .process(InboundTimeProcessor::setInboundTime)
                .process(e -> {
                    String datasetId = e.getMessage().getHeader(DATASET_ID_HEADER_NAME, String.class);
                    e.getIn().setHeader(DATASET_ID_HEADER_NAME, datasetId);

                    String url = e.getMessage().getHeader(URL_HEADER_NAME, String.class);
                    e.getIn().setHeader(URL_HEADER_NAME, url);

                })
                .to("direct:transform.siri")
                .bean(StopMonitoringIngester.class, "processIncomingSMFromGTFSRT")
        ;

        from(messageQueueCamelRoutePrefix + GTFSRT_SX_QUEUE)
                .routeId("gtfsrt.sx.queue")
                .threads(2)
                .process(InboundTimeProcessor::setInboundTime)
                .process(e -> {
                    String datasetId = e.getMessage().getHeader(DATASET_ID_HEADER_NAME, String.class);
                    e.getIn().setHeader(DATASET_ID_HEADER_NAME, datasetId);

                    String url = e.getMessage().getHeader(URL_HEADER_NAME, String.class);
                    e.getIn().setHeader(URL_HEADER_NAME, url);

                })
                .to("direct:transform.siri")
                .bean(SituationExchangeIngester.class, "processIncomingSXFromGTFSRT")
        ;

        from(messageQueueCamelRoutePrefix + GTFSRT_VM_QUEUE )
                .routeId("gtfsrt.vm.queue")
                .threads(100)
                .process(InboundTimeProcessor::setInboundTime)
                .maxPoolSize(100)
                .process(e -> {
                    String datasetId = e.getMessage().getHeader(DATASET_ID_HEADER_NAME, String.class);
                    e.getIn().setHeader(DATASET_ID_HEADER_NAME, datasetId);

                    String url = e.getMessage().getHeader(URL_HEADER_NAME, String.class);
                    e.getIn().setHeader(URL_HEADER_NAME, url);

                })
                .to("direct:transform.siri")
                .bean(VehicleMonitoringIngester.class, "processIncomingVMFromGTFSRT")
        ;

        from(externalSiriSMQueue)
                .routeId("external.siri.sm.queue")
                .threads(externalSMThreads)
                .maxPoolSize(externalSMThreads)
                .process(InboundTimeProcessor::setInboundTime)
                .process(e -> {
                    String datasetId = e.getMessage().getHeader(DATASET_ID_HEADER_NAME, String.class);
                    e.getIn().setHeader(DATASET_ID_HEADER_NAME, datasetId);

                    String url = e.getMessage().getHeader(URL_HEADER_NAME, String.class);
                    e.getIn().setHeader(URL_HEADER_NAME, url);
                })
                .to("direct:transform.siri")
                .bean(ExternalDataHandler.class, "processIncomingSiriSM")
        ;

        from(externalSiriETQueue)
                .routeId("external.siri.et.queue")
                .process(InboundTimeProcessor::setInboundTime)
                .process(e -> {
                    String datasetId = e.getMessage().getHeader(DATASET_ID_HEADER_NAME, String.class);
                    e.getIn().setHeader(DATASET_ID_HEADER_NAME, datasetId);

                    String url = e.getMessage().getHeader(URL_HEADER_NAME, String.class);
                    e.getIn().setHeader(URL_HEADER_NAME, url);
                })
                .to("direct:transform.siri")
                .bean(ExternalDataHandler.class, "processIncomingSiriET")
        ;

        from(externalSiriSXQueue)
                .routeId("external.siri.sx.queue")
                .process(InboundTimeProcessor::setInboundTime)
                .process(e -> {
                    String datasetId = e.getMessage().getHeader(DATASET_ID_HEADER_NAME, String.class);
                    e.getIn().setHeader(DATASET_ID_HEADER_NAME, datasetId);

                    String url = e.getMessage().getHeader(URL_HEADER_NAME, String.class);
                    e.getIn().setHeader(URL_HEADER_NAME, url);
                })
                .to("direct:transform.siri")
                .bean(ExternalDataHandler.class, "processIncomingSiriSX")
        ;

        from(externalSiriVMQueue)
                .routeId("external.siri.vm.queue")
                .process(InboundTimeProcessor::setInboundTime)
                .process(e -> {
                    String datasetId = e.getMessage().getHeader(DATASET_ID_HEADER_NAME, String.class);
                    e.getIn().setHeader(DATASET_ID_HEADER_NAME, datasetId);

                    String url = e.getMessage().getHeader(URL_HEADER_NAME, String.class);
                    e.getIn().setHeader(URL_HEADER_NAME, url);
                })
                .to("direct:transform.siri")
                .bean(ExternalDataHandler.class, "processIncomingSiriVM")
        ;


        from("direct:process.message.synchronous")
                .routeId("process.message.synchronous")
                .convertBodyTo(String.class)
                .to("direct:transform.siri")
                .to("direct:" + CamelRouteNames.PROCESSOR_QUEUE_DEFAULT)
        ;


        from("direct:enqueue.message")
                .routeId("enqueue.message")
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
                    .when(header(INTERNAL_SIRI_DATA_TYPE).isEqualTo(SiriDataType.FACILITY_MONITORING.name()))
                        .setHeader("target_topic", simple(pubsubQueueFM))
                .endChoice()
                .otherwise()
                    .choice()
                        // DataReadyNotification is processed immediately
                        .when().xpath("/siri:Siri/siri:DataReadyNotification", nameSpace)
                            .setHeader("target_topic", simple("direct:" + CamelRouteNames.FETCHED_DELIVERY_QUEUE))
                        .endChoice()
                        .otherwise()
                            .to("log:not_processed:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                            .end()
                            .end()
                            .end()
                            .removeHeaders("*", "subscriptionId", "breadcrumbId", "target_topic", INBOUND_TIME_HEADER_NAME)
                            .to("direct:compress.jaxb")
                            .toD("${header.target_topic}?deliveryMode=1")
                .end()
        ;




        from("direct:handleSiriLiteResponse")
                .choice()
                    .when(header(INTERNAL_SIRI_DATA_TYPE).isEqualTo(SiriDataType.VEHICLE_MONITORING.name()))
                        .setHeader("target_topic", simple(pubsubQueueVM))
                    .when(header(INTERNAL_SIRI_DATA_TYPE).isEqualTo(SiriDataType.SITUATION_EXCHANGE.name()))
                        .setHeader("target_topic", simple(pubsubQueueSX))
                    .when(header(INTERNAL_SIRI_DATA_TYPE).isEqualTo(SiriDataType.STOP_MONITORING.name()))
                        .setHeader("target_topic", simple(pubsubQueueSM))
                    .when(header(INTERNAL_SIRI_DATA_TYPE).isEqualTo(SiriDataType.ESTIMATED_TIMETABLE.name()))
                        .setHeader("target_topic", simple(pubsubQueueET))
                    .when(header(INTERNAL_SIRI_DATA_TYPE).isEqualTo(SiriDataType.GENERAL_MESSAGE.name()))
                        .setHeader("target_topic", simple(pubsubQueueGM))
                    .when(header(INTERNAL_SIRI_DATA_TYPE).isEqualTo(SiriDataType.FACILITY_MONITORING.name()))
                        .setHeader("target_topic", simple(pubsubQueueFM))
                    .otherwise()
                        .log(LoggingLevel.ERROR, "Siri ignored. type not handled: ${header.InternalSiriDatatype}")
                .end()

                .choice()
                    .when(header(SUBSCRIPTION_MODE).isEqualTo(SubscriptionSetup.SubscriptionMode.LITE.name()))
                    .to("direct:transformSiriJsonToVM")
                .end()
                .to("direct:process.mapping")
                .to("direct:" + CamelRouteNames.PROCESSOR_QUEUE_DEFAULT)
                .routeId("handleSiriLiteResponse");


        from("direct:transformSiriJsonToVM")
                .process(p -> {

                    Siri incomingSiri = SiriJsonTransformer.convertJsonVMtoSiri(p.getIn().getBody(String.class));
                    p.getMessage().setBody(SiriXml.toXml(incomingSiri));
                })
                .routeId("TransformSiriJsonToVM");


        from("direct:transform.siri")
                .routeId("transform.siri")
                .choice()
                .when(header(TRANSFORM_SOAP).isEqualTo(simple(TRANSFORM_SOAP)))
                    .log(LoggingLevel.DEBUG, "Transforming SOAP")
                    .process(soapSplitProcessor)
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
                .choice()
                    .when(body().isNull()).stop()
                .end()
                .to("direct:format.xml");


        from("direct:process.mapping")
                .process(p -> {

                    String subscriptionId = p.getIn().getHeader("subscriptionId", String.class);
                    if (StringUtils.isNotEmpty(subscriptionId)) {
                        Optional<DiscoverySubscription> discoveryOpt = subscriptionManager.getDiscoverySubscription(subscriptionId);
                        if (discoveryOpt.isPresent()) {
                            DiscoverySubscription discoverySubscription = discoveryOpt.get();
                            Siri originalInput =   CustomSiriXml.parseXml(p.getIn().getBody(String.class));
                            p.getMessage().setHeaders(p.getIn().getHeaders());


                            if (!discoveryFirstChild.containsKey(subscriptionId)){
                                List<SubscriptionSetup> childrenSubscriptions = subscriptionManager.getChildSubscriptions(discoverySubscription);
                                if (CollectionUtils.isNotEmpty(childrenSubscriptions)){
                                    discoveryFirstChild.put(subscriptionId, childrenSubscriptions.getFirst());
                                }
                            }

                            SubscriptionSetup childSubscription =  discoveryFirstChild.get(subscriptionId);

                            List<ValueAdapter> adapters = subscriptionManager.getValueAdaptersFromId(childSubscription, discoverySubscription.getMappingAdapterId());
                            Siri incoming = SiriValueTransformer.transform(originalInput, adapters, false, true);
                            p.getMessage().setHeaders(p.getIn().getHeaders());
                            p.getMessage().setBody(SiriXml.toXml(incoming));
                        }else{
                            SubscriptionSetup subscriptionSetup = subscriptionManager.get(p.getIn().getHeader("subscriptionId", String.class));
                            if (subscriptionSetup == null) {
                                p.getMessage().setBody(null);
                                return;
                            }
                            Siri originalInput = siriXmlValidator.parseXml(subscriptionSetup, p.getIn().getBody(String.class));
                            Siri incoming = SiriValueTransformer.transform(originalInput, subscriptionSetup.getMappingAdapters(), false, true);
                            p.getMessage().setHeaders(p.getIn().getHeaders());
                            p.getMessage().setBody(SiriXml.toXml(incoming));
                        }
                    }
                })
        ;

        from("direct:format.xml")
                .to("xslt-saxon:xsl/indent.xsl?allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory")
                .routeId("incoming.format.xml")
        ;

        // When shutdown has been triggered - stop processing data from pubsub
        Predicate readFromPubsub = exchange -> adminRouteHelper.isNotShuttingDown();

//        from(pubsubQueueDefault + queueConsumerParameters)
//            .choice().when(readFromPubsub)
//                .to("direct:decompress.jaxb")
//                .log("Processing data from " + pubsubQueueDefault + ", size ${header.Content-Length}")
//                .wireTap("direct:" + CamelRouteNames.PROCESSOR_QUEUE_DEFAULT)
//            .endChoice()
//            .startupOrder(100004)
//            .routeId("incoming.transform.default")
//        ;
        if (configuration.processSX()) {
            from(pubsubQueueSX + queueConsumerParameters)
                    .choice().when(readFromPubsub)
                    .log(LoggingLevel.DEBUG, "Processing data from " + pubsubQueueSX + ", size ${header.Content-Length}")
                    .to("direct:decompress.jaxb")
                    .to("direct:process.queue.default.async")
                    .endChoice()
                    .startupOrder(100004)
                    .routeId("incoming.transform.sx")
            ;

            from(initialDeliverySXQueueName)
                    .threads(5)
                    .maxPoolSize(5)
                    .bean(outboundSubscriptionManager, "generateAndSendInitialDelivery(${header.subscriptionId}, ${header.outboundIdMappingPolicy})")
                    .routeId("initial.delivery.situation.exchange");
        }

        if (configuration.processVM()) {
            from(pubsubQueueVM + queueConsumerParameters)
                    .choice().when(readFromPubsub)
                    .log(LoggingLevel.DEBUG,"Processing data from " + pubsubQueueVM + ", size ${header.Content-Length}")
                    .to("direct:decompress.jaxb")
                    .to("direct:process.queue.default.async")
                    .endChoice()
                    .startupOrder(100003)
                    .routeId("incoming.transform.vm")
            ;

            from(initialDeliveryVMQueueName )
                    .threads(5)
                    .maxPoolSize(5)
                    .bean(outboundSubscriptionManager, "generateAndSendInitialDelivery(${header.subscriptionId}, ${header.outboundIdMappingPolicy})")
                    .routeId("initial.delivery.vehicle.monitoring");
        }

        if (configuration.processET()) {
            from(pubsubQueueET + queueConsumerParameters)
                    .choice().when(readFromPubsub)
                    .log(LoggingLevel.DEBUG,"Processing data from " + pubsubQueueET + ", size ${header.Content-Length}")
                    .to("direct:decompress.jaxb")
                    .to("direct:process.queue.default.async")
                    .endChoice()
                    .startupOrder(100002)
                    .routeId("incoming.transform.et")
            ;

            from(initialDeliveryETQueueName )
                    .threads(5)
                    .maxPoolSize(5)
                    .bean(outboundSubscriptionManager, "generateAndSendInitialDelivery(${header.subscriptionId}, ${header.outboundIdMappingPolicy})")
                    .routeId("initial.delivery.estimated.timetables");
        }

        if (configuration.processSM()) {
            from(pubsubQueueSM + queueConsumerParameters)
                    .threads(400)
                    .maxPoolSize(400)
                    .choice().when(readFromPubsub)
                    .to("direct:decompress.jaxb")
                    .log(LoggingLevel.DEBUG,"Processing data from " + pubsubQueueSM + ", size ${header.Content-Length}")
                    .wireTap("direct:" + CamelRouteNames.PROCESSOR_QUEUE_DEFAULT)
                    .endChoice()
                    .startupOrder(100001)
                    .routeId("incoming.transform.sm")
            ;

            from(initialDeliverySMQueueName )
                    .threads(5)
                    .maxPoolSize(5)
                    .bean(outboundSubscriptionManager, "generateAndSendInitialDelivery(${header.subscriptionId}, ${header.outboundIdMappingPolicy})")
                    .routeId("initial.delivery.stop.monitoring");

        }


        if (configuration.processGM()) {
            from(pubsubQueueGM + queueConsumerParameters)
                    .choice().when(readFromPubsub)
                    .to("direct:decompress.jaxb")
                    .wireTap("direct:" + CamelRouteNames.PROCESSOR_QUEUE_DEFAULT)
                    .endChoice()
                    .startupOrder(100005)
                    .routeId("incoming.transform.gm")
            ;

            from(initialDeliveryGMQueueName)
                    .threads(5)
                    .maxPoolSize(5)
                    .bean(outboundSubscriptionManager, "generateAndSendInitialDelivery(${header.subscriptionId}, ${header.outboundIdMappingPolicy})")
                    .routeId("initial.delivery.general.message");
        }

        if (configuration.processFM()) {
            from(pubsubQueueFM + queueConsumerParameters)
                    .choice().when(readFromPubsub)
                    .to("direct:decompress.jaxb")
                    .wireTap("direct:" + CamelRouteNames.PROCESSOR_QUEUE_DEFAULT)
                    .endChoice()
                    .startupOrder(100006)
                    .routeId("incoming.transform.fm")
            ;

            from(initialDeliveryFMQueueName)
                    .threads(5)
                    .maxPoolSize(5)
                    .bean(outboundSubscriptionManager, "generateAndSendInitialDelivery(${header.subscriptionId}, ${header.outboundIdMappingPolicy})")
                    .routeId("initial.delivery.facility.monitoring");
        }

        from("direct:process.queue.default.async")
                .wireTap("direct:" + CamelRouteNames.PROCESSOR_QUEUE_DEFAULT)
                .routeId("process.queue.default.async")
        ;

        from("direct:" + CamelRouteNames.PROCESSOR_QUEUE_DEFAULT)
                .process(p -> {

                    String subscriptionId = p.getIn().getHeader("subscriptionId", String.class);
                    TimingTracer processorTT = new TimingTracer("processorTT-" + subscriptionId);
                    String datasetId = null;

                    InputStream xml = p.getIn().getBody(InputStream.class);
                    String useOriginalId = p.getIn().getHeader(PARAM_USE_ORIGINAL_ID, String.class);
                    String useAltId = p.getIn().getHeader(PARAM_USE_ALT_ID, String.class);
                    if (StringUtils.isEmpty(useOriginalId)){
                        useOriginalId = Boolean.toString(defaultUseOriginalId);
                    }
                    boolean isGmSIVSicAQuay = Boolean.parseBoolean(p.getIn().getHeader(PARAM_SIV_GM_SIC_A_QUAY, String.class));
                    String clientTrackingName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);

                    IncomingSiriParameters incomingSiriParameters = new IncomingSiriParameters();
                    incomingSiriParameters.setIncomingSiriStream(xml);
                    incomingSiriParameters.setSubscriptionId(subscriptionId);
                    incomingSiriParameters.setDatasetId(datasetId);
                    incomingSiriParameters.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy(useOriginalId, useAltId));
                    incomingSiriParameters.setMaxSize(-1);
                    incomingSiriParameters.setClientTrackingName(clientTrackingName);
                    incomingSiriParameters.setGmSIVSicAQuay(isGmSIVSicAQuay);

                    Long inboundTime = p.getIn().getHeader(INBOUND_TIME_HEADER_NAME, Long.class);
                    incomingSiriParameters.setInboundTime(inboundTime);

                    processorTT.mark("preparation");

                    handler.handleIncomingSiri(incomingSiriParameters);
                    processorTT.mark("ingest completed");
                    if (processorTT.getTotalTime() > 1000){
                        log.info(processorTT.toString());
                    }

                })
                .routeId("incoming.processor.default")
        ;

        from("direct:" + CamelRouteNames.FETCHED_DELIVERY_QUEUE)
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
                .routeId("incoming.processor.fetched_delivery")
        ;

        from("direct:send.sm.from.th.to.realtime.server")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .setExchangePattern(ExchangePattern.InOnly)
                .to(externalSiriSMQueue)
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
