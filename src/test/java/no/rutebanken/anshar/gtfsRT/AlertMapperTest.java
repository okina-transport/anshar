package no.rutebanken.anshar.gtfsRT;

import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.gtfsrt.mappers.AlertMapper;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri20.AffectedLineStructure;
import uk.org.siri.siri20.AffectedStopPointStructure;
import uk.org.siri.siri20.AffectsScopeStructure;
import uk.org.siri.siri20.PtSituationElement;

import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;


public class AlertMapperTest extends SpringBootBaseTest {

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
    public void testAffectsWithOnlyNetwork() {

        String agencyId = "agencyIdTest";

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

        GtfsRealtime.EntitySelector.Builder newEnt = GtfsRealtime.EntitySelector.newBuilder();
        newEnt.setAgencyId(agencyId);

        alertBuilder.addInformedEntity(newEnt.build());
        PtSituationElement situation = AlertMapper.mapSituationFromAlert(alertBuilder.build());

        assertTrue(situation.getAffects() != null);
        assertTrue(situation.getAffects().getNetworks() != null);
        assertTrue(situation.getAffects().getNetworks().getAffectedNetworks() != null && situation.getAffects().getNetworks().getAffectedNetworks().size() > 0);

        AffectsScopeStructure.Networks.AffectedNetwork firstNetwork = situation.getAffects().getNetworks().getAffectedNetworks().get(0);
        assertTrue(firstNetwork.getNetworkRef() != null);
        assertEquals(agencyId,     firstNetwork.getNetworkRef().getValue());

    }

    @Test
    public void testAffectsWithOnlyLine() {

        String agencyId = "agencyIdTest";
        String lineId = "lineIdTest";

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

        GtfsRealtime.EntitySelector.Builder newEnt = GtfsRealtime.EntitySelector.newBuilder();
        newEnt.setRouteId(lineId);

        alertBuilder.addInformedEntity(newEnt.build());
        PtSituationElement situation = AlertMapper.mapSituationFromAlert(alertBuilder.build());

        assertTrue(situation.getAffects() != null);
        assertTrue(situation.getAffects().getNetworks() != null);
        assertTrue(situation.getAffects().getNetworks().getAffectedNetworks() != null && situation.getAffects().getNetworks().getAffectedNetworks().size() > 0);

        AffectsScopeStructure.Networks.AffectedNetwork firstNetwork = situation.getAffects().getNetworks().getAffectedNetworks().get(0);
        assertTrue(firstNetwork.getAffectedLines() != null && firstNetwork.getAffectedLines().size() > 0);
        AffectedLineStructure affectedLine = firstNetwork.getAffectedLines().get(0);
        assertTrue( affectedLine.getLineRef() != null );
        assertEquals(lineId, affectedLine.getLineRef().getValue());
    }

    @Test
    public void testAffectsWithLineAndStop() {

        String agencyId = "agencyIdTest";
        String lineId = "lineIdTest";
        String stopId = "stopIdTest";

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

        GtfsRealtime.EntitySelector.Builder newEnt = GtfsRealtime.EntitySelector.newBuilder();
        newEnt.setRouteId(lineId);
        newEnt.setStopId(stopId);

        alertBuilder.addInformedEntity(newEnt.build());
        PtSituationElement situation = AlertMapper.mapSituationFromAlert(alertBuilder.build());

        assertTrue(situation.getAffects() != null);
        assertTrue(situation.getAffects().getNetworks() != null);
        assertTrue(situation.getAffects().getNetworks().getAffectedNetworks() != null && situation.getAffects().getNetworks().getAffectedNetworks().size() > 0);

        AffectsScopeStructure.Networks.AffectedNetwork firstNetwork = situation.getAffects().getNetworks().getAffectedNetworks().get(0);
        assertTrue(firstNetwork.getAffectedLines() != null && firstNetwork.getAffectedLines().size() > 0);
        AffectedLineStructure affectedLine = firstNetwork.getAffectedLines().get(0);
        assertTrue( affectedLine.getLineRef() != null );
        assertEquals(lineId, affectedLine.getLineRef().getValue());


        assertTrue(  affectedLine.getDestinations() != null );
        assertTrue(  affectedLine.getDestinations().get(0).getStopPointRef() != null );
        assertEquals(stopId, affectedLine.getDestinations().get(0).getStopPointRef().getValue());
    }

