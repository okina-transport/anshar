package no.rutebanken.anshar.gtfsRT;

import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.gtfsrt.mappers.AlertMapper;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri20.PtSituationElement;

import static junit.framework.TestCase.assertEquals;


public class GTFSRTMapperTest extends SpringBootBaseTest {

    private static String GTFS_RT_URL = "https://www.data.gouv.fr/fr/datasets/r/3bd20bc6-bfae-48d8-8785-6a34cf272df2";
    private static final int DEFAULT_HEARTBEAT_SECONDS = 300;


    @Test
    public void testGTFSRTAlertMapperTest() {
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


        assertEquals("headerText", situation.getSummaries().get(0).getValue());
        assertEquals("desc", situation.getDescriptions().get(0).getValue());


    }

    @Test
    public void testGTFSRTTripUpdateMapperTest() {
        // TODO Ecrire un test qui vérifie le mapping des trip Update

    }

    @Test
    public void testGTFSRTVehiclePositionMapperTest() {
        // TODO Ecrire un test qui vérifie le mapping des vehiclePosition

    }


}
