package no.rutebanken.anshar.gtfsrt.mappers;


import com.google.transit.realtime.GtfsRealtime;
import com.hazelcast.map.IMap;
import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.ishtar.model.PublishToDisplayAction;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.mapping.StopTimesService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import uk.org.ifopt.siri21.StopPlaceRef;
import uk.org.siri.siri20.EnvironmentReasonEnumeration;
import uk.org.siri.siri20.EquipmentReasonEnumeration;
import uk.org.siri.siri20.MiscellaneousReasonEnumeration;
import uk.org.siri.siri20.PersonnelReasonEnumeration;
import uk.org.siri.siri21.*;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;


/***
 * Utility class to convert Alert (GTFS RT)  to situation exchange (SIRI)
 */

@Component
public class AlertMapper {


    private static final Logger logger = LoggerFactory.getLogger(AlertMapper.class);

    private final StopPlaceUpdaterService stopPlaceService;
    private final StopTimesService stopTimesService;
    private final IMap<String, Long> sxStartActivePeriodMap;
    private final SimpleDateFormat yyyyMMddFormatter;

    public AlertMapper(StopPlaceUpdaterService stopPlaceService, StopTimesService stopTimesService,
                       @Qualifier("getSxStartActivePeriodMap") IMap<String, Long> sxStartActivePeriodMap
    ) {
        this.stopPlaceService = stopPlaceService;
        this.stopTimesService = stopTimesService;
        this.sxStartActivePeriodMap = sxStartActivePeriodMap;
        this.yyyyMMddFormatter = new SimpleDateFormat("yyyyMMdd");
    }

    private static void mapSeverity(PtSituationElement ptSituationElement, GtfsRealtime.Alert alert) {
        if (!alert.hasSeverityLevel()) {
            return;
        }
        ptSituationElement.setSeverity(convertSeverity(alert.getSeverityLevel()));
    }

    private static SeverityEnumeration convertSeverity(GtfsRealtime.Alert.SeverityLevel severityLevel) {
        return switch (severityLevel) {
            case UNKNOWN_SEVERITY -> SeverityEnumeration.UNKNOWN;
            case INFO -> SeverityEnumeration.VERY_SLIGHT;
            case WARNING -> SeverityEnumeration.NORMAL;
            case SEVERE -> SeverityEnumeration.SEVERE;
        };
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
        return switch (effect) {
            case NO_SERVICE -> ServiceConditionEnumeration.NO_SERVICE;
            case REDUCED_SERVICE -> ServiceConditionEnumeration.SHORT_FORMED_SERVICE;
            case SIGNIFICANT_DELAYS -> ServiceConditionEnumeration.DELAYED;
            case DETOUR, STOP_MOVED -> ServiceConditionEnumeration.DIVERTED;
            case ADDITIONAL_SERVICE -> ServiceConditionEnumeration.ADDITIONAL_SERVICE;
            case MODIFIED_SERVICE -> ServiceConditionEnumeration.ALTERED;
            case OTHER_EFFECT -> ServiceConditionEnumeration.NORMAL_SERVICE;
            case UNKNOWN_EFFECT -> ServiceConditionEnumeration.UNKNOWN;
            default -> ServiceConditionEnumeration.UNDEFINED_SERVICE_INFORMATION;
        };

    }

