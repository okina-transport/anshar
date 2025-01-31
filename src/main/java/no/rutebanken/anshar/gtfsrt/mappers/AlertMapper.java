package no.rutebanken.anshar.gtfsrt.mappers;


import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.ifopt.siri20.StopPlaceRef;
import uk.org.siri.siri20.*;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/***
 * Utility class to convert Alert (GTFS RT)  to situation exchange (SIRI)
 */


public class AlertMapper {

    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyyMMdd");

    private static final Logger logger = LoggerFactory.getLogger(AlertMapper.class);


    private static StopPlaceUpdaterService stopPlaceService;

    /**
     * Maps a GTFS-RT Alert to a PtSituationElement, converting relevant alert data into a structured format.
     *
     * @param alert      The GTFS-Realtime Alert to be mapped.
     * @param datasetId  The identifier of the dataset associated with the alert.
     * @param routeIdList A list of route IDs to filter the alert’s applicability.
     * @return A {@link PtSituationElement} object containing the structured data from the alert.
     */
    public static PtSituationElement mapSituationFromAlert(GtfsRealtime.Alert alert, String datasetId, List<String> routeIdList) {

        PtSituationElement ptSituationElement = new PtSituationElement();
        mapDescription(ptSituationElement, alert);
        mapPeriod(ptSituationElement, alert);
        mapReasons(ptSituationElement, alert);
        mapAffects(ptSituationElement, alert, datasetId, routeIdList);
        mapEffect(ptSituationElement, alert);
        mapSeverity(ptSituationElement, alert);

        return ptSituationElement;
    }

    private static void mapSeverity(PtSituationElement ptSituationElement, GtfsRealtime.Alert alert) {
        if (!alert.hasSeverityLevel()) {
            return;
        }

        ptSituationElement.setSeverity(convertSeverity(alert.getSeverityLevel()));
    }

    private static SeverityEnumeration convertSeverity(GtfsRealtime.Alert.SeverityLevel severityLevel) {

        switch (severityLevel) {
            case UNKNOWN_SEVERITY:
                return SeverityEnumeration.UNKNOWN;
            case INFO:
                return SeverityEnumeration.VERY_SLIGHT;
            case WARNING:
                return SeverityEnumeration.NORMAL;
            case SEVERE:
                return SeverityEnumeration.SEVERE;
            default:
                return SeverityEnumeration.UNDEFINED;
        }
    }

    private static void mapEffect(PtSituationElement ptSituationElement, GtfsRealtime.Alert alert) {
        if (!alert.hasEffect()) {
            return;
        }

        PtConsequencesStructure consequences = new PtConsequencesStructure();
        PtConsequenceStructure consequence = new PtConsequenceStructure();

        consequence.getConditions().add(convertEffectToCondition(alert.getEffect()));
        consequences.getConsequences().add(consequence);
        ptSituationElement.setConsequences(consequences);
    }

    private static ServiceConditionEnumeration convertEffectToCondition(GtfsRealtime.Alert.Effect effect) {

        switch (effect) {
            case NO_SERVICE:
                return ServiceConditionEnumeration.NO_SERVICE;
            case REDUCED_SERVICE:
                return ServiceConditionEnumeration.SHORT_FORMED_SERVICE;
            case SIGNIFICANT_DELAYS:
                return ServiceConditionEnumeration.DELAYED;
            case DETOUR:
            case STOP_MOVED:
                return ServiceConditionEnumeration.DIVERTED;
            case ADDITIONAL_SERVICE:
                return ServiceConditionEnumeration.ADDITIONAL_SERVICE;
            case MODIFIED_SERVICE:
                return ServiceConditionEnumeration.ALTERED;
            case OTHER_EFFECT:
                return ServiceConditionEnumeration.NORMAL_SERVICE;
            case UNKNOWN_EFFECT:
                return ServiceConditionEnumeration.UNKNOWN;
            default:
                return ServiceConditionEnumeration.UNDEFINED_SERVICE_INFORMATION;
        }

    }