    @Test
    public void testAffectsWithNetworkLineAndStop() {

        String agencyId = "agencyIdTest";
        String lineId = "lineIdTest";
        String stopId = "stopIdTest";

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

        GtfsRealtime.EntitySelector.Builder newEnt = GtfsRealtime.EntitySelector.newBuilder();
        newEnt.setRouteId(lineId);
        newEnt.setStopId(stopId);
        newEnt.setAgencyId(agencyId);

        alertBuilder.addInformedEntity(newEnt.build());
        PtSituationElement situation = AlertMapper.mapSituationFromAlert(alertBuilder.build());

        assertTrue(situation.getAffects() != null);
        assertTrue(situation.getAffects().getNetworks() != null);
        assertTrue(situation.getAffects().getNetworks().getAffectedNetworks() != null && situation.getAffects().getNetworks().getAffectedNetworks().size() > 0);

        AffectsScopeStructure.Networks.AffectedNetwork firstNetwork = situation.getAffects().getNetworks().getAffectedNetworks().get(0);
        assertTrue(firstNetwork.getAffectedLines() != null && firstNetwork.getAffectedLines().size() > 0);
        AffectedLineStructure affectedLine = firstNetwork.getAffectedLines().get(0);
        assertTrue( affectedLine.getLineRef() != null );
        assertEquals(lineId, affectedLine.getLineRef().getValue());


        assertTrue(  affectedLine.getDestinations() != null );
        assertTrue(  affectedLine.getDestinations().get(0).getStopPointRef() != null );
        assertEquals(stopId, affectedLine.getDestinations().get(0).getStopPointRef().getValue());

        assertTrue(situation.getAffects().getNetworks() != null);
        assertTrue(situation.getAffects().getNetworks().getAffectedNetworks() != null && situation.getAffects().getNetworks().getAffectedNetworks().size() > 0);


        assertTrue(firstNetwork.getNetworkRef() != null);
        assertEquals(agencyId,     firstNetwork.getNetworkRef().getValue());
    }

    @Test
    public void testAffectsWithOnlyStop() {

        String agencyId = "agencyIdTest";
        String lineId = "lineIdTest";
        String stopId = "stopIdTest";
        String stopId2 = "stopIdTest2";

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

        GtfsRealtime.EntitySelector.Builder newEnt = GtfsRealtime.EntitySelector.newBuilder();
        newEnt.setStopId(stopId);

        GtfsRealtime.EntitySelector.Builder newEnt2 = GtfsRealtime.EntitySelector.newBuilder();
        newEnt2.setStopId(stopId2);


        alertBuilder.addInformedEntity(newEnt.build());
        alertBuilder.addInformedEntity(newEnt2.build());
        PtSituationElement situation = AlertMapper.mapSituationFromAlert(alertBuilder.build());

       assertTrue(situation.getAffects() != null);
        assertTrue(situation.getAffects().getStopPoints() != null);
        assertTrue(situation.getAffects().getStopPoints().getAffectedStopPoints() != null);
        assertEquals(2,situation.getAffects().getStopPoints().getAffectedStopPoints().size());
        AffectedStopPointStructure firstPoint = situation.getAffects().getStopPoints().getAffectedStopPoints().get(0);
        assertTrue(   firstPoint.getStopPointRef() != null);
        assertEquals(  stopId, firstPoint.getStopPointRef().getValue());

        AffectedStopPointStructure secondPoint = situation.getAffects().getStopPoints().getAffectedStopPoints().get(1);
        assertTrue(   secondPoint.getStopPointRef() != null);
        assertEquals(  stopId2, secondPoint.getStopPointRef().getValue());
    }


}
