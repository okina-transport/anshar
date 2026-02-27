package no.rutebanken.anshar.gtfsrt;

import com.google.protobuf.util.JsonFormat;
import com.google.transit.realtime.GtfsRealtime;
import io.micrometer.common.util.StringUtils;
import no.rutebanken.anshar.api.FlowStatus;
import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.GTFSRTType;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.gtfsrt.readers.AlertReader;
import no.rutebanken.anshar.gtfsrt.readers.TripUpdateReader;
import no.rutebanken.anshar.gtfsrt.readers.VehiclePositionReader;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.health.IncomingDataHealthService;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.health.IncomingFlowType.GTFS;


@Service
public class GtfsRTDataRetriever {
    private static final Logger logger = LoggerFactory.getLogger(GtfsRTDataRetriever.class);
    private final String lockMap = "ansharRouteLockMap";
    private final String gtfsRtLock = "isGtfsRtRunning";
    private final String gtfsRtLastExecutionTime = "gtfsRTLastExecutionTime";
    private final String DEFAULT_ERROR_CODE = "500";
    private final Pattern pattern = Pattern.compile("HTTP response code: (\\d+)");
    @Autowired
    private TripUpdateReader tripUpdateReader;
    @Autowired
    private VehiclePositionReader vehiclePositionReader;
    @Autowired
    private AlertReader alertReader;
    @Autowired
    private SubscriptionConfig subscriptionConfig;
    @Autowired
    private AnsharConfiguration configuration;
    @Autowired
    private ExtendedHazelcastService hazelcastService;
    @Autowired
    private PrometheusMetricsService metrics;
    private long iterationNb = 0;
    @Autowired
    private IncomingDataHealthService incomingDataHealthService;


    public void getGTFSRTData() {
        if (!isGtfsRtRunning()) {
            startGtfsRtRecovering();
        } else {
            long lastExecutionTime = getLastExecutionTime();
            logger.info(" GTFS-RT en cours. Pas de nouveau lancement. Dernier lancement:" + lastExecutionTime);

            if (System.currentTimeMillis() - lastExecutionTime > 120000) {
                logger.warn("GTFS-RT : dernière exécution datant de plus de 2 minutes. Force unlock.");
                hazelcastService.getHazelcastInstance().getMap(lockMap).put(gtfsRtLock, false);
            }
        }
    }

    private boolean isGtfsRtRunning() {
        Object isGtfsRtRunning = hazelcastService.getHazelcastInstance().getMap(lockMap).get(gtfsRtLock);
        return isGtfsRtRunning != null && (boolean) isGtfsRtRunning;
    }

    private long getLastExecutionTime() {
        Object lastExecutionTime = hazelcastService.getHazelcastInstance().getMap(lockMap).get(gtfsRtLastExecutionTime);
        return lastExecutionTime != null ? (long) lastExecutionTime : 0;
    }

    private void startGtfsRtRecovering() {
        try {
            hazelcastService.getHazelcastInstance().getMap(lockMap).put(gtfsRtLock, true);
            logger.info("Démarrage récupération des flux GTFS-RT n°:" + iterationNb);

            for (GtfsRTApi gtfsRTApi : subscriptionConfig.getGtfsRTApis()) {
                try {
                    recoverDataForApi(gtfsRTApi);
                } catch (Throwable e) {
                    logger.error("Error on GTFSRT feed:" + gtfsRTApi.getDatasetId() + " - " + gtfsRTApi.getUrl());
                    logger.error("Error detail", e);
                    gtfsRTApi.setStatus(FlowStatus.ERROR);
                    metrics.registerIncomingDataMonitoring(GTFS.getCode(), gtfsRTApi.getDatasetId(), "500", gtfsRTApi.getUrl());
                    incomingDataHealthService.sendSubscriptionMonitoringData(GTFS.getCode(), gtfsRTApi.getDatasetId(), "500", gtfsRTApi.getUrl());
                }
                incomingDataHealthService.recordStatus(gtfsRTApi);
            }
            logger.info("Intégration des flux GTFS-RT terminée n°:" + iterationNb);
            iterationNb++;
        } catch (Throwable e) {
            logger.error("Error on while iterating GTFSRT feed", e);
        } finally {
            hazelcastService.getHazelcastInstance().getMap(lockMap).put(gtfsRtLock, false);
        }
    }

