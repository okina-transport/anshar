package no.rutebanken.anshar.gtfsrt;

import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.gtfsrt.swallowers.AlertSwallower;
import no.rutebanken.anshar.gtfsrt.swallowers.TripUpdateSwallower;
import no.rutebanken.anshar.gtfsrt.swallowers.VehiclePositionSwallower;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;


@Service
public class GtfsRTDataRetriever {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());


    @Autowired
    private TripUpdateSwallower tripUpdateSwallower;

    @Autowired
    private VehiclePositionSwallower vehiclePositionSwallower;

    @Autowired
    private AlertSwallower alertSwallower;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private AnsharConfiguration configuration;


    public void getGTFSRTData() throws IOException {
        logger.info("Démarrage récupération des flux GTFS-RT");

        for (GtfsRTApi gtfsRTApi : subscriptionConfig.getGtfsRTApis()) {
            logger.info("URL:" + gtfsRTApi.getUrl());

            URL url1 = new URL(gtfsRTApi.getUrl());
            BufferedInputStream in = new BufferedInputStream(url1.openStream());
            GtfsRealtime.FeedMessage completeGTFsFeed = GtfsRealtime.FeedMessage.newBuilder().mergeFrom(in).build();

            tripUpdateSwallower.setUrl(gtfsRTApi.getUrl());
            tripUpdateSwallower.ingestTripUpdateData(gtfsRTApi.getDatasetId(), completeGTFsFeed);

            if (configuration.processVM()){
                vehiclePositionSwallower.setUrl(gtfsRTApi.getUrl());
                vehiclePositionSwallower.ingestVehiclePositionData(gtfsRTApi.getDatasetId(), completeGTFsFeed);
            }

            if (configuration.processSX()){
                alertSwallower.setUrl(gtfsRTApi.getUrl());
                alertSwallower.ingestAlertData(gtfsRTApi.getDatasetId(), completeGTFsFeed);
            }

        }

        logger.info("Intégration des flux GTFS-RT terminée");

    }




}
