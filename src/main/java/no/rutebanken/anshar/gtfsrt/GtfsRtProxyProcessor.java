package no.rutebanken.anshar.gtfsrt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.api.FlowStatus;
import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.gtfsrt.model.GtfsRtInboundEt;
import no.rutebanken.anshar.gtfsrt.model.GtfsRtInboundSm;
import no.rutebanken.anshar.gtfsrt.model.GtfsRtInboundSx;
import no.rutebanken.anshar.gtfsrt.model.GtfsRtInboundVm;
import no.rutebanken.anshar.gtfsrt.readers.AlertReader;
import no.rutebanken.anshar.gtfsrt.readers.TripUpdateReader;
import no.rutebanken.anshar.gtfsrt.readers.VehiclePositionReader;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.health.IncomingDataHealthService;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.VehicleActivityStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.MonitoredStopVisit;
import uk.org.siri.siri21.MonitoredStopVisitCancellation;
import uk.org.siri.siri21.PtSituationElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.gtfsrt.GtfsRTDataRetriever.GTFS_RT_TAG;
import static no.rutebanken.anshar.gtfsrt.GtfsRtConstants.*;

public class GtfsRtProxyProcessor implements Processor {

    private static final Logger logger = LoggerFactory.getLogger(GtfsRtProxyProcessor.class);

    private long iterationNb = 0;

    private final ProducerTemplate producerTemplate;

    private final TripUpdateReader tripUpdateReader;

    private final VehiclePositionReader vehiclePositionReader;

    private final AlertReader alertReader;

    private final SubscriptionConfig subscriptionConfig;

    private final ExtendedHazelcastService hazelcastService;

    private final GtfsRtHelper gtfsRTHelper;

    private final ObjectMapper objectMapper;

    private final IncomingDataHealthService incomingDataHealthService;

    private final PrometheusMetricsService metrics;

    public GtfsRtProxyProcessor(ProducerTemplate producerTemplate,
                                SubscriptionConfig subscriptionConfig,
                                ExtendedHazelcastService hazelcastService,
                                TripUpdateReader tripUpdateReader,
                                VehiclePositionReader vehiclePositionReader,
                                AlertReader alertReader,
                                GtfsRtHelper gtfsRTHelper,
                                IncomingDataHealthService incomingDataHealthService,
                                PrometheusMetricsService metrics) {
        this.producerTemplate = producerTemplate;
        this.tripUpdateReader = tripUpdateReader;
        this.vehiclePositionReader = vehiclePositionReader;
        this.alertReader = alertReader;
        this.subscriptionConfig = subscriptionConfig;
        this.hazelcastService = hazelcastService;
        this.gtfsRTHelper = gtfsRTHelper;
        this.incomingDataHealthService = incomingDataHealthService;
        this.metrics = metrics;
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (!gtfsRTHelper.isGtfsRtRunning()) {
            startGtfsRtRecovering();
        } else {
            long lastExecutionTime = gtfsRTHelper.getLastExecutionTime();
            logger.info(" GTFS-RT en cours. Pas de nouveau lancement. Dernier lancement: {}", lastExecutionTime);

            if (System.currentTimeMillis() - lastExecutionTime > 120000) {
                logger.warn("GTFS-RT : dernière exécution datant de plus de 2 minutes. Force unlock.");
                hazelcastService.getHazelcastInstance().getMap(LOCK_MAP).put(GTFS_RT_LOCK, false);
            }
        }
    }

