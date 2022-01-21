package no.rutebanken.anshar.gtfsrt;

import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.gtfsrt.swallowers.AlertSwallower;
import no.rutebanken.anshar.gtfsrt.swallowers.TripUpdateSwallower;
import no.rutebanken.anshar.gtfsrt.swallowers.VehiclePositionSwallower;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;


@Service
public class GtfsRTDataRetriever {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());



    @Value("${anshar.gtfs.rt.url.list}")
    private String gtfsRTUrls;

    @Autowired
    private TripUpdateSwallower tripUpdateSwallower;

    @Autowired
    private VehiclePositionSwallower vehiclePositionSwallower;

    @Autowired
    private AlertSwallower alertSwallower;


    public void getGTFSRTData() throws IOException {
        logger.info("Démarrage récupération des flux GTFS-RT");

        String[] urlTab = gtfsRTUrls.split(",");

        for (String url : urlTab) {
            logger.info("URL:" + url);

            URL url1 = new URL(url);
            BufferedInputStream in = new BufferedInputStream(url1.openStream());
            GtfsRealtime.FeedMessage completeGTFsFeed = GtfsRealtime.FeedMessage.newBuilder().mergeFrom(in).build();

            tripUpdateSwallower.setUrl(url);
            tripUpdateSwallower.ingestTripUpdateData(completeGTFsFeed);

            vehiclePositionSwallower.setUrl(url);
            vehiclePositionSwallower.ingestVehiclePositionData(completeGTFsFeed);

            alertSwallower.setUrl(url);
            alertSwallower.ingestAlertData(completeGTFsFeed);
        }

        logger.info("Intégration des flux GTFS-RT terminée");

    }




}
