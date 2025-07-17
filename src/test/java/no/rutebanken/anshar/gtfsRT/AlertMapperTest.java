package no.rutebanken.anshar.gtfsRT;

import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.gtfsrt.mappers.AlertMapper;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.*;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;


class AlertMapperTest extends SpringBootBaseTest {

    @Autowired
    private AlertMapper alertMapper;

//    @BeforeEach
//    public void setup() {
//        alertMapper = new AlertMapper(null);
//    }

    @Test
    void testGTFSRTAlertMapperTest() {
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

        List<String> routeIdList = Arrays.asList("12,13".split(","));


        GtfsRTApi gtfsRTApi = new GtfsRTApi();
        gtfsRTApi.setDatasetId("");

        GtfsRealtime.FeedEntity.Builder feedEntity = GtfsRealtime.FeedEntity.newBuilder();
        feedEntity.setAlert(alertBuilder.build());
        feedEntity.setId("id2");

        PtSituationElement situation = alertMapper.mapSituationFromAlert(feedEntity.build(), gtfsRTApi, routeIdList);

        assertThat(situation.getSummaries().get(0).getValue()).isEqualTo("headerText");
        assertThat(situation.getDescriptions().get(0).getValue()).isEqualTo("desc");


    }

    @Test
    void testAffectsWithOnlyNetwork() {

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
        List<String> routeIdList = Arrays.asList("12,13".split(","));
        GtfsRTApi gtfsRTApi = new GtfsRTApi();
        gtfsRTApi.setDatasetId("");

        GtfsRealtime.FeedEntity.Builder feedEntity = GtfsRealtime.FeedEntity.newBuilder();
        feedEntity.setAlert(alertBuilder.build());
        feedEntity.setId("id1");

        PtSituationElement situation = alertMapper.mapSituationFromAlert(feedEntity.build(), gtfsRTApi, routeIdList);

        assertThat(situation.getAffects()).isNotNull();
        assertThat(situation.getAffects().getNetworks()).isNotNull();
        assertThat(situation.getAffects().getNetworks().getAffectedNetworks()).isNotEmpty();

        AffectsScopeStructure.Networks.AffectedNetwork firstNetwork = situation.getAffects().getNetworks().getAffectedNetworks().get(0);
        assertThat(firstNetwork.getNetworkRef()).isNotNull();
        assertThat(firstNetwork.getNetworkRef().getValue()).isEqualTo(agencyId);
    }

    @Test
    void testAffectsWithOnlyLine() {

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

        List<String> routeIdList = List.of(lineId);

        GtfsRealtime.FeedEntity.Builder feedEntity = GtfsRealtime.FeedEntity.newBuilder();
        feedEntity.setAlert(alertBuilder.build());
        feedEntity.setId("id2");

        GtfsRTApi gtfsRTApi = new GtfsRTApi();
        gtfsRTApi.setDatasetId("");

        PtSituationElement situation = alertMapper.mapSituationFromAlert(feedEntity.build(), gtfsRTApi, routeIdList);

        assertThat(situation.getAffects()).isNotNull();
        assertThat(situation.getAffects().getNetworks()).isNotNull();
        assertThat(situation.getAffects().getNetworks().getAffectedNetworks()).isNotEmpty();

        AffectsScopeStructure.Networks.AffectedNetwork firstNetwork = situation.getAffects().getNetworks().getAffectedNetworks().get(0);
        assertThat(firstNetwork.getAffectedLines()).isNotEmpty();
        AffectedLineStructure affectedLine = firstNetwork.getAffectedLines().get(0);
        assertThat(affectedLine.getLineRef()).isNotNull();
        assertThat(affectedLine.getLineRef().getValue()).isEqualTo(lineId);
    }