    private static void addStopPlace(AffectsScopeStructure affects, String stopId) {
        if (affects.getStopPlaces() == null) {
            affects.setStopPlaces(new AffectsScopeStructure.StopPlaces());
        } else if (affects.getStopPlaces().getAffectedStopPlaces().stream().anyMatch(sp -> sp.getStopPlaceRef().getValue().equals(stopId))) {
            return;
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
        StopPointRefStructure newStopRef = new StopPointRefStructure();
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
        StopPointRefStructure newStopRef = new StopPointRefStructure();
        newStopRef.setValue(stopId);
        newStop.setStopPointRef(newStopRef);
        affectedLine.getRoutes().getAffectedRoutes().getFirst().getStopPoints().getAffectedStopPointsAndLinkProjectionToNextStopPoints().add(newStop);

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

        AffectedRouteStructure firstRoute = affectedLine.getRoutes().getAffectedRoutes().getFirst();

        for (Serializable affectedStopPointsAndLinkProjectionToNextStopPoint : firstRoute.getStopPoints().getAffectedStopPointsAndLinkProjectionToNextStopPoints()) {

            if (affectedStopPointsAndLinkProjectionToNextStopPoint instanceof AffectedStopPointStructure currentAffectedStopPoint
                    && currentAffectedStopPoint.getStopPointRef().getValue().equals(stopId)) {
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
     * @param informedEntity  The GTFS-Realtime {@link GtfsRealtime.EntitySelector} providing the route ID.
     * @return The existing or newly created {@link AffectedLineStructure}, or {@code null} if the route ID is not in the provided list.
     */
    private static AffectedLineStructure getOrCreateLine(AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork, GtfsRealtime.EntitySelector informedEntity, String routeId) {
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

    private static void mapReasons(PtSituationElement ptSituationElement, GtfsRealtime.Alert alert) {

        if (alert.getCause() == null)
            return;

        switch (alert.getCause()) {
            case WEATHER:
                ptSituationElement.setEnvironmentReason(EnvironmentReasonEnumeration.UNDEFINED_ENVIRONMENTAL_PROBLEM.value());
                break;
            case CONSTRUCTION:
                ptSituationElement.setEquipmentReason(EquipmentReasonEnumeration.CONSTRUCTION_WORK.value());
                break;
            case MAINTENANCE:
                ptSituationElement.setEquipmentReason(EquipmentReasonEnumeration.MAINTENANCE_WORK.value());
                break;
            case STRIKE:
                ptSituationElement.setPersonnelReason(PersonnelReasonEnumeration.INDUSTRIAL_ACTION.value());
                break;
            case OTHER_CAUSE:
                ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.UNDEFINED_PROBLEM.value());
                break;
            case UNKNOWN_CAUSE:
                ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.UNKNOWN.value());
                break;
            case ACCIDENT:
                ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.ACCIDENT.value());
                break;
            case DEMONSTRATION:
                ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.DEMONSTRATION.value());
                break;
            case MEDICAL_EMERGENCY:
                ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.INCIDENT.value());
                break;
            case POLICE_ACTIVITY:
                ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.POLICE_ACTIVITY.value());
                break;
            case TECHNICAL_PROBLEM:
                ptSituationElement.setEquipmentReason(EquipmentReasonEnumeration.TECHNICAL_PROBLEM.value());
                break;
            case HOLIDAY:
                ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.HOLIDAY.value());
                break;
        }
    }

    private static void mapDescription(PtSituationElement ptSituationElement, GtfsRealtime.Alert alert) {
        if (alert.hasHeaderText()) {
            ptSituationElement.getSummaries().addAll(translate(alert.getHeaderText()));
        }
        if (alert.hasDescriptionText()) {
            ptSituationElement.getDescriptions().addAll(translate(alert.getDescriptionText()));
        }
        if (alert.hasTtsHeaderText()) {
            ptSituationElement.getSummaries().addAll(translate(alert.getTtsHeaderText()));
        }
        if (alert.hasTtsDescriptionText()) {
            ptSituationElement.getDescriptions().addAll(translate(alert.getTtsDescriptionText()));
        }
    }

    private static void mapUrl(PtSituationElement ptSituationElement, GtfsRealtime.Alert alert) {
        if (!alert.hasUrl() || CollectionUtils.isEmpty(alert.getUrl().getTranslationList())) {
            return;
        }
        PtSituationElement.InfoLinks infoLinks = new PtSituationElement.InfoLinks();
        infoLinks.getInfoLinks().addAll(
                alert.getUrl().getTranslationList().stream()
                        .map(GtfsRealtime.TranslatedString.Translation::getText)
                        .distinct()
                        .map(
                                t -> {
                                    InfoLinkStructure ils = new InfoLinkStructure();
                                    ils.setUri(t);
                                    return ils;
                                }
                        ).distinct().toList()
        );
        ptSituationElement.setInfoLinks(infoLinks);
    }

    private static List<DefaultedTextStructure> translate(GtfsRealtime.TranslatedString gtfsTranslatedString) {

        if (gtfsTranslatedString.getTranslationList() == null || gtfsTranslatedString.getTranslationList().isEmpty())
            return new ArrayList<>();

        List<DefaultedTextStructure> siriTextStructures = new ArrayList<>();

        for (GtfsRealtime.TranslatedString.Translation translation : gtfsTranslatedString.getTranslationList()) {

            String translationText = translation.getText();
            if (StringUtils.isEmpty(translationText))
                continue;


            DefaultedTextStructure defaultedTextStructure = new DefaultedTextStructure();
            defaultedTextStructure.setValue(translationText);

            String lang = translation.getLanguage() == null || translation.getLanguage().isEmpty() ? "FR" : translation.getLanguage();

            defaultedTextStructure.setLang(lang);

            siriTextStructures.add(defaultedTextStructure);
        }

        return siriTextStructures;
    }

    /**
     * Maps a GTFS-RT Alert to a PtSituationElement, converting relevant alert data into a structured format.
     *
     * @param feedEntity  The GTFS-Realtime entity that contains alert
     * @param gtfsrtApi   paramters of the API
     * @param routeIdList A list of route IDs to filter the alert’s applicability.
     * @return A {@link PtSituationElement} object containing the structured data from the alert.
     */
    public Optional<PtSituationElement> mapSituationFromAlert(GtfsRealtime.FeedEntity feedEntity, GtfsRTApi gtfsrtApi, List<String> routeIdList) {
        String datasetId = gtfsrtApi.getDatasetId();


        GtfsRealtime.Alert alert = feedEntity.getAlert();
        PtSituationElement ptSituationElement = new PtSituationElement();
        mapInformedEntities(ptSituationElement, alert, datasetId, routeIdList);
        if (ptSituationElement.getAffects() == null) {
            // no affected entities for this situation, discard it
            return Optional.empty();
        }
        SituationNumber situationNumber = new SituationNumber();
        situationNumber.setValue(feedEntity.getId());
        ptSituationElement.setSituationNumber(situationNumber);
        mapDescription(ptSituationElement, alert);
        mapUrl(ptSituationElement, alert);
        mapPeriod(ptSituationElement, alert, gtfsrtApi.getActivePeriodDays());
        mapReasons(ptSituationElement, alert);
        mapEffect(ptSituationElement, alert);
        mapSeverity(ptSituationElement, alert);
        addPublishToDisplayActionIfNecessary(gtfsrtApi, ptSituationElement);

        return Optional.of(ptSituationElement);
    }

    /**
     * Maps the affected entities from a GTFS-Realtime Alert to a {@link PtSituationElement}.
     *
     * @param ptSituationElement The {@link PtSituationElement} to which the affected entities will be mapped.
     * @param alert              The GTFS-Realtime {@link GtfsRealtime.Alert} containing the affected entities.
     * @param datasetId          The identifier of the dataset associated with the alert.
     * @param routeIdList        Allowed routeIds (may be null or empty), discard Alert.informedEntities linked to
     *                           routeIds not in this list (ignored if list is null/empty)
     */
    private void mapInformedEntities(PtSituationElement ptSituationElement, GtfsRealtime.Alert alert, String datasetId, List<String> routeIdList) {
        List<GtfsRealtime.EntitySelector> informedEntities = alert.getInformedEntityList();
        if (CollectionUtils.isEmpty(informedEntities)) {
            logger.warn("No informed entity found for alert");
            return;
        }
        AffectsScopeStructure affects = new AffectsScopeStructure();
        AffectsScopeStructure.VehicleJourneys vehicleJourneys = new AffectsScopeStructure.VehicleJourneys();
        boolean hasMappedEntitiy = false;
        for (GtfsRealtime.EntitySelector informedEntity : informedEntities) {
            String routeId = null;
            boolean discardEntity = false;
            if (informedEntity.hasTrip()) {
                if (informedEntity.getTrip().hasTripId()) {
                    Optional<String> optRouteId = stopTimesService.getRouteId(datasetId, informedEntity.getTrip().getTripId());
                    if (optRouteId.isEmpty()) {
                        logger.debug("Trip id {} not found in dataset {}, discard entity",
                                informedEntity.getTrip().getTripId(),
                                datasetId);
                        discardEntity = true;
                    } else {
                        routeId = optRouteId.get();
                    }
                } else {
                    routeId = informedEntity.getTrip().getRouteId();
                }
            } else if (informedEntity.hasRouteId()) {
                routeId = informedEntity.getRouteId();
            }
            if (routeId != null && !stopTimesService.checkIfKnownRouteId(datasetId, routeId)) {
                logger.debug("Route id {} not found in dataset {}, discard entity", routeId, datasetId);
                discardEntity = true;
            } else if (routeId != null && CollectionUtils.isNotEmpty(routeIdList) && !routeIdList.contains(routeId)) {
                logger.debug("Route id {} not in accepted route ids {}, discard entity", routeId, routeIdList);
                discardEntity = true;
            }
            if (discardEntity) {
                continue;
            }
            hasMappedEntitiy = true;
            vehicleJourneys.getAffectedVehicleJourneies().addAll(getVehicleJourneys(informedEntity, datasetId));
            mapInformedEntity(affects, informedEntity, datasetId, routeId);
        }
        if (!hasMappedEntitiy) {
            return;
        }
        if (CollectionUtils.isNotEmpty(vehicleJourneys.getAffectedVehicleJourneies())) {
            affects.setVehicleJourneys(vehicleJourneys);
        }
        ptSituationElement.setAffects(affects);
    }

    /**
     * Records the affected entities from a GTFS-Realtime {@link GtfsRealtime.EntitySelector} into an {@link AffectsScopeStructure}.
     * This method determines whether the affected entity is related to an agency, route, or stop and updates the structure accordingly.
     *
     * @param affects        The {@link AffectsScopeStructure} that stores the affected elements.
     * @param informedEntity The GTFS-Realtime {@link GtfsRealtime.EntitySelector} representing the affected entity.
     * @param datasetId      The identifier of the dataset associated with the affected entity.
     */
    private void mapInformedEntity(AffectsScopeStructure affects, GtfsRealtime.EntitySelector informedEntity,
                                   String datasetId, String routeId) {


        if (informedEntity.hasAgencyId() || StringUtils.isNotEmpty(routeId)) {
            AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork = getOrCreateNetwork(affects, informedEntity);

            if (informedEntity.hasRouteId()) {
                AffectedLineStructure affectedLine = getOrCreateLine(affectedNetwork, informedEntity, routeId);
                addAffectedStopInAffectedLine(affectedLine, informedEntity);
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
    private void addAffectedStop(AffectsScopeStructure affects, GtfsRealtime.EntitySelector informedEntity, String datasetId) {
        String stopId = informedEntity.getStopId();
        if (stopPlaceService.isKnownId(datasetId + ":StopPlace:" + stopId)) {
            addStopPlace(affects, stopId);
        } else {
            addStopPoint(affects, stopId);
        }
    }

    private List<AffectedVehicleJourneyStructure> getVehicleJourneys(GtfsRealtime.EntitySelector informedEntity, String datasetId) {
        AffectsScopeStructure.VehicleJourneys vehicleJourneys = new AffectsScopeStructure.VehicleJourneys();

        if (informedEntity.hasTrip()) {
            AffectedVehicleJourneyStructure vehicleJourney = new AffectedVehicleJourneyStructure();

            GtfsRealtime.TripDescriptor tripDescriptor = informedEntity.getTrip();
            if (tripDescriptor != null)
                mapTripDescriptor(tripDescriptor, vehicleJourney, datasetId);

            vehicleJourneys.getAffectedVehicleJourneies().add(vehicleJourney);
        }
        return vehicleJourneys.getAffectedVehicleJourneies();

    }

    private void mapTripDescriptor(GtfsRealtime.TripDescriptor tripDescriptor, AffectedVehicleJourneyStructure vehicleJourney, String datasetId) {
        if (tripDescriptor.hasStartDate()) {
            try {
                Date startDate = yyyyMMddFormatter.parse(tripDescriptor.getStartDate());
                ZonedDateTime departureTime = ZonedDateTime.ofInstant(startDate.toInstant(), ZoneId.systemDefault());
                vehicleJourney.setOriginAimedDepartureTime(departureTime);
            } catch (ParseException e) {
                logger.error("Unable to parse start date : {}", tripDescriptor.getStartDate());
            }
        }
        if (tripDescriptor.hasTripId()) {
            FramedVehicleJourneyRefStructure fvjrs = new FramedVehicleJourneyRefStructure();
            fvjrs.setDatedVehicleJourneyRef(tripDescriptor.getTripId());
            vehicleJourney.setFramedVehicleJourneyRef(fvjrs);
            stopTimesService.getRouteId(datasetId, tripDescriptor.getTripId()).ifPresent(routeId -> {
                LineRef lineRef = new LineRef();
                lineRef.setValue(routeId);
                vehicleJourney.setLineRef(lineRef);
            });
        } else if (tripDescriptor.hasRouteId()) {
            LineRef lineRef = new LineRef();
            lineRef.setValue(tripDescriptor.getRouteId());
            vehicleJourney.setLineRef(lineRef);
        }

    }

    private void mapPeriod(PtSituationElement ptSituationElement, GtfsRealtime.Alert alert, Integer activePeriodDays) {
        ZoneId zoneId = ZoneId.systemDefault();

        if (alert.getActivePeriodList().isEmpty() && activePeriodDays != null) {
            String situationNumber = ptSituationElement.getSituationNumber().getValue();
            long startSeconds;

            if (sxStartActivePeriodMap.containsKey(situationNumber)) {
                startSeconds = sxStartActivePeriodMap.get(situationNumber);
            } else {
                startSeconds = Instant.now().getEpochSecond();
                sxStartActivePeriodMap.put(situationNumber, startSeconds);
            }
            ZonedDateTime startTime = Instant.ofEpochSecond(startSeconds).atZone(ZoneId.systemDefault());

            long endSeconds = startSeconds + (86400L * activePeriodDays);
            ZonedDateTime endTime = Instant.ofEpochSecond(endSeconds).atZone(ZoneId.systemDefault());


            HalfOpenTimestampOutputRangeStructure validityPeriod = new HalfOpenTimestampOutputRangeStructure();
            validityPeriod.setStartTime(startTime);
            validityPeriod.setEndTime(endTime);
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

    private void addPublishToDisplayActionIfNecessary(GtfsRTApi gtfsrtApi, PtSituationElement ptSituationElement) {
        PublishToDisplayAction configuredPublishToDisplayAction = gtfsrtApi.getPublishToDisplayAction();
        if (configuredPublishToDisplayAction != PublishToDisplayAction.NONE) {
            ActionsStructure actionsStructure = new ActionsStructure();
            List<uk.org.siri.siri21.PublishToDisplayAction> publishToDisplayActions = actionsStructure.getPublishToDisplayActions();
            uk.org.siri.siri21.PublishToDisplayAction publishToDisplayAction = buildPublishToDisplayAction(configuredPublishToDisplayAction);

            publishToDisplayActions.add(publishToDisplayAction);

            ptSituationElement.setPublishingActions(actionsStructure);
        }
    }

    private static uk.org.siri.siri21.PublishToDisplayAction buildPublishToDisplayAction(PublishToDisplayAction configuredPublishToDisplayAction) {
        uk.org.siri.siri21.PublishToDisplayAction publishToDisplayAction = new uk.org.siri.siri21.PublishToDisplayAction();

        publishToDisplayAction.setOnPlace(configuredPublishToDisplayAction == PublishToDisplayAction.ON_PLACE_AND_ON_BOARD
        || configuredPublishToDisplayAction == PublishToDisplayAction.ON_PLACE);

        publishToDisplayAction.setOnBoard(configuredPublishToDisplayAction == PublishToDisplayAction.ON_BOARD
                || configuredPublishToDisplayAction == PublishToDisplayAction.ON_PLACE_AND_ON_BOARD);
        return publishToDisplayAction;
    }

}
