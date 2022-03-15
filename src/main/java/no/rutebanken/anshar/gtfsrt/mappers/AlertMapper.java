package no.rutebanken.anshar.gtfsrt.mappers;


import com.google.transit.realtime.GtfsRealtime;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.AffectedOperatorStructure;
import uk.org.siri.siri20.AffectedStopPointStructure;
import uk.org.siri.siri20.AffectedVehicleJourneyStructure;
import uk.org.siri.siri20.AffectsScopeStructure;
import uk.org.siri.siri20.DefaultedTextStructure;
import uk.org.siri.siri20.EnvironmentReasonEnumeration;
import uk.org.siri.siri20.EquipmentReasonEnumeration;
import uk.org.siri.siri20.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.MiscellaneousReasonEnumeration;
import uk.org.siri.siri20.OperatorRefStructure;
import uk.org.siri.siri20.PersonnelReasonEnumeration;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.StopPointRef;

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


    /**
     * Main function that converts alert (GTFS-RT) to situation exchange(SIRI)
     *
     * @param alert
     *      An alert coming from GTFS-RT
     * @return
     *      A situation exchange time table (SIRI format)
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
            return ;


        AffectsScopeStructure affectStruct = new AffectsScopeStructure();
        AffectsScopeStructure.Operators operators = new AffectsScopeStructure.Operators();
        AffectsScopeStructure.StopPoints stopPoints = new AffectsScopeStructure.StopPoints();
        AffectsScopeStructure.VehicleJourneys vehicleJourneys = new AffectsScopeStructure.VehicleJourneys();





        for (GtfsRealtime.EntitySelector informedEntity : informedEntities) {

            operators.getAffectedOperators().addAll(getOperators(informedEntity));
            stopPoints.getAffectedStopPoints().addAll(getStopPoints(informedEntity));
            vehicleJourneys.getAffectedVehicleJourneies().addAll(getVehicleJourneys(informedEntity));

        }

        affectStruct.setOperators(operators);
        affectStruct.setStopPoints(stopPoints);
        affectStruct.setVehicleJourneys(vehicleJourneys);


        ptSituationElement.setAffects(affectStruct);


    }

    private static List<AffectedVehicleJourneyStructure> getVehicleJourneys(GtfsRealtime.EntitySelector informedEntity ){
        AffectsScopeStructure.VehicleJourneys vehicleJourneys = new AffectsScopeStructure.VehicleJourneys();

        if (informedEntity.hasTrip()){
            AffectedVehicleJourneyStructure vehicleJourney = new AffectedVehicleJourneyStructure();

            GtfsRealtime.TripDescriptor tripDescriptor = informedEntity.getTrip();
            if (tripDescriptor != null )
                mapTripDescriptor(tripDescriptor, vehicleJourney);

            vehicleJourneys.getAffectedVehicleJourneies().add(vehicleJourney);
        }
        return vehicleJourneys.getAffectedVehicleJourneies();

    }

    private static List<AffectedStopPointStructure> getStopPoints(GtfsRealtime.EntitySelector informedEntity ){
        AffectsScopeStructure.StopPoints stopPoints = new AffectsScopeStructure.StopPoints();

        if (informedEntity.hasStopId()){
            AffectedStopPointStructure stopPoint = new AffectedStopPointStructure();
            StopPointRef stopPointRef = new StopPointRef();
            stopPointRef.setValue(informedEntity.getStopId());
            stopPoint.setStopPointRef(stopPointRef);
            stopPoints.getAffectedStopPoints().add(stopPoint);
        }

        return stopPoints.getAffectedStopPoints();

    }

    private static List<AffectedOperatorStructure> getOperators(GtfsRealtime.EntitySelector informedEntity ){

        AffectsScopeStructure.Operators operators = new AffectsScopeStructure.Operators();
        if (informedEntity.hasAgencyId()){
            AffectedOperatorStructure affectedOperator = new AffectedOperatorStructure();
            OperatorRefStructure operatorRefStructure = new OperatorRefStructure();
            operatorRefStructure.setValue(informedEntity.getAgencyId());
            affectedOperator.setOperatorRef(operatorRefStructure);
            operators.getAffectedOperators().add(affectedOperator);
        }
        return operators.getAffectedOperators();
    }

    private static void mapTripDescriptor(GtfsRealtime.TripDescriptor tripDescriptor, AffectedVehicleJourneyStructure vehicleJourney){
        if (StringUtils.isNotEmpty(tripDescriptor.getStartDate())){
            try {
                Date startDate = DATE_FORMATTER.parse(tripDescriptor.getStartDate());
                ZonedDateTime departureTime = ZonedDateTime.ofInstant(startDate.toInstant(),ZoneId.systemDefault());
                vehicleJourney.setOriginAimedDepartureTime(departureTime);
            } catch (ParseException e) {
                logger.error("Unable to parse start date :" + tripDescriptor.getStartDate());
            }
        }

        if (StringUtils.isNotEmpty(tripDescriptor.getRouteId())){
            LineRef lineRef = new LineRef();
            lineRef.setValue(tripDescriptor.getRouteId());
            vehicleJourney.setLineRef(lineRef);
        }


    }

    private static void mapReasons(PtSituationElement ptSituationElement, GtfsRealtime.Alert alert) {

        if (alert.getCause() == null)
            return;

        switch(alert.getCause()){
            case WEATHER:
                ptSituationElement.setEnvironmentReason(EnvironmentReasonEnumeration.UNKNOWN);
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
                ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.FATALITY);
                break;
            case POLICE_ACTIVITY:
                ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.POLICE_ACTIVITY);
                break;
            case TECHNICAL_PROBLEM:
                ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.INCIDENT);
                break;
            case HOLIDAY:
                ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.HOLIDAY);
                break;
        }
    }

    private static void mapPeriod(PtSituationElement ptSituationElement, GtfsRealtime.Alert alert) {

        for (GtfsRealtime.TimeRange timeRange : alert.getActivePeriodList()) {

            HalfOpenTimestampOutputRangeStructure validityPeriod = new HalfOpenTimestampOutputRangeStructure();

            if (timeRange.hasStart()){
                ZonedDateTime timestamp = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeRange.getStart() * 1000), ZoneId.systemDefault());
                validityPeriod.setStartTime(timestamp);
            }

            if (timeRange.hasEnd()){
                ZonedDateTime timestamp = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeRange.getEnd() * 1000), ZoneId.systemDefault());
                validityPeriod.setEndTime(timestamp);
            }

            if (timeRange.hasStart() || timeRange.hasEnd()){
                ptSituationElement.getValidityPeriods().add(validityPeriod);
            }
        }
    }

    private static void mapDescription(PtSituationElement ptSituationElement, GtfsRealtime.Alert alert) {

        if (alert.getHeaderText() != null){
            ptSituationElement.getSummaries().addAll(translate(alert.getHeaderText()));
        }

        if (alert.getDescriptionText() != null){
            ptSituationElement.getDescriptions().addAll(translate(alert.getDescriptionText()));
        }
    }

    private static List<DefaultedTextStructure> translate(GtfsRealtime.TranslatedString gtfsTranslatedString){

        if (gtfsTranslatedString.getTranslationList() == null || gtfsTranslatedString.getTranslationList().size() == 0)
            return new ArrayList<>();

        List<DefaultedTextStructure> siriTextStructures = new ArrayList<>();

        for (GtfsRealtime.TranslatedString.Translation translation : gtfsTranslatedString.getTranslationList()) {

            String translationText = translation.getText();
            if (StringUtils.isEmpty(translationText))
                continue;

            translationText = translationText.replaceAll( " ","\\s+");

            DefaultedTextStructure defaultedTextStructure = new DefaultedTextStructure();
            defaultedTextStructure.setValue(translationText);

            if (translation.getLanguage() != null){
                defaultedTextStructure.setLang("LANG_TYPE_" + translation.getLanguage().toUpperCase());
            }

            siriTextStructures.add(defaultedTextStructure);
        }

        return siriTextStructures;
    }


}