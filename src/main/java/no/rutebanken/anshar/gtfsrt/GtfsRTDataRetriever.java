package no.rutebanken.anshar.gtfsrt;

import com.google.protobuf.util.JsonFormat;
import com.google.transit.realtime.GtfsRealtime;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.apache.commons.io.IOUtils;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.gtfsrt.GtfsRtConstants.GTFS_RT_LOCK;
import static no.rutebanken.anshar.gtfsrt.GtfsRtConstants.LOCK_MAP;


@Service
public class GtfsRTDataRetriever {
    public static final String GTFS_RT_TAG = "GTFS-RT";

    private static final Logger logger = LoggerFactory.getLogger(GtfsRTDataRetriever.class);
    public static final String GTFS_RT = "GTFS-RT";
    private final Pattern pattern = Pattern.compile("HTTP response code: (\\d+)");
    private final TripUpdateReader tripUpdateReader;

    private final VehiclePositionReader vehiclePositionReader;

    private final AlertReader alertReader;

    private final SubscriptionConfig subscriptionConfig;

    private final AnsharConfiguration configuration;

    private final ExtendedHazelcastService hazelcastService;

    private final GtfsRtHelper gtfsRtHelper;

    private final IncomingDataHealthService incomingDataHealthService;

    private final String DEFAULT_ERROR_CODE = "500";

    private final PrometheusMetricsService metrics;

    private long iterationNb = 0;


