package no.rutebanken.anshar.gtfsRT;

import com.google.transit.realtime.GtfsRealtime;
import com.hazelcast.map.IMap;
import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.gtfsrt.mappers.AlertMapper;
import no.rutebanken.anshar.ishtar.model.PublishToDisplayAction;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.mapping.StopTimesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.siri.siri21.*;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class AlertMapperTest {

    @Mock // required for mock injection
    private StopPlaceUpdaterService stopPlaceService;

    @Mock
    private StopTimesService stopTimesService;

    @Mock // required for mock injection
    private IMap<String, Long> sxStartActivePeriodMap;

    @InjectMocks
    private AlertMapper alertMapper;


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

        alertBuilder.addInformedEntity(buildEntitySelector());

        List<String> routeIdList = Arrays.asList("12,13".split(","));


        GtfsRTApi gtfsRTApi = new GtfsRTApi();
        gtfsRTApi.setDatasetId("");

        GtfsRealtime.FeedEntity.Builder feedEntity = GtfsRealtime.FeedEntity.newBuilder();
        feedEntity.setAlert(alertBuilder.build());
        feedEntity.setId("id2");

        PtSituationElement situation = alertMapper.mapSituationFromAlert(feedEntity.build(), gtfsRTApi, routeIdList).get();

        assertThat(situation.getSummaries().getFirst().getValue()).isEqualTo("headerText");
        assertThat(situation.getDescriptions().getFirst().getValue()).isEqualTo("desc");


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

        PtSituationElement situation = alertMapper.mapSituationFromAlert(feedEntity.build(), gtfsRTApi, routeIdList).get();

        assertThat(situation.getAffects()).isNotNull();
        assertThat(situation.getAffects().getNetworks()).isNotNull();
        assertThat(situation.getAffects().getNetworks().getAffectedNetworks()).isNotEmpty();

        AffectsScopeStructure.Networks.AffectedNetwork firstNetwork = situation.getAffects().getNetworks().getAffectedNetworks().getFirst();
        assertThat(firstNetwork.getNetworkRef()).isNotNull();
        assertThat(firstNetwork.getNetworkRef().getValue()).isEqualTo(agencyId);
    }

    @Test
    void testAffectsWithOnlyLine() {

        String lineId = "lineIdTest";
        String datasetId = "datasetIdTest";

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
        gtfsRTApi.setDatasetId(datasetId);

        when(stopTimesService.checkIfKnownRouteId(datasetId, lineId)).thenReturn(true);

        PtSituationElement situation = alertMapper.mapSituationFromAlert(feedEntity.build(), gtfsRTApi, routeIdList).get();

        assertThat(situation.getAffects()).isNotNull();
        assertThat(situation.getAffects().getNetworks()).isNotNull();
        assertThat(situation.getAffects().getNetworks().getAffectedNetworks()).isNotEmpty();

        AffectsScopeStructure.Networks.AffectedNetwork firstNetwork = situation.getAffects().getNetworks().getAffectedNetworks().getFirst();
        assertThat(firstNetwork.getAffectedLines()).isNotEmpty();
        AffectedLineStructure affectedLine = firstNetwork.getAffectedLines().getFirst();
        assertThat(affectedLine.getLineRef()).isNotNull();
        assertThat(affectedLine.getLineRef().getValue()).isEqualTo(lineId);
    }

    @Test
    void testAffectsWithLineAndStop() {

        String lineId = "lineIdTest";
        String stopId = "stopIdTest";
        String datasetId = "datasetIdTest";

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
        gtfsRTApi.setDatasetId(datasetId);

        when(stopTimesService.checkIfKnownRouteId(datasetId, lineId)).thenReturn(true);

        PtSituationElement situation = alertMapper.mapSituationFromAlert(feedEntity.build(), gtfsRTApi, routeIdList).get();

        assertThat(situation.getAffects()).isNotNull();
        assertThat(situation.getAffects().getNetworks()).isNotNull();
        assertThat(situation.getAffects().getNetworks().getAffectedNetworks()).isNotEmpty();

        AffectsScopeStructure.Networks.AffectedNetwork firstNetwork = situation.getAffects().getNetworks().getAffectedNetworks().getFirst();
        assertThat(firstNetwork.getAffectedLines()).isNotEmpty();
        AffectedLineStructure affectedLine = firstNetwork.getAffectedLines().getFirst();
        assertThat(affectedLine.getLineRef()).isNotNull();
        assertThat(affectedLine.getLineRef().getValue()).isEqualTo(lineId);


        assertThat(affectedLine.getRoutes()).isNotNull();
        assertThat(affectedLine.getRoutes().getAffectedRoutes()).isNotEmpty();
        assertThat(affectedLine.getRoutes().getAffectedRoutes()).isNotEmpty();
        AffectedRouteStructure firstAffectedRoute = affectedLine.getRoutes().getAffectedRoutes().getFirst();
        assertThat(firstAffectedRoute.getStopPoints()).isNotNull();
        assertThat(firstAffectedRoute.getStopPoints().getAffectedStopPointsAndLinkProjectionToNextStopPoints()).isNotEmpty();

        AffectedStopPointStructure affStopPoint = (AffectedStopPointStructure) firstAffectedRoute.getStopPoints().getAffectedStopPointsAndLinkProjectionToNextStopPoints().getFirst();
        assertThat(affStopPoint.getStopPointRef().getValue()).isEqualTo(stopId);
    }

    @Test
    void testAffectsWithNetworkLineAndStop() {

        String agencyId = "agencyIdTest";
        String lineId = "lineIdTest";
        String stopId = "stopIdTest";
        String datasetId = "datasetIdTest";

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
        gtfsrtApi.setDatasetId(datasetId);

        when(stopTimesService.checkIfKnownRouteId(datasetId, lineId)).thenReturn(true);
        PtSituationElement situation = alertMapper.mapSituationFromAlert(feedEntity.build(), gtfsrtApi, routeIdList).get();

        assertThat(situation.getAffects()).isNotNull();
        assertThat(situation.getAffects().getNetworks()).isNotNull();
        assertThat(situation.getAffects().getNetworks().getAffectedNetworks()).isNotEmpty();

        AffectsScopeStructure.Networks.AffectedNetwork firstNetwork = situation.getAffects().getNetworks().getAffectedNetworks().getFirst();
        assertThat(firstNetwork.getAffectedLines()).isNotEmpty();
        AffectedLineStructure affectedLine = firstNetwork.getAffectedLines().getFirst();
        assertThat(affectedLine.getLineRef()).isNotNull();
        assertThat(affectedLine.getLineRef().getValue()).isEqualTo(lineId);


        assertThat(affectedLine.getRoutes()).isNotNull();
        AffectedRouteStructure.StopPoints stopPointsFromFirstAffectedRoute = affectedLine.getRoutes().getAffectedRoutes().getFirst().getStopPoints();
        assertThat(stopPointsFromFirstAffectedRoute).isNotNull();
        assertThat(stopPointsFromFirstAffectedRoute.getAffectedStopPointsAndLinkProjectionToNextStopPoints()).isNotEmpty();

        AffectedStopPointStructure affStopPoint = (AffectedStopPointStructure) stopPointsFromFirstAffectedRoute.getAffectedStopPointsAndLinkProjectionToNextStopPoints().getFirst();
        assertThat(affStopPoint.getStopPointRef().getValue()).isEqualTo(stopId);


        assertThat(situation.getAffects().getNetworks()).isNotNull();
        assertThat(situation.getAffects().getNetworks().getAffectedNetworks()).isNotEmpty();


        assertThat(firstNetwork.getNetworkRef()).isNotNull();
        assertThat(firstNetwork.getNetworkRef().getValue()).isEqualTo(agencyId);
    }

    @Test
    void testAffectsWithTripId() {
        String tripId = "tripId";
        String lineId = "lineId";
        String datasetId = "datasetId";

        GtfsRTApi gtfsrtApi = new GtfsRTApi();
        gtfsrtApi.setDatasetId(datasetId);

        GtfsRealtime.FeedEntity feedEntity = GtfsRealtime.FeedEntity.newBuilder()
                .setId("id2")
                .setAlert(
                        GtfsRealtime.Alert.newBuilder().addInformedEntity(
                                GtfsRealtime.EntitySelector.newBuilder()
                                        .setTrip(
                                                GtfsRealtime.TripDescriptor.newBuilder()
                                                        .setTripId(tripId)
                                        )
                        )
                ).build();

        when(stopTimesService.checkIfKnownRouteId(datasetId, lineId)).thenReturn(true);
        when(stopTimesService.getRouteId(datasetId, tripId)).thenReturn(Optional.of(lineId));

        PtSituationElement situation = alertMapper.mapSituationFromAlert(feedEntity, gtfsrtApi, List.of(lineId)).get();

        assertThat(situation.getAffects()).isNotNull();
        assertThat(situation.getAffects().getVehicleJourneys().getAffectedVehicleJourneies()).hasSize(1);
        assertThat(situation.getAffects().getVehicleJourneys().getAffectedVehicleJourneies().getFirst().getFramedVehicleJourneyRef().getDatedVehicleJourneyRef()).isEqualTo(tripId);
        assertThat(situation.getAffects().getVehicleJourneys().getAffectedVehicleJourneies().getFirst().getLineRef().getValue()).isEqualTo(lineId);
    }

    @Test
    void testEmptyValidityPeriod() {

        String agencyId = "agencyIdTest";
        String lineId = "lineIdTest";
        String stopId = "stopIdTest";
        String datasetId = "datasetIdTest";

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
        gtfsrtApi.setDatasetId(datasetId);
        gtfsrtApi.setGenerateActivePeriod(true);
        gtfsrtApi.setActivePeriodDays(3);
        ZonedDateTime now = ZonedDateTime.now();

        when(stopTimesService.checkIfKnownRouteId(datasetId, lineId)).thenReturn(true);

        PtSituationElement situation = alertMapper.mapSituationFromAlert(feedEntity.build(), gtfsrtApi, routeIdList).get();

        assertThat(situation.getValidityPeriods()).isNotNull();
        assertThat(situation.getValidityPeriods()).hasSize(1);
        ZonedDateTime firstSituationStart = situation.getValidityPeriods().getFirst().getStartTime();
        ZonedDateTime firstSituationEnd = situation.getValidityPeriods().getFirst().getEndTime();
        assertThat(firstSituationStart).isCloseTo(now, within(5, SECONDS));
        assertThat(firstSituationEnd.toInstant())
                .isCloseTo(firstSituationStart.toInstant().plus(3, ChronoUnit.DAYS), within(5, SECONDS));


        // Ingesting again the same alert with empty validity period. ValidityStart and end must be recovered from hazelcast cache
        PtSituationElement situation2 = alertMapper.mapSituationFromAlert(feedEntity.build(), gtfsrtApi, routeIdList).get();
        assertThat(situation2.getValidityPeriods()).isNotNull();
        assertThat(situation2.getValidityPeriods()).hasSize(1);
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
        PtSituationElement situation = alertMapper.mapSituationFromAlert(feedEntity.build(), gtfsrtApi, routeIdList).get();

        assertThat(situation.getAffects()).isNotNull();
        assertThat(situation.getAffects().getStopPoints()).isNotNull();
        assertThat(situation.getAffects().getStopPoints().getAffectedStopPoints()).isNotEmpty().hasSize(2);
        AffectedStopPointStructure firstPoint = situation.getAffects().getStopPoints().getAffectedStopPoints().getFirst();
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
        PtSituationElement situation = alertMapper.mapSituationFromAlert(feedEntity.build(), gtfsrtApi, routeIdList).get();
        assertThat(situation.getSeverity()).isEqualTo(outputSeverity);
    }

    private GtfsRealtime.Alert buildAlertWithSeverity(GtfsRealtime.Alert.SeverityLevel severityLevel) {
        GtfsRealtime.Alert.Builder alertBuilder = GtfsRealtime.Alert.newBuilder();
        alertBuilder.addInformedEntity(buildEntitySelector());
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

        PtSituationElement situation = alertMapper.mapSituationFromAlert(feedEntity.build(), gtfsrtApi, routeIdList).get();
        assertThat(situation.getConsequences()).isNotNull();
        assertThat(situation.getConsequences().getConsequences()).isNotEmpty();
        assertThat(situation.getConsequences().getConsequences().getFirst()).isNotNull();
        assertThat(situation.getConsequences().getConsequences().getFirst().getConditions()).isNotNull();
        assertThat(situation.getConsequences().getConsequences().getFirst().getConditions().getFirst()).isEqualTo(outputServiceCondition);
    }

    private GtfsRealtime.Alert buildAlertWithEffect(GtfsRealtime.Alert.Effect effect) {
        GtfsRealtime.Alert.Builder alertBuilder = GtfsRealtime.Alert.newBuilder();
        alertBuilder.setEffect(effect);
        alertBuilder.addInformedEntity(buildEntitySelector());
        return alertBuilder.build();
    }


    @Test
    void testUrlConversions() {
        // Arrange
        GtfsRealtime.Alert alert = GtfsRealtime.Alert.newBuilder()
                .addInformedEntity(buildEntitySelector())
                .setUrl(GtfsRealtime.TranslatedString.newBuilder()
                        .addTranslation(GtfsRealtime.TranslatedString.Translation.newBuilder().setText("https://www.google.fr"))
                        .addTranslation(GtfsRealtime.TranslatedString.Translation.newBuilder().setText("https://www.google.fr"))
                        .addTranslation(GtfsRealtime.TranslatedString.Translation.newBuilder().setText("https://www.microsoft.fr"))
                        .addTranslation(GtfsRealtime.TranslatedString.Translation.newBuilder().setText("https://www.microsoft.fr")))
                .build();
        GtfsRealtime.FeedEntity feedEntity = GtfsRealtime.FeedEntity.newBuilder()
                .setAlert(alert)
                .setId("id2")
                .build();

        // Act
        PtSituationElement situation = alertMapper.mapSituationFromAlert(feedEntity, new GtfsRTApi(), null).get();

        // Assert
        assertThat(situation.getInfoLinks().getInfoLinks()).hasSize(2); // should discard duplicate URLs
        assertThat(situation.getInfoLinks().getInfoLinks().getFirst().getUri()).isEqualTo("https://www.google.fr");
        assertThat(situation.getInfoLinks().getInfoLinks().get(1).getUri()).isEqualTo("https://www.microsoft.fr");
    }

    @ParameterizedTest
    @CsvSource({"NONE,false,false","ON_PLACE,true,false", "ON_BOARD,false,true", "ON_PLACE_AND_ON_BOARD,true,true"})
    void test_add_publishingToDisplayAction(PublishToDisplayAction publishToDisplayAction, Boolean onPlaceAssertion, Boolean onBoardAssertion) {
        String tripId = "tripId";
        String lineId = "lineId";
        String datasetId = "datasetId";

        GtfsRTApi gtfsrtApi = new GtfsRTApi();
        gtfsrtApi.setDatasetId(datasetId);
        gtfsrtApi.setPublishToDisplayAction(publishToDisplayAction);

        GtfsRealtime.FeedEntity feedEntity = GtfsRealtime.FeedEntity.newBuilder()
                .setId("id2")
                .setAlert(
                        GtfsRealtime.Alert.newBuilder().addInformedEntity(
                                GtfsRealtime.EntitySelector.newBuilder()
                                        .setTrip(
                                                GtfsRealtime.TripDescriptor.newBuilder()
                                                        .setTripId(tripId)
                                        )
                        )
                ).build();

        when(stopTimesService.checkIfKnownRouteId(datasetId, lineId)).thenReturn(true);
        when(stopTimesService.getRouteId(datasetId, tripId)).thenReturn(Optional.of(lineId));

        PtSituationElement result = alertMapper.mapSituationFromAlert(feedEntity, gtfsrtApi, Collections.emptyList()).orElse(null);

        if (publishToDisplayAction != PublishToDisplayAction.NONE) {
            assertThat(result).isNotNull();
            assertThat(result.getPublishingActions()).isNotNull();
            assertThat(result.getPublishingActions().getPublishToDisplayActions()).isNotEmpty().hasSize(1);
            assertThat(result.getPublishingActions().getPublishToDisplayActions()).extracting("onPlace").containsExactly(onPlaceAssertion);
            assertThat(result.getPublishingActions().getPublishToDisplayActions()).extracting("onBoard").containsExactly(onBoardAssertion);
        } else {
            assertThat(result).isNotNull();
            assertThat(result.getPublishingActions()).isNull();
        }
    }


    private GtfsRealtime.EntitySelector buildEntitySelector() {
        return GtfsRealtime.EntitySelector.newBuilder().setStopId("AZERTY").build();
    }

}
