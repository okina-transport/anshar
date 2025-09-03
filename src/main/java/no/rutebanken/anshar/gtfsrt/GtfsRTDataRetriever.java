package no.rutebanken.anshar.gtfsrt;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
public class GtfsRTDataRetriever extends GtfsRtGenericRetriever {

    private static final Logger logger = LoggerFactory.getLogger(GtfsRTDataRetriever.class);

    protected GtfsRTDataRetriever(TripUpdateReader tripUpdateReader,
                                  VehiclePositionReader vehiclePositionReader,
                                  AlertReader alertReader,
                                  SubscriptionConfig subscriptionConfig,
                                  AnsharConfiguration configuration,
                                  ExtendedHazelcastService hazelcastService,
                                  GtfsRtHelper gtfsRtHelper,
                                  IncomingDataHealthService incomingDataHealthService,
                                  PrometheusMetricsService metrics) {
        super(
                tripUpdateReader,
                vehiclePositionReader,
                alertReader,
                subscriptionConfig,
                configuration,
                hazelcastService,
                gtfsRtHelper,
                incomingDataHealthService,
                metrics
        );
    }

    @Override
    protected void recoverDataForApi(GtfsRTApi gtfsRTApi) {

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

}