    @Test
    void testAffectsWithLineAndStop() {

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

        List<String> routeIdList = List.of(lineId);

        GtfsRealtime.FeedEntity.Builder feedEntity = GtfsRealtime.FeedEntity.newBuilder();
        feedEntity.setAlert(alertBuilder.build());
        feedEntity.setId("id2");

        GtfsRTApi gtfsRTApi = new GtfsRTApi();
        gtfsRTApi.setDatasetId("");

        PtSituationElement situation = alertMapper.mapSituationFromAlert(feedEntity.build(), gtfsRTApi, routeIdList);

        assertThat(situation.getAffects()).isNotNull();
        assertThat(situation.getAffects().getNetworks()).isNotNull();
        assertThat(situation.getAffects().getNetworks().getAffectedNetworks()).isNotEmpty();

        AffectsScopeStructure.Networks.AffectedNetwork firstNetwork = situation.getAffects().getNetworks().getAffectedNetworks().get(0);
        assertThat(firstNetwork.getAffectedLines()).isNotEmpty();
        AffectedLineStructure affectedLine = firstNetwork.getAffectedLines().get(0);
        assertThat(affectedLine.getLineRef()).isNotNull();
        assertThat(affectedLine.getLineRef().getValue()).isEqualTo(lineId);


        assertThat(affectedLine.getRoutes()).isNotNull();
        assertThat(affectedLine.getRoutes().getAffectedRoutes()).isNotEmpty();
        assertThat(affectedLine.getRoutes().getAffectedRoutes()).isNotEmpty();
        AffectedRouteStructure firstAffectedRoute = affectedLine.getRoutes().getAffectedRoutes().get(0);
        assertThat(firstAffectedRoute.getStopPoints()).isNotNull();
        assertThat(firstAffectedRoute.getStopPoints().getAffectedStopPointsAndLinkProjectionToNextStopPoints()).isNotEmpty();

        AffectedStopPointStructure affStopPoint = (AffectedStopPointStructure) firstAffectedRoute.getStopPoints().getAffectedStopPointsAndLinkProjectionToNextStopPoints().get(0);
        assertThat(affStopPoint.getStopPointRef().getValue()).isEqualTo(stopId);
    }

    @Test
    void testAffectsWithNetworkLineAndStop() {

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

        List<String> routeIdList = List.of(lineId);

        GtfsRealtime.FeedEntity.Builder feedEntity = GtfsRealtime.FeedEntity.newBuilder();
        feedEntity.setAlert(alertBuilder.build());
        feedEntity.setId("id2");

        GtfsRTApi gtfsrtApi = new GtfsRTApi();
        gtfsrtApi.setDatasetId("");
        PtSituationElement situation = alertMapper.mapSituationFromAlert(feedEntity.build(), gtfsrtApi, routeIdList);

        assertThat(situation.getAffects()).isNotNull();
        assertThat(situation.getAffects().getNetworks()).isNotNull();
        assertThat(situation.getAffects().getNetworks().getAffectedNetworks()).isNotEmpty();

        AffectsScopeStructure.Networks.AffectedNetwork firstNetwork = situation.getAffects().getNetworks().getAffectedNetworks().get(0);
        assertThat(firstNetwork.getAffectedLines()).isNotEmpty();
        AffectedLineStructure affectedLine = firstNetwork.getAffectedLines().get(0);
        assertThat(affectedLine.getLineRef()).isNotNull();
        assertThat(affectedLine.getLineRef().getValue()).isEqualTo(lineId);


        assertThat(affectedLine.getRoutes()).isNotNull();
        AffectedRouteStructure.StopPoints stopPointsFromFirstAffectedRoute = affectedLine.getRoutes().getAffectedRoutes().get(0).getStopPoints();
        assertThat(stopPointsFromFirstAffectedRoute).isNotNull();
        assertThat(stopPointsFromFirstAffectedRoute.getAffectedStopPointsAndLinkProjectionToNextStopPoints()).isNotEmpty();

        AffectedStopPointStructure affStopPoint = (AffectedStopPointStructure) stopPointsFromFirstAffectedRoute.getAffectedStopPointsAndLinkProjectionToNextStopPoints().get(0);
        assertThat(affStopPoint.getStopPointRef().getValue()).isEqualTo(stopId);


        assertThat(situation.getAffects().getNetworks()).isNotNull();
        assertThat(situation.getAffects().getNetworks().getAffectedNetworks()).isNotEmpty();


        assertThat(firstNetwork.getNetworkRef()).isNotNull();
        assertThat(firstNetwork.getNetworkRef().getValue()).isEqualTo(agencyId);
    }

