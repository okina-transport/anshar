package no.rutebanken.anshar.gtfsrt;

import com.fasterxml.jackson.core.JsonProcessingException;
import no.rutebanken.anshar.api.FlowStatus;
import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.gtfsrt.readers.AlertReader;
import no.rutebanken.anshar.gtfsrt.readers.TripUpdateReader;
import no.rutebanken.anshar.gtfsrt.readers.VehiclePositionReader;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.health.IncomingDataHealthService;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static no.rutebanken.anshar.gtfsrt.GtfsRtConstants.GTFS_RT_LOCK;
import static no.rutebanken.anshar.gtfsrt.GtfsRtConstants.LOCK_MAP;

public abstract class GtfsRtGenericRetriever implements Processor {

    public static final String GTFS_RT_TAG = "GTFS-RT";
    private static final Logger logger = LoggerFactory.getLogger(GtfsRtGenericRetriever.class);

    protected final TripUpdateReader tripUpdateReader;

    protected final VehiclePositionReader vehiclePositionReader;

    protected final AlertReader alertReader;

    protected final SubscriptionConfig subscriptionConfig;

    protected final AnsharConfiguration configuration;

    protected final ExtendedHazelcastService hazelcastService;

    protected final GtfsRtHelper gtfsRtHelper;

    protected final IncomingDataHealthService incomingDataHealthService;

    protected final PrometheusMetricsService metrics;

    private long iterationNb = 0;

    protected GtfsRtGenericRetriever(TripUpdateReader tripUpdateReader, VehiclePositionReader vehiclePositionReader, AlertReader alertReader, SubscriptionConfig subscriptionConfig, AnsharConfiguration configuration, ExtendedHazelcastService hazelcastService, GtfsRtHelper gtfsRtHelper, IncomingDataHealthService incomingDataHealthService, PrometheusMetricsService metrics) {
        this.tripUpdateReader = tripUpdateReader;
        this.vehiclePositionReader = vehiclePositionReader;
        this.alertReader = alertReader;
        this.subscriptionConfig = subscriptionConfig;
        this.configuration = configuration;
        this.hazelcastService = hazelcastService;
        this.gtfsRtHelper = gtfsRtHelper;
        this.incomingDataHealthService = incomingDataHealthService;
        this.metrics = metrics;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (!gtfsRtHelper.isGtfsRtRunning()) {
            startGtfsRtRecovering();
        } else {
            long lastExecutionTime = gtfsRtHelper.getLastExecutionTime();
            logger.info(" GTFS-RT en cours. Pas de nouveau lancement. Dernier lancement: {}", lastExecutionTime);

            if (System.currentTimeMillis() - lastExecutionTime > 120000) {
                logger.warn("GTFS-RT : dernière exécution datant de plus de 2 minutes. Force unlock.");
                hazelcastService.getHazelcastInstance().getMap(LOCK_MAP).put(GTFS_RT_LOCK, false);
            }
        }
    }

    protected void startGtfsRtRecovering() {
        hazelcastService.getHazelcastInstance().getMap(LOCK_MAP).put(GTFS_RT_LOCK, true);
        logger.info("Démarrage récupération des flux GTFS-RT n°:{}", iterationNb);

        for (GtfsRTApi gtfsRTApi : subscriptionConfig.getGtfsRTApis()) {
            try {
                recoverDataForApi(gtfsRTApi);
            } catch (Exception e) {

                logger.error("Error on GTFSRT feed: {} - {}", gtfsRTApi.getDatasetId(), gtfsRTApi.getUrl());
                logger.error("Error detail", e);
                gtfsRTApi.setStatus(FlowStatus.ERROR);
                metrics.registerIncomingDataMonitoring(GTFS_RT_TAG, gtfsRTApi.getDatasetId(), "500", gtfsRTApi.getUrl());
                incomingDataHealthService.sendSubscriptionMonitoringData(GTFS_RT_TAG, gtfsRTApi.getDatasetId(), "500", gtfsRTApi.getUrl());
            }
            incomingDataHealthService.recordStatus(gtfsRTApi);
            subscriptionConfig.updateGtfsRtStatus(gtfsRTApi);
        }
        hazelcastService.getHazelcastInstance().getMap(LOCK_MAP).put(GTFS_RT_LOCK, false);
        logger.info("Intégration des flux GTFS-RT terminée n°:{}", iterationNb);
        iterationNb++;
    }

    protected abstract void recoverDataForApi(GtfsRTApi gtfsRTApi) throws JsonProcessingException;
}
