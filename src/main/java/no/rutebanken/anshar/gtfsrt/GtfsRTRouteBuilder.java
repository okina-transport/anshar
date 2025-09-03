package no.rutebanken.anshar.gtfsrt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.AppMode;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.gtfsrt.model.GtfsRtInboundEt;
import no.rutebanken.anshar.gtfsrt.model.GtfsRtInboundSm;
import no.rutebanken.anshar.gtfsrt.model.GtfsRtInboundSx;
import no.rutebanken.anshar.gtfsrt.model.GtfsRtInboundVm;
import no.rutebanken.anshar.gtfsrt.readers.AlertReader;
import no.rutebanken.anshar.gtfsrt.readers.TripUpdateReader;
import no.rutebanken.anshar.gtfsrt.readers.VehiclePositionReader;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.routes.health.IncomingDataHealthService;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.anshar.gtfsrt.GtfsRtConstants.*;

@Component
public class GtfsRTRouteBuilder extends BaseRouteBuilder {

    public static final String IMPORT_GTFSRT_ROUTE_ID = "import_GTFSRT_DATA";

    private static final Logger logger = LoggerFactory.getLogger(GtfsRTRouteBuilder.class);

    @Value("${anshar.gtfs.interval.millis:60000}") //120000
    private int gtfsIntervalInMillis;

    private final AnsharConfiguration configuration;

    private final TripUpdateReader tripUpdateReader;

    private final VehiclePositionReader vehiclePositionReader;

    private final AlertReader alertReader;

    private final SubscriptionConfig subscriptionConfig;

    private final ExtendedHazelcastService hazelcastService;

    private final GtfsRtHelper gtfsRtHelper;

    private final ProducerTemplate producerTemplate;

    private final ObjectMapper objectMapper;

    private final IncomingDataHealthService incomingDataHealthService;

    private final PrometheusMetricsService metrics;

    protected GtfsRTRouteBuilder(AnsharConfiguration config, SubscriptionManager subscriptionManager, TripUpdateReader tripUpdateReader, VehiclePositionReader vehiclePositionReader, AlertReader alertReader, SubscriptionConfig subscriptionConfig, ExtendedHazelcastService hazelcastService, GtfsRtHelper gtfsRtHelper, ProducerTemplate producerTemplate, IncomingDataHealthService incomingDataHealthService, PrometheusMetricsService metrics) {
        super(config, subscriptionManager);
        this.configuration = config;
        this.tripUpdateReader = tripUpdateReader;
        this.vehiclePositionReader = vehiclePositionReader;
        this.alertReader = alertReader;
        this.subscriptionConfig = subscriptionConfig;
        this.hazelcastService = hazelcastService;
        this.gtfsRtHelper = gtfsRtHelper;
        this.producerTemplate = producerTemplate;
        this.incomingDataHealthService = incomingDataHealthService;
        this.metrics = metrics;
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void configure() throws Exception {
        if (configuration.getAppModes().isEmpty()) {
            singletonFrom("quartz://anshar/import_GTFSRT_DATA?trigger.repeatInterval=" + gtfsIntervalInMillis, IMPORT_GTFSRT_ROUTE_ID)
                    .process(new GtfsRTDataRetriever(tripUpdateReader, vehiclePositionReader, alertReader, subscriptionConfig, configuration, hazelcastService, gtfsRtHelper, incomingDataHealthService, metrics))
                    .end();
        } else if (configuration.getAppModes().contains(AppMode.PROXY)) {
            singletonFrom("quartz://anshar/import_GTFSRT_DATA?trigger.repeatInterval=" + gtfsIntervalInMillis, IMPORT_GTFSRT_ROUTE_ID)
                    .process(new GtfsRtProxyProcessor(producerTemplate, tripUpdateReader, vehiclePositionReader, alertReader, subscriptionConfig, configuration, hazelcastService, gtfsRtHelper, incomingDataHealthService, metrics))
                    .end();
        }

        configureProxyEtIngester();

        configureProxySmIngester();

        configureProxyVmIngester();

        configureProxySxIngester();
    }

    private void configureProxyEtIngester() {
        if (configuration.processET()) {
            from(GTFS_RT_ET_PROXY_QUEUE)
                .routeId(GTFS_RT_ET_PROXY_QUEUE)
                .process(exchange -> {
                    logger.debug("Processing ET proxy queue");
                    String rawMessage = exchange.getIn().getBody(String.class);
                    if (StringUtils.isNotBlank(rawMessage)) {
                        GtfsRtInboundEt etData = objectMapper.readValue(rawMessage, GtfsRtInboundEt.class);
                        tripUpdateReader.consumeEstimatedTimeTables(etData);
                    } else {
                        logger.error("Empty ET message received");
                    }
                })
                .end();
        }
    }

    private void configureProxySmIngester() {
        if (configuration.processSM()) {
            from(GTFS_RT_SM_PROXY_QUEUE)
                .routeId(GTFS_RT_SM_PROXY_QUEUE)
                .process(exchange -> {
                    logger.debug("Processing SM proxy queue");
                    String rawMessage = exchange.getIn().getBody(String.class);
                    if (StringUtils.isNotBlank(rawMessage)) {
                        GtfsRtInboundSm smData = objectMapper.readValue(rawMessage, GtfsRtInboundSm.class);
                        tripUpdateReader.consumeStopVisits(smData);
                    } else {
                        logger.error("Empty SM message received");
                    }
                })
                .end();
        }
    }

    private void configureProxyVmIngester() {
        if (configuration.processVM()) {
            from(GTFS_RT_VM_PROXY_QUEUE)
                .routeId(GTFS_RT_VM_PROXY_QUEUE)
                .process(exchange -> {
                    logger.debug("Processing VM proxy queue");
                    String rawMessage = exchange.getIn().getBody(String.class);
                    if (StringUtils.isNotBlank(rawMessage)) {
                        GtfsRtInboundVm vmData = objectMapper.readValue(rawMessage, GtfsRtInboundVm.class);
                        vehiclePositionReader.consumeVehicleMonitoring(vmData);
                    } else {
                        logger.error("Empty VM message received");
                    }
                })
                .end();
        }
    }

    private void configureProxySxIngester() {
        if (configuration.processSX()) {
            from(GTFS_RT_SX_PROXY_QUEUE)
                .routeId(GTFS_RT_SX_PROXY_QUEUE)
                .process(exchange -> {
                    logger.debug("Processing SX proxy queue");
                    String rawMessage = exchange.getIn().getBody(String.class);
                    if (StringUtils.isNotBlank(rawMessage)) {
                        GtfsRtInboundSx sxData = objectMapper.readValue(rawMessage, GtfsRtInboundSx.class);
                        alertReader.consumeAlerts(sxData);
                    } else {
                        logger.error("Empty SX message received");
                    }
                })
                .end();
        }
    }
}