    private void startGtfsRtRecovering() {

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
            }
            incomingDataHealthService.recordStatus(gtfsRTApi);
            subscriptionConfig.updateGtfsRtStatus(gtfsRTApi);
        }
        hazelcastService.getHazelcastInstance().getMap(LOCK_MAP).put(GTFS_RT_LOCK, false);
        logger.info("Intégration des flux GTFS-RT terminée n°:{}", iterationNb);
        iterationNb++;
    }

    private void recoverDataForApi(GtfsRTApi gtfsRTApi) throws JsonProcessingException {
        if (gtfsRTApi.getActive() != null && !gtfsRTApi.getActive()) {
            logger.info("GTRS-RT flow disabled: {} - {}", gtfsRTApi.getDatasetId(), gtfsRTApi.getUrl());
            gtfsRTApi.setStatus(FlowStatus.DISABLED);
            return;
        }

        logger.info("======> Reading GTFS-RT for datasetId: {} and URL: {}", gtfsRTApi.getDatasetId(), gtfsRTApi.getUrl());
        Optional<GtfsRealtime.FeedMessage> completeGTFSFeedOpt = gtfsRTHelper.buildMessageFromApi(gtfsRTApi);
        if (completeGTFSFeedOpt.isEmpty()) {
            logger.info("Empty feed for datasetId: {} and URL: {}", gtfsRTApi.getDatasetId(), gtfsRTApi.getUrl());
            gtfsRTApi.setStatus(FlowStatus.EMPTY_FEED);
            return;
        }

        GtfsRealtime.FeedMessage completeGTFSFeed = completeGTFSFeedOpt.get();
        if (completeGTFSFeed.getEntityList().isEmpty()) {
            logger.info("Flux vide détecté sur le datasetId : {}", gtfsRTApi.getDatasetId());
            gtfsRTApi.setStatus(FlowStatus.EMPTY_FEED);
            return;
        }
        String url = gtfsRTApi.getUrl();
        String datasetId = gtfsRTApi.getDatasetId();

        List<String> routeIdList = gtfsRTApi.getRouteIdList() != null
                ? Arrays.stream(gtfsRTApi.getRouteIdList().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList())
                : new ArrayList<>();

        List<EstimatedVehicleJourney> estimatedVehicleJourneys = tripUpdateReader.buildEstimatedVehicleJourneyList(completeGTFSFeed, datasetId, routeIdList);
        if (CollectionUtils.isNotEmpty(estimatedVehicleJourneys)) {
            GtfsRtInboundEt etData = GtfsRtInboundEt.builder()
                    .url(url)
                    .dataSet(datasetId)
                    .estimatedVehicleJourneys(estimatedVehicleJourneys)
                    .build();
            producerTemplate.sendBody(GTFS_RT_ET_PROXY_QUEUE, objectMapper.writeValueAsString(etData));
        }


        List<MonitoredStopVisit> stopVisits = tripUpdateReader.buildStopVisitList(completeGTFSFeed, datasetId, routeIdList, gtfsRTApi.getPublishedLineNameMapping());
        List<MonitoredStopVisitCancellation> stopCancellations = tripUpdateReader.buildStopCancellationList(completeGTFSFeed, datasetId, routeIdList);
        if (CollectionUtils.isNotEmpty(stopVisits) || CollectionUtils.isNotEmpty(stopCancellations)) {
            GtfsRtInboundSm smData = GtfsRtInboundSm.builder()
                    .url(url)
                    .dataSet(datasetId)
                    .stopVisits(stopVisits)
                    .stopCancellations(stopCancellations).build();
            producerTemplate.sendBody(GTFS_RT_SM_PROXY_QUEUE, objectMapper.writeValueAsString(smData));
        }


        List<VehicleActivityStructure> vehicleActivities = vehiclePositionReader.buildVehicleActivityList(completeGTFSFeed, datasetId, routeIdList);
        if (CollectionUtils.isNotEmpty(vehicleActivities)) {
            GtfsRtInboundVm vmData = GtfsRtInboundVm.builder()
                    .url(url)
                    .dataSet(datasetId)
                    .vehicleActivities(vehicleActivities)
                    .build();
            producerTemplate.sendBody(GTFS_RT_VM_PROXY_QUEUE, objectMapper.writeValueAsString(vmData));
        }

        List<PtSituationElement> situations = alertReader.buildSituationList(completeGTFSFeed, gtfsRTApi, routeIdList);
        alertReader.updateParticipantRef(datasetId, situations);
        if (CollectionUtils.isNotEmpty(situations)) {
            GtfsRtInboundSx sxData = GtfsRtInboundSx.builder()
                    .url(url)
                    .dataSet(datasetId).situations(situations)
                    .build();
            producerTemplate.sendBody(GTFS_RT_SX_PROXY_QUEUE, objectMapper.writeValueAsString(sxData));
        }
        logger.info("GTFS-RT Reading completed for datasetId:{} and URL: {}", datasetId, url);

    }

}