    public GtfsRTDataRetriever(TripUpdateReader tripUpdateReader, VehiclePositionReader vehiclePositionReader, AlertReader alertReader, SubscriptionConfig subscriptionConfig, AnsharConfiguration configuration, ExtendedHazelcastService hazelcastService, GtfsRtHelper gtfsRtHelper, PrometheusMetricsService metrics, IncomingDataHealthService incomingDataHealthService) {
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


    public void getGTFSRTData() {
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

    private void startGtfsRtRecovering() {
        try {
            hazelcastService.getHazelcastInstance().getMap(LOCK_MAP).put(GTFS_RT_LOCK, true);
            logger.info("Démarrage récupération des flux GTFS-RT n°:{}", iterationNb);

            for (GtfsRTApi gtfsRTApi : subscriptionConfig.getGtfsRTApis()) {
                try {
                    recoverDataForApi(gtfsRTApi);
                } catch (Exception e) {
                    logger.error("Error on GTFSRT feed: {} - {}", gtfsRTApi.getDatasetId(), gtfsRTApi.getUrl());
                    logger.error("Error detail", e);
                    gtfsRTApi.setStatus(FlowStatus.ERROR);
                    metrics.registerIncomingDataMonitoring(GTFS_RT, gtfsRTApi.getDatasetId(), "500", gtfsRTApi.getUrl());
                    incomingDataHealthService.sendSubscriptionMonitoringData(GTFS_RT, gtfsRTApi.getDatasetId(), "500", gtfsRTApi.getUrl());
                }
                incomingDataHealthService.recordStatus(gtfsRTApi);
                subscriptionConfig.updateGtfsRtStatus(gtfsRTApi);
            }
            logger.info("Intégration des flux GTFS-RT terminée n°:{}",iterationNb);
            iterationNb++;
        } catch (Exception e) {
            logger.error("Error on while iterating GTFSRT feed", e);
        } finally {
            hazelcastService.getHazelcastInstance().getMap(LOCK_MAP).put(GTFS_RT_LOCK, false);
        }
    }

    private void recoverDataForApi(GtfsRTApi gtfsRTApi) {

        if (gtfsRTApi.getActive() != null && !gtfsRTApi.getActive()) {
            logger.info("GTRS-RT flow disabled:{} - {}", gtfsRTApi.getDatasetId(), gtfsRTApi.getUrl());
            gtfsRTApi.setStatus(FlowStatus.DISABLED);
            return;
        }

        gtfsRTApi.setLastUpdate(System.currentTimeMillis());
        logger.info("======> Reading GTFS-RT for datasetId:{} and URL:{}", gtfsRTApi.getDatasetId(), gtfsRTApi.getUrl());
        Optional<GtfsRealtime.FeedMessage> completeGTFSFeedOpt = gtfsRtHelper.buildMessageFromApi(gtfsRTApi);
        if (completeGTFSFeedOpt.isEmpty()) {
            logger.info("Empty feed for datasetId: {} and URL: {}", gtfsRTApi.getDatasetId(), gtfsRTApi.getUrl());
            gtfsRTApi.setStatus(FlowStatus.EMPTY_FEED);
            return;
        }

        GtfsRealtime.FeedMessage completeGTFSFeed = completeGTFSFeedOpt.get();
        if (completeGTFSFeed.getEntityList().isEmpty()) {
            logger.info("Flux vide détecté sur le datasetId :{}", gtfsRTApi.getDatasetId());
            gtfsRTApi.setStatus(FlowStatus.EMPTY_FEED);
            return;
        }

        List<String> routeIdList = gtfsRTApi.getRouteIdList() != null
                ? Arrays.stream(gtfsRTApi.getRouteIdList().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList())
                : new ArrayList<>();

        tripUpdateReader.setUrl(gtfsRTApi.getUrl());
        tripUpdateReader.ingestTripUpdateData(gtfsRTApi.getDatasetId(), routeIdList, completeGTFSFeed, gtfsRTApi.getPublishedLineNameMapping());

        if (configuration.processVM()) {
            vehiclePositionReader.setUrl(gtfsRTApi.getUrl());
            vehiclePositionReader.ingestVehiclePositionData(gtfsRTApi.getDatasetId(), routeIdList, completeGTFSFeed);
        }

        if (configuration.processSX()) {
            alertReader.setUrl(gtfsRTApi.getUrl());
            alertReader.ingestAlertData(gtfsRTApi, routeIdList, completeGTFSFeed);
        }
        gtfsRTApi.setStatus(FlowStatus.OK);
        logger.info("GTFS-RT Reading completed for datasetId:{} and URL: {}", gtfsRTApi.getDatasetId(), gtfsRTApi.getUrl());
    }

    /**
     * Creates a GTFSRT feed message object from an URL
     *
     * @param gtfsRTApi parameters of the API  : url, type (json or protobuf), dataset
     * @return a GTFSRT FeedMessage object
     * @throws IOException
     */
    private Optional<GtfsRealtime.FeedMessage> buildMessageFromApi(GtfsRTApi gtfsRTApi) {

        try {
            URL url1 = new URL(gtfsRTApi.getUrl());
            if (gtfsRTApi.getType() == null || GTFSRTType.PROTOBUF.equals(gtfsRTApi.getType())) {
                BufferedInputStream in = new BufferedInputStream(url1.openStream());
                metrics.registerIncomingDataMonitoring(GTFS_RT, gtfsRTApi.getDatasetId(), "200", gtfsRTApi.getUrl());
                incomingDataHealthService.sendSubscriptionMonitoringData(GTFS_RT, gtfsRTApi.getDatasetId(), "200", gtfsRTApi.getUrl());
                return Optional.of(GtfsRealtime.FeedMessage.newBuilder().mergeFrom(in).build());
            }
            GtfsRealtime.FeedMessage.Builder structBuilder = GtfsRealtime.FeedMessage.newBuilder();
            String json = IOUtils.toString(url1, StandardCharsets.UTF_8);
            JsonFormat.parser().ignoringUnknownFields().merge(json, structBuilder);
            metrics.registerIncomingDataMonitoring(GTFS_RT, gtfsRTApi.getDatasetId(), "200", gtfsRTApi.getUrl());
            incomingDataHealthService.sendSubscriptionMonitoringData(GTFS_RT, gtfsRTApi.getDatasetId(), "200", gtfsRTApi.getUrl());
            return Optional.of(structBuilder.build());
        } catch (IOException ex) {
            metrics.registerIncomingDataMonitoring(GTFS_RT, gtfsRTApi.getDatasetId(), getErrorCode(ex.getMessage()), gtfsRTApi.getUrl());
            incomingDataHealthService.sendSubscriptionMonitoringData(GTFS_RT, gtfsRTApi.getDatasetId(), getErrorCode(ex.getMessage()), gtfsRTApi.getUrl());
            logger.error("Error while creating feedMessage", ex);
            return Optional.empty();
        }

    }

    private String getErrorCode(String errorMessage) {
        Matcher matcher = pattern.matcher(errorMessage);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return DEFAULT_ERROR_CODE;
        }

    }
}