    /**
     * Maps the affected entities from a GTFS-Realtime Alert to a {@link PtSituationElement}.
     *
     * @param ptSituationElement The {@link PtSituationElement} to which the affected entities will be mapped.
     * @param alert The GTFS-Realtime {@link GtfsRealtime.Alert} containing the affected entities.
     * @param datasetId The identifier of the dataset associated with the alert.
     * @param routeIdList A list of route IDs used to filter the affected entities.
     */
    private static void mapAffects(PtSituationElement ptSituationElement, GtfsRealtime.Alert alert, String datasetId, List<String> routeIdList) {
        List<GtfsRealtime.EntitySelector> informedEntities = alert.getInformedEntityList();
        if (informedEntities == null || informedEntities.size() == 0)
            return;

        AffectsScopeStructure affectStruct = new AffectsScopeStructure();
        AffectsScopeStructure.VehicleJourneys vehicleJourneys = new AffectsScopeStructure.VehicleJourneys();

        for (GtfsRealtime.EntitySelector informedEntity : informedEntities) {
            vehicleJourneys.getAffectedVehicleJourneies().addAll(getVehicleJourneys(informedEntity, routeIdList));
            recordAffect(affectStruct, informedEntity, datasetId, routeIdList);
        }
        affectStruct.setVehicleJourneys(vehicleJourneys);
        ptSituationElement.setAffects(affectStruct);
    }


    /**
     * Records the affected entities from a GTFS-Realtime {@link GtfsRealtime.EntitySelector} into an {@link AffectsScopeStructure}.
     * This method determines whether the affected entity is related to an agency, route, or stop and updates the structure accordingly.
     *
     * @param affects The {@link AffectsScopeStructure} that stores the affected elements.
     * @param informedEntity The GTFS-Realtime {@link GtfsRealtime.EntitySelector} representing the affected entity.
     * @param datasetId The identifier of the dataset associated with the affected entity.
     * @param routeIdList A list of route IDs used to filter the affected entities.
     */
    private static void recordAffect(AffectsScopeStructure affects, GtfsRealtime.EntitySelector informedEntity, String datasetId, List<String> routeIdList) {

        if (informedEntity.hasAgencyId() || informedEntity.hasRouteId()) {
            AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork = getOrCreateNetwork(affects, informedEntity);

            if (informedEntity.hasRouteId()) {
                AffectedLineStructure affectedLine = getOrCreateLine(affectedNetwork, informedEntity, routeIdList);
                if (affectedLine != null) {
                    addAffectedStopInAffectedLine(affectedLine, informedEntity);
                }
            }
        } else if (informedEntity.hasStopId()) {
            addAffectedStop(affects, informedEntity, datasetId);
        }
    }

