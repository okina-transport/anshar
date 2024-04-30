package no.rutebanken.anshar.gtfsrt;

import com.google.protobuf.util.JsonFormat;
import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.GTFSRTType;
import no.rutebanken.anshar.gtfsrt.readers.AlertReader;
import no.rutebanken.anshar.gtfsrt.readers.TripUpdateReader;
import no.rutebanken.anshar.gtfsrt.readers.VehiclePositionReader;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Optional;


@Service
public class GtfsRTDataRetriever {
    private static final Logger logger = LoggerFactory.getLogger(GtfsRTDataRetriever.class);


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


    public void getGTFSRTData() {
        logger.info("Démarrage récupération des flux GTFS-RT");

        for (GtfsRTApi gtfsRTApi : subscriptionConfig.getGtfsRTApis()) {
            try {
                recoverDataForApi(gtfsRTApi);
            } catch (Exception e) {
                logger.error("Error on GTFSRT feed:" + gtfsRTApi.getDatasetId() + " - " + gtfsRTApi.getUrl());
                logger.error("Error details", e);
            }
        }

        logger.info("Intégration des flux GTFS-RT terminée");

    }

    private void recoverDataForApi(GtfsRTApi gtfsRTApi) {

        if (gtfsRTApi.getActive() != null && !gtfsRTApi.getActive()) {
            logger.info("GTRS-RT flow disabled:" + gtfsRTApi.getDatasetId() + " - " + gtfsRTApi.getUrl());
            return;
        }


        logger.info("======> Reading GTFS-RT for datasetId:" + gtfsRTApi.getDatasetId() + " and  URL:" + gtfsRTApi.getUrl());
        Optional<GtfsRealtime.FeedMessage> completeGTFSFeedOpt = buildMessageFromApi(gtfsRTApi);
        if (completeGTFSFeedOpt.isEmpty()) {
            logger.info("Empty feed for datasetId:" + gtfsRTApi.getDatasetId() + " and  URL:" + gtfsRTApi.getUrl());
            return;
        }

        GtfsRealtime.FeedMessage completeGTFSFeed = completeGTFSFeedOpt.get();
        if (completeGTFSFeed.getEntityList().size() == 0) {
            logger.info("Flux vide détecté sur le datasetId :" + gtfsRTApi.getDatasetId());
            return;
        }


        tripUpdateReader.setUrl(gtfsRTApi.getUrl());
        tripUpdateReader.ingestTripUpdateData(gtfsRTApi.getDatasetId(), completeGTFSFeed);

        if (configuration.processVM()) {
            vehiclePositionReader.setUrl(gtfsRTApi.getUrl());
            vehiclePositionReader.ingestVehiclePositionData(gtfsRTApi.getDatasetId(), completeGTFSFeed);
        }

        if (configuration.processSX()) {
            alertReader.setUrl(gtfsRTApi.getUrl());
            alertReader.ingestAlertData(gtfsRTApi.getDatasetId(), completeGTFSFeed);
        }
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
            URL url1 = new URL(gtfsRTApi.getUrl());


            if (gtfsRTApi.getType() == null || GTFSRTType.PROTOBUF.equals(gtfsRTApi.getType())) {
                BufferedInputStream in = new BufferedInputStream(url1.openStream());
                return Optional.of(GtfsRealtime.FeedMessage.newBuilder().mergeFrom(in).build());
            }


            GtfsRealtime.FeedMessage.Builder structBuilder = GtfsRealtime.FeedMessage.newBuilder();
            String json = IOUtils.toString(url1, Charset.forName("UTF-8"));
            JsonFormat.parser().ignoringUnknownFields().merge(json, structBuilder);
            return Optional.of(structBuilder.build());
        } catch (IOException ex) {
            logger.error("Error while creating feedMessage", ex);
            return Optional.empty();
        }

    }
}
