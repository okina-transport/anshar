package no.rutebanken.anshar.gtfsrt.mappers;


import com.google.transit.realtime.GtfsRealtime;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;


/***
 * Utility class to convert Alert (GTFS RT)  to situation exchange (SIRI)
 */

public class AlertMapper {

    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyyMMdd");

    private static final Logger logger = LoggerFactory.getLogger(AlertMapper.class);


    /**
     * Main function that converts alert (GTFS-RT) to situation exchange(SIRI)
     *
     * @param alert An alert coming from GTFS-RT
     * @return A situation exchange time table (SIRI format)
     */
    public static PtSituationElement mapSituationFromAlert(GtfsRealtime.Alert alert) {

        PtSituationElement ptSituationElement = new PtSituationElement();
        mapDescription(ptSituationElement, alert);
        mapPeriod(ptSituationElement, alert);
        mapReasons(ptSituationElement, alert);
        mapAffects(ptSituationElement, alert);

        return ptSituationElement;
    }

    private static void mapAffects(PtSituationElement ptSituationElement, GtfsRealtime.Alert alert) {

        List<GtfsRealtime.EntitySelector> informedEntities = alert.getInformedEntityList();
        if (informedEntities == null || informedEntities.size() == 0)
            return;


        AffectsScopeStructure affectStruct = new AffectsScopeStructure();
        AffectsScopeStructure.VehicleJourneys vehicleJourneys = new AffectsScopeStructure.VehicleJourneys();


        for (GtfsRealtime.EntitySelector informedEntity : informedEntities) {

            vehicleJourneys.getAffectedVehicleJourneies().addAll(getVehicleJourneys(informedEntity));
            recordAffect(affectStruct, informedEntity);
        }
        affectStruct.setVehicleJourneys(vehicleJourneys);
        ptSituationElement.setAffects(affectStruct);
    }


    /**
     * Add a new affect
     *
     * @param affects        all already processed affects
     * @param informedEntity new entity containing affects that must be recorded
     */
    private static void recordAffect(AffectsScopeStructure affects, GtfsRealtime.EntitySelector informedEntity) {


        if (informedEntity.hasAgencyId() || informedEntity.hasRouteId()) {
            AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork = getOrCreateNetwork(affects, informedEntity);

            if (informedEntity.hasRouteId()) {
                AffectedLineStructure affectedLine = getOrCreateLine(affectedNetwork, informedEntity);
                addDestinationStop(affectedLine, informedEntity);
            }
        } else if (informedEntity.hasStopId()) {
            addAffectedStop(affects, informedEntity);
        }
    }

    /**
     * Records affected stops to the general affect structure
     * (case for stops without line and without network specified)
     *
     * @param affects        all currently processed affects
     * @param informedEntity the current entity for which affect must be recorded
     */
    private static void addAffectedStop(AffectsScopeStructure affects, GtfsRealtime.EntitySelector informedEntity) {

        String stopId = informedEntity.getStopId();


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
    private static void addDestinationStop(AffectedLineStructure affectedLine, GtfsRealtime.EntitySelector informedEntity) {

        if (!informedEntity.hasStopId()) {
            return;
        }

        String stopId = informedEntity.getStopId();

        for (AffectedStopPointStructure destination : affectedLine.getDestinations()) {
            if (destination.getStopPointRef().getValue().equals(stopId)) {
                return;
            }
        }

        AffectedStopPointStructure newStop = new AffectedStopPointStructure();
        StopPointRef newStopRef = new StopPointRef();
        newStopRef.setValue(stopId);
        newStop.setStopPointRef(newStopRef);
        affectedLine.getDestinations().add(newStop);
    }

    /**
     * Read affects to find the line affected by the situation
     *
     * @param affectedNetwork currently affected network
     * @param informedEntity  the entity containing the affected line that must be recorded
     * @return the current affected line (recovered or created)
     */

    private static AffectedLineStructure getOrCreateLine(AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork, GtfsRealtime.EntitySelector informedEntity) {
        String routeId = informedEntity.getRouteId();

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



    private static List<AffectedVehicleJourneyStructure> getVehicleJourneys(GtfsRealtime.EntitySelector informedEntity) {
        AffectsScopeStructure.VehicleJourneys vehicleJourneys = new AffectsScopeStructure.VehicleJourneys();

        if (informedEntity.hasTrip()) {
            AffectedVehicleJourneyStructure vehicleJourney = new AffectedVehicleJourneyStructure();

            GtfsRealtime.TripDescriptor tripDescriptor = informedEntity.getTrip();
            if (tripDescriptor != null)
                mapTripDescriptor(tripDescriptor, vehicleJourney);

            vehicleJourneys.getAffectedVehicleJourneies().add(vehicleJourney);
        }
        return vehicleJourneys.getAffectedVehicleJourneies();

    }


    private static void mapTripDescriptor(GtfsRealtime.TripDescriptor tripDescriptor, AffectedVehicleJourneyStructure vehicleJourney) {
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
            LineRef lineRef = new LineRef();
            lineRef.setValue(tripDescriptor.getRouteId());
            vehicleJourney.setLineRef(lineRef);
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
                ZonedDateTime timestamp = ZonedDateTime.ofInstant(Instant.ofEpochMilli(Long.MIN_VALUE), zoneId);
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

            translationText = translationText.replaceAll(" ", "\\s+");

            DefaultedTextStructure defaultedTextStructure = new DefaultedTextStructure();
            defaultedTextStructure.setValue(translationText);

            String lang = translation.getLanguage() == null || translation.getLanguage().equals("") ? "FR" : translation.getLanguage();

            defaultedTextStructure.setLang(lang);

            siriTextStructures.add(defaultedTextStructure);
        }

        return siriTextStructures;
    }

}
