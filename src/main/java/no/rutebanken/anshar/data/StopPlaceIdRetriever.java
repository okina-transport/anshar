package no.rutebanken.anshar.data;


import no.rutebanken.anshar.config.TokenService;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StopPlaceIdRetriever {

    private static final Logger logger = LoggerFactory.getLogger(StopPlaceIdRetriever.class);
    private boolean isProcessAlreadyLaunched = false;

    @Value("${stop-places.api.url}")
    private String stopPlaceApiURL;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private StopPlaceIdCache idCache;

    @Autowired
    private SubscriptionManager subscriptionManager;


    public void getStopPlaceIds() throws IOException {

        if (isProcessAlreadyLaunched) {
            logger.info("Process already launched. Exiting");
            return;
        }

        isProcessAlreadyLaunched = true;

        try {

            List<String> stopPointList = subscriptionManager.getAllSubscriptions(SiriDataType.STOP_MONITORING).stream()
                    .map(SubscriptionSetup::getStopMonitoringRefValues)
                    .flatMap(List::stream)
                    .filter(stopPointId -> !idCache.isKnownImportedId(stopPointId))
                    .collect(Collectors.toList());


            stopPointList.forEach(this::getNetexIdForPoint);

        } catch (Exception e) {
            logger.error("Error on stopPlace id recovering", e);
        } finally {
            isProcessAlreadyLaunched = false;
        }

    }


    /**
     * Launch a query to stop place repository to check if id is existng in theorical offer.
     * If a netex id is found in theorical offer, the association : imported-id/netex-id is saved into id cache
     *
     * @param stopPointId the searchedimported-id
     */
    private void getNetexIdForPoint(String stopPointId) {

        try {

            String extracted = extractId(stopPointId);
            URL url = new URL(stopPlaceApiURL + "?importedId=" + extracted);
            HttpURLConnection connection = null;
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-type", "application/json");
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + tokenService.getToken());
            InputStream inputStream = connection.getInputStream();
            String netexId = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));

            if (StringUtils.isNotEmpty(netexId)) {
                logger.info("Netex id :" + netexId + " has been found for point:" + stopPointId);
                idCache.addNewAssociationToCache(stopPointId, netexId);
            }

        } catch (Exception e) {
            //do nothing
        }
    }

    /**
     * Extract stopCode from a raw Id with ":" separators
     *
     * @param rawId the raw id with : separators (e.g: SIRI_NVP_037:StopPoint:BP:MADU01:LOC)
     * @return the stop code
     */
    private String extractId(String rawId) {
        if (rawId.contains(":")) {
            String idWithoutLoc = rawId.replace(":LOC", "");
            String[] idTab = idWithoutLoc.split(":");
            return idTab[idTab.length - 1];
        } else {
            return rawId;
        }
    }

}