    @Test
    void testEmptyValidityPeriod() {

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

        List<String> routeIdList = List.of(lineId);

        GtfsRealtime.FeedEntity.Builder feedEntity = GtfsRealtime.FeedEntity.newBuilder();
        feedEntity.setAlert(alertBuilder.build());
        feedEntity.setId("id2");

        GtfsRTApi gtfsrtApi = new GtfsRTApi();
        gtfsrtApi.setDatasetId("");
        gtfsrtApi.setGenerateActivePeriod(true);
        gtfsrtApi.setActivePeriodDays(3);
        ZonedDateTime now = ZonedDateTime.now();
        PtSituationElement situation = alertMapper.mapSituationFromAlert(feedEntity.build(), gtfsrtApi, routeIdList);

        assertThat(situation.getValidityPeriods()).isNotNull();
        assertThat(situation.getValidityPeriods().size()).isEqualTo(1);
        ZonedDateTime firstSituationStart = situation.getValidityPeriods().getFirst().getStartTime();
        ZonedDateTime firstSituationEnd = situation.getValidityPeriods().getFirst().getEndTime();
        assertThat(firstSituationStart).isCloseTo(now, within(5, SECONDS));
        assertThat(firstSituationEnd).isCloseTo(now.plusDays(3), within(5, SECONDS));


        // Ingesting again the same alert with empty validity period. ValidityStart and end must be recovered from hazelcast cache
        PtSituationElement situation2 = alertMapper.mapSituationFromAlert(feedEntity.build(), gtfsrtApi, routeIdList);
        assertThat(situation2.getValidityPeriods()).isNotNull();
        assertThat(situation2.getValidityPeriods().size()).isEqualTo(1);
        ZonedDateTime secondSituationStart = situation2.getValidityPeriods().getFirst().getStartTime();
        ZonedDateTime secondSituationEnd = situation2.getValidityPeriods().getFirst().getEndTime();
        assertThat(secondSituationStart).isEqualTo(firstSituationStart);
        assertThat(secondSituationEnd).isEqualTo(firstSituationEnd);

    }

    @Test
    void testAffectsWithOnlyStop() {

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
        List<String> routeIdList = Arrays.asList("12,13".split(","));


        GtfsRealtime.FeedEntity.Builder feedEntity = GtfsRealtime.FeedEntity.newBuilder();
        feedEntity.setAlert(alertBuilder.build());
        feedEntity.setId("id2");

        GtfsRTApi gtfsrtApi = new GtfsRTApi();
        gtfsrtApi.setDatasetId("");
        PtSituationElement situation = alertMapper.mapSituationFromAlert(feedEntity.build(), gtfsrtApi, routeIdList);

        assertThat(situation.getAffects()).isNotNull();
        assertThat(situation.getAffects().getStopPoints()).isNotNull();
        assertThat(situation.getAffects().getStopPoints().getAffectedStopPoints()).isNotEmpty().hasSize(2);
        AffectedStopPointStructure firstPoint = situation.getAffects().getStopPoints().getAffectedStopPoints().get(0);
        assertThat(firstPoint).isNotNull();
        assertThat(firstPoint.getStopPointRef().getValue()).isEqualTo(stopId);

        AffectedStopPointStructure secondPoint = situation.getAffects().getStopPoints().getAffectedStopPoints().get(1);
        assertThat(secondPoint).isNotNull();
        assertThat(secondPoint.getStopPointRef().getValue()).isEqualTo(stopId2);
    }

    @Test
    void testSeverityConversions() {
        testSeverityConversion(GtfsRealtime.Alert.SeverityLevel.UNKNOWN_SEVERITY, SeverityEnumeration.UNKNOWN);
        testSeverityConversion(GtfsRealtime.Alert.SeverityLevel.INFO, SeverityEnumeration.VERY_SLIGHT);
        testSeverityConversion(GtfsRealtime.Alert.SeverityLevel.WARNING, SeverityEnumeration.NORMAL);
        testSeverityConversion(GtfsRealtime.Alert.SeverityLevel.SEVERE, SeverityEnumeration.SEVERE);
    }

