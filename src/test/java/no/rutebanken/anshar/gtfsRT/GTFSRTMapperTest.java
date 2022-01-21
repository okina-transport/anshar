package no.rutebanken.anshar.gtfsRT;

import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.gtfsrt.mappers.AlertMapper;
import no.rutebanken.anshar.gtfsrt.mappers.TripUpdateMapper;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.PtSituationElement;

import java.io.BufferedInputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;


public class GTFSRTMapperTest extends SpringBootBaseTest {

    private static String GTFS_RT_URL = "https://www.data.gouv.fr/fr/datasets/r/3bd20bc6-bfae-48d8-8785-6a34cf272df2";
    private static final int DEFAULT_HEARTBEAT_SECONDS = 300;



    @Test
    public void testGTFSRTAlertMapperTest()  {
        GtfsRealtime.Alert.Builder alertBuilder = GtfsRealtime.Alert.newBuilder();

        GtfsRealtime.TranslatedString.Translation.Builder translation = GtfsRealtime.TranslatedString.Translation.newBuilder();
        translation.setText("headerText");
        translation.setLanguage("FR");

        GtfsRealtime.TranslatedString.Builder headerTextBuilder = GtfsRealtime.TranslatedString.newBuilder().addTranslation(translation);
        alertBuilder.setHeaderText(headerTextBuilder);

        GtfsRealtime.TranslatedString.Translation.Builder descTextTrans = GtfsRealtime.TranslatedString.Translation.newBuilder();
        descTextTrans.setText("desc");
        descTextTrans.setLanguage("FR");

        GtfsRealtime.TranslatedString.Builder descTextBuilder = GtfsRealtime.TranslatedString.newBuilder().addTranslation(descTextTrans);
        alertBuilder.setDescriptionText(descTextBuilder);

        PtSituationElement situation = AlertMapper.mapSituationFromAlert(alertBuilder.build());


        assertEquals("headerText",situation.getSummaries().get(0).getValue());
        assertEquals("desc",situation.getDescriptions().get(0).getValue());


    }

    @Test
    public void testGTFSRTTripUpdateMapperTest(){
        // TODO Ecrire un test qui vérifie le mapping des trip Update

    }
    @Test
    public void testGTFSRTVehiclePositionMapperTest(){
        // TODO Ecrire un test qui vérifie le mapping des vehiclePosition

    }



}