    private void recoverDataForApi(GtfsRTApi gtfsRTApi) {

        if (gtfsRTApi.getActive() != null && !gtfsRTApi.getActive()) {
            logger.info("GTRS-RT flow disabled:" + gtfsRTApi.getDatasetId() + " - " + gtfsRTApi.getUrl());
            gtfsRTApi.setStatus(FlowStatus.DISABLED);
            return;
        }

        gtfsRTApi.setLastUpdate(System.currentTimeMillis());
        logger.info("======> Reading GTFS-RT for datasetId:" + gtfsRTApi.getDatasetId() + " and  URL:" + gtfsRTApi.getUrl());
        Optional<GtfsRealtime.FeedMessage> completeGTFSFeedOpt = buildMessageFromApi(gtfsRTApi);
        if (completeGTFSFeedOpt.isEmpty()) {
            logger.info("Empty feed for datasetId:" + gtfsRTApi.getDatasetId() + " and  URL:" + gtfsRTApi.getUrl());
            gtfsRTApi.setStatus(FlowStatus.EMPTY_FEED);
            return;
        }

        GtfsRealtime.FeedMessage completeGTFSFeed = completeGTFSFeedOpt.get();
        if (completeGTFSFeed.getEntityList().size() == 0) {
            logger.info("Flux vide détecté sur le datasetId :" + gtfsRTApi.getDatasetId());
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
            alertReader.ingestAlertData(gtfsRTApi, routeIdList, completeGTFSFeed, System.currentTimeMillis());
        }
        gtfsRTApi.setStatus(FlowStatus.OK);
        logger.info("GTFS-RT Reading completed for datasetId:" + gtfsRTApi.getDatasetId() + " and  URL:" + gtfsRTApi.getUrl());
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
            URL newUrl = URI.create(gtfsRTApi.getUrl()).toURL();
            HttpURLConnection connection = (HttpURLConnection) newUrl.openConnection();
            connection.setRequestMethod("GET");

            if (gtfsRTApi.getApiKey() != null && StringUtils.isNotBlank(gtfsRTApi.getApiKey())) {
                connection.setRequestProperty("Authorization", gtfsRTApi.getApiKey());
                logger.debug("Request sent to {} with Authorization header.", gtfsRTApi.getUrl());
            }

            try (InputStream inputStream = connection.getInputStream()) {
                String responseCode = String.valueOf(connection.getResponseCode());
                metrics.registerIncomingDataMonitoring(GTFS.getCode(), gtfsRTApi.getDatasetId(), responseCode, gtfsRTApi.getUrl());
                incomingDataHealthService.sendSubscriptionMonitoringData(GTFS.getCode(), gtfsRTApi.getDatasetId(), responseCode, gtfsRTApi.getUrl());

                if (gtfsRTApi.getType() == null || GTFSRTType.PROTOBUF.equals(gtfsRTApi.getType())) {
                    BufferedInputStream in = new BufferedInputStream(inputStream);
                    return Optional.of(GtfsRealtime.FeedMessage.newBuilder().mergeFrom(in).build());
                } else {
                    GtfsRealtime.FeedMessage.Builder structBuilder = GtfsRealtime.FeedMessage.newBuilder();
                    String json = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                    JsonFormat.parser().ignoringUnknownFields().merge(json, structBuilder);
                    return Optional.of(structBuilder.build());
                }
            }
        } catch (IOException ex) {
            metrics.registerIncomingDataMonitoring(GTFS.getCode(), gtfsRTApi.getDatasetId(), getErrorCode(ex.getMessage()), gtfsRTApi.getUrl());
            incomingDataHealthService.sendSubscriptionMonitoringData(GTFS.getCode(), gtfsRTApi.getDatasetId(), getErrorCode(ex.getMessage()), gtfsRTApi.getUrl());
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