    private void testSeverityConversion(GtfsRealtime.Alert.SeverityLevel inputSeverityLevel, SeverityEnumeration outputSeverity) {
        GtfsRealtime.Alert alert = buildAlertWithSeverity(inputSeverityLevel);
        List<String> routeIdList = Arrays.asList("12,13".split(","));

        GtfsRealtime.FeedEntity.Builder feedEntity = GtfsRealtime.FeedEntity.newBuilder();
        feedEntity.setAlert(alert);
        feedEntity.setId("id2");

        GtfsRTApi gtfsrtApi = new GtfsRTApi();
        gtfsrtApi.setDatasetId("");
        PtSituationElement situation = alertMapper.mapSituationFromAlert(feedEntity.build(), gtfsrtApi, routeIdList);
        assertThat(situation.getSeverity()).isEqualTo(outputSeverity);
    }

    private GtfsRealtime.Alert buildAlertWithSeverity(GtfsRealtime.Alert.SeverityLevel severityLevel) {
        GtfsRealtime.Alert.Builder alertBuilder = GtfsRealtime.Alert.newBuilder();
        alertBuilder.setSeverityLevel(severityLevel);
        return alertBuilder.build();
    }

    @Test
    void testEffectConversions() {
        testEffectConversion(GtfsRealtime.Alert.Effect.NO_SERVICE, ServiceConditionEnumeration.NO_SERVICE);
        testEffectConversion(GtfsRealtime.Alert.Effect.REDUCED_SERVICE, ServiceConditionEnumeration.SHORT_FORMED_SERVICE);
        testEffectConversion(GtfsRealtime.Alert.Effect.SIGNIFICANT_DELAYS, ServiceConditionEnumeration.DELAYED);
        testEffectConversion(GtfsRealtime.Alert.Effect.DETOUR, ServiceConditionEnumeration.DIVERTED);
        testEffectConversion(GtfsRealtime.Alert.Effect.STOP_MOVED, ServiceConditionEnumeration.DIVERTED);
        testEffectConversion(GtfsRealtime.Alert.Effect.ADDITIONAL_SERVICE, ServiceConditionEnumeration.ADDITIONAL_SERVICE);
        testEffectConversion(GtfsRealtime.Alert.Effect.MODIFIED_SERVICE, ServiceConditionEnumeration.ALTERED);
        testEffectConversion(GtfsRealtime.Alert.Effect.OTHER_EFFECT, ServiceConditionEnumeration.NORMAL_SERVICE);
        testEffectConversion(GtfsRealtime.Alert.Effect.UNKNOWN_EFFECT, ServiceConditionEnumeration.UNKNOWN);

    }

    private void testEffectConversion(GtfsRealtime.Alert.Effect inputEffect, ServiceConditionEnumeration outputServiceCondition) {
        GtfsRealtime.Alert alert = buildAlertWithEffect(inputEffect);
        List<String> routeIdList = Arrays.asList("12,13".split(","));


        GtfsRealtime.FeedEntity.Builder feedEntity = GtfsRealtime.FeedEntity.newBuilder();
        feedEntity.setAlert(alert);
        feedEntity.setId("id2");

        GtfsRTApi gtfsrtApi = new GtfsRTApi();
        gtfsrtApi.setDatasetId("");

        PtSituationElement situation = alertMapper.mapSituationFromAlert(feedEntity.build(), gtfsrtApi, routeIdList);
        assertThat(situation.getConsequences()).isNotNull();
        assertThat(situation.getConsequences().getConsequences()).isNotEmpty();
        assertThat(situation.getConsequences().getConsequences().get(0)).isNotNull();
        assertThat(situation.getConsequences().getConsequences().get(0).getConditions()).isNotNull();
        assertThat(situation.getConsequences().getConsequences().get(0).getConditions().get(0)).isEqualTo(outputServiceCondition);
    }

    private GtfsRealtime.Alert buildAlertWithEffect(GtfsRealtime.Alert.Effect effect) {
        GtfsRealtime.Alert.Builder alertBuilder = GtfsRealtime.Alert.newBuilder();
        alertBuilder.setEffect(effect);
        return alertBuilder.build();
    }


}