    /**
     * Records affected stops to the general affect structure
     * (case for stops without line and without network specified)
     *
     * @param affects        all currently processed affects
     * @param informedEntity the current entity for which affect must be recorded
     */
    private static void addAffectedStop(AffectsScopeStructure affects, GtfsRealtime.EntitySelector informedEntity, String datasetId) {

        if (stopPlaceService == null) {
            stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);
        }
        String stopId = informedEntity.getStopId();
        if (stopPlaceService.isKnownId(datasetId + ":StopPlace:" + stopId)) {
            addStopPlace(affects, stopId);
        } else {
            addStopPoint(affects, stopId);
        }
    }

    private static void addStopPlace(AffectsScopeStructure affects, String stopId) {
        if (affects.getStopPlaces() == null) {
            AffectsScopeStructure.StopPlaces newStopPlaces = new AffectsScopeStructure.StopPlaces();
            affects.setStopPlaces(newStopPlaces);
        } else {
            for (AffectedStopPlaceStructure affectedStopPlace : affects.getStopPlaces().getAffectedStopPlaces()) {
                if (affectedStopPlace.getStopPlaceRef().getValue().equals(stopId)) {
                    return;
                }
            }
        }

        AffectedStopPlaceStructure newStopPlaces = new AffectedStopPlaceStructure();
        StopPlaceRef newStopRef = new StopPlaceRef();
        newStopRef.setValue(stopId);
        newStopPlaces.setStopPlaceRef(newStopRef);
        affects.getStopPlaces().getAffectedStopPlaces().add(newStopPlaces);
    }

    private static void addStopPoint(AffectsScopeStructure affects, String stopId) {
        if (affects.getStopPoints() == null) {
            AffectsScopeStructure.StopPoints newStopPoints = new AffectsScopeStructure.StopPoints();
            affects.setStopPoints(newStopPoints);
        } else {
            for (AffectedStopPointStructure affectedStopPoint : affects.getStopPoints().getAffectedStopPoints()) {
                if (affectedStopPoint.getStopPointRef().getValue().equals(stopId)) {
                    return;
                }
            }
        }

        AffectedStopPointStructure newStopPoints = new AffectedStopPointStructure();
        StopPointRef newStopRef = new StopPointRef();
        newStopRef.setValue(stopId);
        newStopPoints.setStopPointRef(newStopRef);
        affects.getStopPoints().getAffectedStopPoints().add(newStopPoints);
    }

    /**
     * Record an affected stop into an affected line
     * (case for stop specified with line id)
     *
     * @param affectedLine   the line on which affected stop must be added
     * @param informedEntity the entity for which affected stop must be recorded
     */
    private static void addAffectedStopInAffectedLine(AffectedLineStructure affectedLine, GtfsRealtime.EntitySelector informedEntity) {

        if (!informedEntity.hasStopId()) {
            return;
        }

        String stopId = informedEntity.getStopId();


        if (isStopPointAlreadyAffected(stopId, affectedLine)) {
            // StopPoint is already registered in affectedLine.
            return;
        }

        // StopPoint is not already registered. need to create it


        if (affectedLine.getRoutes() == null) {
            AffectedLineStructure.Routes newRoutes = new AffectedLineStructure.Routes();
            AffectedRouteStructure newAffectedRoute = new AffectedRouteStructure();
            AffectedRouteStructure.StopPoints newStopPoints = new AffectedRouteStructure.StopPoints();
            newAffectedRoute.setStopPoints(newStopPoints);
            newRoutes.getAffectedRoutes().add(newAffectedRoute);
            affectedLine.setRoutes(newRoutes);
        }

        AffectedStopPointStructure newStop = new AffectedStopPointStructure();
        StopPointRef newStopRef = new StopPointRef();
        newStopRef.setValue(stopId);
        newStop.setStopPointRef(newStopRef);
        affectedLine.getRoutes().getAffectedRoutes().get(0).getStopPoints().getAffectedStopPointsAndLinkProjectionToNextStopPoints().add(newStop);

    }

    /**
     * Checks if a stopPoint is already affected in an affectedLine
     *
     * @param stopId       the stop to check
     * @param affectedLine the line on which the check must be made
     * @return true: stop already affected
     * false : stop not existing in line
     */
    private static boolean isStopPointAlreadyAffected(String stopId, AffectedLineStructure affectedLine) {

        if (affectedLine.getRoutes() == null || affectedLine.getRoutes().getAffectedRoutes().isEmpty()) {
            return false;
        }

        AffectedRouteStructure firstRoute = affectedLine.getRoutes().getAffectedRoutes().get(0);

        for (Serializable affectedStopPointsAndLinkProjectionToNextStopPoint : firstRoute.getStopPoints().getAffectedStopPointsAndLinkProjectionToNextStopPoints()) {

            if (!(affectedStopPointsAndLinkProjectionToNextStopPoint instanceof AffectedStopPointStructure)) {
                continue;
            }

            AffectedStopPointStructure currentAffectedStopPoint = (AffectedStopPointStructure) affectedStopPointsAndLinkProjectionToNextStopPoint;
            if (currentAffectedStopPoint.getStopPointRef().getValue().equals(stopId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retrieves an existing {@link AffectedLineStructure} from the given {@link AffectsScopeStructure.Networks.AffectedNetwork}
     * based on the route ID of the informed entity. If the route is not found, a new {@link AffectedLineStructure} is created and added.
     *
     * @param affectedNetwork The {@link AffectsScopeStructure.Networks.AffectedNetwork} containing affected lines.
     * @param informedEntity The GTFS-Realtime {@link GtfsRealtime.EntitySelector} providing the route ID.
     * @param routeIdList A list of route IDs used to filter valid routes.
     * @return The existing or newly created {@link AffectedLineStructure}, or {@code null} if the route ID is not in the provided list.
     */
    private static AffectedLineStructure getOrCreateLine(AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork, GtfsRealtime.EntitySelector informedEntity, List<String> routeIdList) {
        String routeId = informedEntity.getRouteId();

        if (!routeIdList.isEmpty() && !routeIdList.contains(routeId)){
            return null;
        }

        for (AffectedLineStructure affectedLine : affectedNetwork.getAffectedLines()) {

            if (affectedLine.getLineRef().getValue().equals(routeId)) {
                return affectedLine;
            }
        }

        AffectedLineStructure newLine = new AffectedLineStructure();
        LineRef lineRef = new LineRef();
        lineRef.setValue(routeId);
        newLine.setLineRef(lineRef);

        if (informedEntity.hasTrip() && informedEntity.getTrip().hasDirectionId()) {
            DirectionStructure dirStruct = new DirectionStructure();
            DirectionRefStructure dirRef = new DirectionRefStructure();
            dirRef.setValue(String.valueOf(informedEntity.getTrip().getDirectionId()));
            dirStruct.setDirectionRef(dirRef);
            newLine.getDirections().add(dirStruct);
        }

        affectedNetwork.getAffectedLines().add(newLine);
        return newLine;
    }

    /**
     * Gets or creates a network for the given affects scope and informed entity.
     *
     * @param affects        The affects scope.
     * @param informedEntity The informed entity.
     * @return The affected network.
     */
    private static AffectsScopeStructure.Networks.AffectedNetwork getOrCreateNetwork(AffectsScopeStructure affects, GtfsRealtime.EntitySelector informedEntity) {

        if (affects.getNetworks() == null) {
            AffectsScopeStructure.Networks newNetworks = new AffectsScopeStructure.Networks();
            affects.setNetworks(newNetworks);
        } else {
            for (AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork : affects.getNetworks().getAffectedNetworks()) {

                if ((informedEntity.hasAgencyId() && informedEntity.getAgencyId().equals(affectedNetwork.getNetworkRef().getValue()))
                        ||
                        (!informedEntity.hasAgencyId() && affectedNetwork.getNetworkRef() == null)) {
                    return affectedNetwork;
                }
            }
        }


        AffectsScopeStructure.Networks.AffectedNetwork newNetwork = new AffectsScopeStructure.Networks.AffectedNetwork();
        if (informedEntity.hasAgencyId()) {
            NetworkRefStructure networkRefStruct = new NetworkRefStructure();
            networkRefStruct.setValue(informedEntity.getAgencyId());
            newNetwork.setNetworkRef(networkRefStruct);
        }
        affects.getNetworks().getAffectedNetworks().add(newNetwork);
        return newNetwork;
    }


    private static List<AffectedVehicleJourneyStructure> getVehicleJourneys(GtfsRealtime.EntitySelector informedEntity, List<String> routeIdList) {
        AffectsScopeStructure.VehicleJourneys vehicleJourneys = new AffectsScopeStructure.VehicleJourneys();

        if (informedEntity.hasTrip()) {
            AffectedVehicleJourneyStructure vehicleJourney = new AffectedVehicleJourneyStructure();

            GtfsRealtime.TripDescriptor tripDescriptor = informedEntity.getTrip();
            if (tripDescriptor != null)
                mapTripDescriptor(tripDescriptor, vehicleJourney, routeIdList);

            vehicleJourneys.getAffectedVehicleJourneies().add(vehicleJourney);
        }
        return vehicleJourneys.getAffectedVehicleJourneies();

    }


    private static void mapTripDescriptor(GtfsRealtime.TripDescriptor tripDescriptor, AffectedVehicleJourneyStructure vehicleJourney, List<String> routeIdList) {
        if (StringUtils.isNotEmpty(tripDescriptor.getStartDate())) {
            try {
                Date startDate = DATE_FORMATTER.parse(tripDescriptor.getStartDate());
                ZonedDateTime departureTime = ZonedDateTime.ofInstant(startDate.toInstant(), ZoneId.systemDefault());
                vehicleJourney.setOriginAimedDepartureTime(departureTime);
            } catch (ParseException e) {
                logger.error("Unable to parse start date :" + tripDescriptor.getStartDate());
            }
        }

        if (StringUtils.isNotEmpty(tripDescriptor.getRouteId())) {
            if (routeIdList.contains(tripDescriptor.getRouteId())) {
                LineRef lineRef = new LineRef();
                lineRef.setValue(tripDescriptor.getRouteId());
                vehicleJourney.setLineRef(lineRef);
            }
        }
    }

    private static void mapReasons(PtSituationElement ptSituationElement, GtfsRealtime.Alert alert) {

        if (alert.getCause() == null)
            return;

        switch (alert.getCause()) {
            case WEATHER:
                ptSituationElement.setEnvironmentReason(EnvironmentReasonEnumeration.UNDEFINED_ENVIRONMENTAL_PROBLEM);
                break;
            case CONSTRUCTION:
                ptSituationElement.setEquipmentReason(EquipmentReasonEnumeration.CONSTRUCTION_WORK);
                break;
            case MAINTENANCE:
                ptSituationElement.setEquipmentReason(EquipmentReasonEnumeration.MAINTENANCE_WORK);
                break;
            case STRIKE:
                ptSituationElement.setPersonnelReason(PersonnelReasonEnumeration.INDUSTRIAL_ACTION);
                break;
            case OTHER_CAUSE:
                ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.UNDEFINED_PROBLEM);
                break;
            case UNKNOWN_CAUSE:
                ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.UNKNOWN);
                break;
            case ACCIDENT:
                ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.ACCIDENT);
                break;
            case DEMONSTRATION:
                ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.DEMONSTRATION);
                break;
            case MEDICAL_EMERGENCY:
                ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.INCIDENT);
                break;
            case POLICE_ACTIVITY:
                ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.POLICE_ACTIVITY);
                break;
            case TECHNICAL_PROBLEM:
                ptSituationElement.setEquipmentReason(EquipmentReasonEnumeration.TECHNICAL_PROBLEM);
                break;
            case HOLIDAY:
                ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.HOLIDAY);
                break;
        }
    }

    private static void mapPeriod(PtSituationElement ptSituationElement, GtfsRealtime.Alert alert) {
        ZoneId zoneId = ZoneId.systemDefault();

        if (alert.getActivePeriodList().isEmpty()) {
            HalfOpenTimestampOutputRangeStructure validityPeriod = new HalfOpenTimestampOutputRangeStructure();
            ZonedDateTime timestamp = ZonedDateTime.ofInstant(Instant.ofEpochMilli(Long.MIN_VALUE), zoneId);
            validityPeriod.setStartTime(timestamp);
            ptSituationElement.getValidityPeriods().add(validityPeriod);
        }

        for (GtfsRealtime.TimeRange timeRange : alert.getActivePeriodList()) {

            HalfOpenTimestampOutputRangeStructure validityPeriod = new HalfOpenTimestampOutputRangeStructure();

            if (timeRange.hasStart()) {
                ZonedDateTime timestamp = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeRange.getStart() * 1000), zoneId);
                validityPeriod.setStartTime(timestamp);
            } else {
                ZonedDateTime timestamp = ZonedDateTime.now();
                validityPeriod.setStartTime(timestamp);
            }

            if (timeRange.hasEnd()) {
                ZonedDateTime timestamp = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeRange.getEnd() * 1000), zoneId);
                validityPeriod.setEndTime(timestamp);
            }

            ptSituationElement.getValidityPeriods().add(validityPeriod);
        }
    }

    private static void mapDescription(PtSituationElement ptSituationElement, GtfsRealtime.Alert alert) {

        if (alert.getHeaderText() != null) {
            ptSituationElement.getSummaries().addAll(translate(alert.getHeaderText()));
        }

        if (alert.getDescriptionText() != null) {
            ptSituationElement.getDescriptions().addAll(translate(alert.getDescriptionText()));
        }
    }

    private static List<DefaultedTextStructure> translate(GtfsRealtime.TranslatedString gtfsTranslatedString) {

        if (gtfsTranslatedString.getTranslationList() == null || gtfsTranslatedString.getTranslationList().size() == 0)
            return new ArrayList<>();

        List<DefaultedTextStructure> siriTextStructures = new ArrayList<>();

        for (GtfsRealtime.TranslatedString.Translation translation : gtfsTranslatedString.getTranslationList()) {

            String translationText = translation.getText();
            if (StringUtils.isEmpty(translationText))
                continue;


            DefaultedTextStructure defaultedTextStructure = new DefaultedTextStructure();
            defaultedTextStructure.setValue(translationText);

            String lang = translation.getLanguage() == null || translation.getLanguage().equals("") ? "FR" : translation.getLanguage();

            defaultedTextStructure.setLang(lang);

            siriTextStructures.add(defaultedTextStructure);
        }

        return siriTextStructures;
    }

}
