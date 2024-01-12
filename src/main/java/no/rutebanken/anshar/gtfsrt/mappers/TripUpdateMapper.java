package no.rutebanken.anshar.gtfsrt.mappers;

import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.routes.mapping.StopTimesService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.org.siri.siri20.*;

import java.math.BigInteger;
import java.time.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/***
 * Utility class to convert Trip Update (GTFS RT)  to estimated time table (SIRI)
 */
@Component
public class TripUpdateMapper {

    private static final Logger logger = LoggerFactory.getLogger(TripUpdateMapper.class);

    @Autowired
    StopTimesService stopTimesService;


    /**
     * Read a tripUpdate and creates siri objects
     *
     * @param tripUpdate GTFS-RT object to read
     * @param datasetId
     * @return A list of siri objects
     */
    public List<MonitoredStopVisit> mapStopVisitFromTripUpdate(GtfsRealtime.TripUpdate tripUpdate, String datasetId) {
        List<MonitoredStopVisit> stopVisitList = new ArrayList<>();


        FramedVehicleJourneyRefStructure vehicleJourneyRef = createVehicleJourneyRef(tripUpdate);

        String tripId = tripUpdate.getTrip().getTripId();
        if(tripUpdate.getTrip().getScheduleRelationship() != null && GtfsRealtime.TripDescriptor.ScheduleRelationship.UNSCHEDULED.equals(
        tripUpdate.getTrip().getScheduleRelationship())){
            return Collections.emptyList();
        }
        LineRef lineRef = createLineRef(tripUpdate, datasetId, tripId);
        DestinationRef destinationRef = createDestinationRef(datasetId, tripId);
        NaturalLanguageStringStructure destinationName = createDestinationName(destinationRef);

        for (GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate : tripUpdate.getStopTimeUpdateList()) {
            MonitoredStopVisit stopVisit = new MonitoredStopVisit();

            String stopId = getStopId(stopTimeUpdate, datasetId, tripId);
            if (StringUtils.isEmpty(stopId)) {
                logger.error("Unable to determine stopId for dataset:{}, tripId:{}, stopSequence:{}, stopId:{}", datasetId, tripId, stopTimeUpdate.getStopSequence(), stopTimeUpdate.getStopId());
            }
            mapMonitoringRef(stopVisit, stopId);
            MonitoredVehicleJourneyStructure monitoredVehicleStruct = new MonitoredVehicleJourneyStructure();
            monitoredVehicleStruct.setLineRef(lineRef);
            monitoredVehicleStruct.setDestinationRef(destinationRef);
            monitoredVehicleStruct.getDestinationNames().add(destinationName);
            monitoredVehicleStruct.setFramedVehicleJourneyRef(vehicleJourneyRef);
            monitoredVehicleStruct.setMonitored(true);
            MonitoredCallStructure monitoredCallStructure = new MonitoredCallStructure();
            StopPointRef stopPointRef = new StopPointRef();
            stopPointRef.setValue(stopId);
            monitoredCallStructure.setStopPointRef(stopPointRef);
            monitoredCallStructure.setOrder(BigInteger.valueOf(stopTimeUpdate.getStopSequence()));
            mapArrival(monitoredCallStructure, stopTimeUpdate);
            mapDeparture(monitoredCallStructure, stopTimeUpdate, datasetId, tripId);
            monitoredVehicleStruct.setMonitoredCall(monitoredCallStructure);
            stopVisit.setMonitoredVehicleJourney(monitoredVehicleStruct);
            feedItemIdentifier(stopVisit, stopId);
            stopVisitList.add(stopVisit);
        }


        return stopVisitList;
    }

    /**
     * Feeed itemIdentifier field with a concatenation between vehicleJourney and stop
     *
     * @param stopVisit the stopVisit on which itemIdentifier must be updated
     * @param stopId    the stopId on which the visit occurs
     */
    private void feedItemIdentifier(MonitoredStopVisit stopVisit, String stopId) {
        String vehicleJourneyRef = stopVisit.getMonitoredVehicleJourney().getFramedVehicleJourneyRef().getDatedVehicleJourneyRef();
        stopVisit.setItemIdentifier(vehicleJourneyRef + "-" + stopId);
    }


    /**
     * Read the tripUpdate and map departure times (aimed and expected) to siri object
     *
     * @param monitoredCallStructure the siri object
     * @param stopTimeUpdate         source object from GTFS-RT file that contains data to read
     */
    private void mapDeparture(MonitoredCallStructure monitoredCallStructure, GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate, String datasetId, String tripId) {
        if (!stopTimeUpdate.hasDeparture() || stopTimeUpdate.getDeparture().getTime() == 0) {
            return;
        }

        long departureTimeSeconds = stopTimeUpdate.getDeparture().getTime();
        ZonedDateTime expectedDeparture = ZonedDateTime.ofInstant(Instant.ofEpochMilli(departureTimeSeconds * 1000), ZoneId.systemDefault());
        monitoredCallStructure.setExpectedDepartureTime(expectedDeparture);

        long aimedDepartureSeconds = departureTimeSeconds - stopTimeUpdate.getDeparture().getDelay();
        LocalDate localDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(aimedDepartureSeconds * 1000), ZoneId.systemDefault()).toLocalDate();

        ZonedDateTime aimedDeparture = stopTimesService.getDepartureTime(datasetId, tripId, stopTimeUpdate.getStopSequence()).isPresent() ?
                ZonedDateTime.of(localDate, LocalTime.parse(stopTimesService.getDepartureTime(datasetId, tripId, stopTimeUpdate.getStopSequence()).get()),  ZoneId.systemDefault()) :
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(aimedDepartureSeconds * 1000), ZoneId.systemDefault());;

        monitoredCallStructure.setAimedDepartureTime(aimedDeparture);
    }


    /**
     * Read the tripUpdate and map arrival times (aimed and expected) to siri object
     *
     * @param monitoredCallStructure the siri object
     * @param stopTimeUpdate         source object from GTFS-RT file that contains data to read
     */
    private static void mapArrival(MonitoredCallStructure monitoredCallStructure, GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate) {
        if (!stopTimeUpdate.hasArrival() || stopTimeUpdate.getArrival().getTime() == 0) {
            return;
        }

        long arrivalTimeSeconds = stopTimeUpdate.getArrival().getTime();
        ZonedDateTime expectedArrival = ZonedDateTime.ofInstant(Instant.ofEpochMilli(arrivalTimeSeconds * 1000), ZoneId.systemDefault());
        monitoredCallStructure.setExpectedArrivalTime(expectedArrival);

        ZonedDateTime aimedArrival;

        if (stopTimeUpdate.getArrival().getDelay() != 0) {
            long aimedArrivalSeconds = arrivalTimeSeconds - stopTimeUpdate.getArrival().getDelay();
            aimedArrival = ZonedDateTime.ofInstant(Instant.ofEpochMilli(aimedArrivalSeconds * 1000), ZoneId.systemDefault());
        } else {
            aimedArrival = expectedArrival;
        }
        monitoredCallStructure.setAimedArrivalTime(aimedArrival);
    }


    /**
     * Read a tripUpdate and create a vehicleJourneyRef
     *
     * @param tripUpdate the tripUpdate from which the vehicleJourney must be read
     * @return The vehicleJourneyRef
     */
    private static FramedVehicleJourneyRefStructure createVehicleJourneyRef(GtfsRealtime.TripUpdate tripUpdate) {
        String tripId = tripUpdate.getTrip() != null ? tripUpdate.getTrip().getTripId() : "";
        FramedVehicleJourneyRefStructure vehicleJourneyRef = new FramedVehicleJourneyRefStructure();
        vehicleJourneyRef.setDatedVehicleJourneyRef(tripId);

        DataFrameRefStructure dataFrameRef = new DataFrameRefStructure();
        dataFrameRef.setValue(tripId);
        vehicleJourneyRef.setDataFrameRef(dataFrameRef);
        return vehicleJourneyRef;
    }


    /**
     * Read the tripUpdate and create a lineRef with routeId included in tripUpdate
     *
     * @param tripUpdate The trip update from which the routeId must be read
     * @return The lineRef containing the routeId
     */
    private LineRef createLineRef(GtfsRealtime.TripUpdate tripUpdate, String datasetId, String tripId) {
        String routeId = tripUpdate.getTrip() != null && StringUtils.isNotEmpty(tripUpdate.getTrip().getRouteId())? tripUpdate.getTrip().getRouteId() : stopTimesService.getRouteId(datasetId, tripId).isPresent() ?
                stopTimesService.getRouteId(datasetId, tripId).get() : "";
        LineRef lineRef = new LineRef();

        lineRef.setValue(routeId);
        return lineRef;
    }

    private DestinationRef createDestinationRef(String datasetId, String tripId) {

        DestinationRef destinationRef = new DestinationRef();
        destinationRef.setValue(stopTimesService.getDestinationId(datasetId,tripId).isPresent() ? stopTimesService.getDestinationId(datasetId,tripId).get() : "");
        return destinationRef;
    }

    private NaturalLanguageStringStructure createDestinationName(DestinationRef destinationRef) {
        NaturalLanguageStringStructure naturalLanguageStringStructure = new NaturalLanguageStringStructure();
        naturalLanguageStringStructure.setValue(destinationRef.getValue());
        return naturalLanguageStringStructure;
    }

    private String getStopId(GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate, String datasetId, String tripId) {
        if (stopTimeUpdate.hasStopId()) {
            return stopTimeUpdate.getStopId();
        }

        return stopTimesService.getStopId(datasetId, tripId, stopTimeUpdate.getStopSequence()).orElse(null);
    }

    private void mapMonitoringRef(MonitoredStopVisit stopVisit, String stopId) {

        MonitoringRefStructure monitoringRefStruct = new MonitoringRefStructure();
        monitoringRefStruct.setValue(stopId);
        stopVisit.setMonitoringRef(monitoringRefStruct);
    }


    /**
     * Main function that converts tripUpdate (GTFS-RT) to estimated time table (SIRI)
     *
     * @param tripUpdate A tripUpdate coming from GTFS-RT
     * @return An estimated time table (SIRI format)
     */
    public static EstimatedVehicleJourney mapVehicleJourneyFromTripUpdate(GtfsRealtime.TripUpdate tripUpdate) {

        GtfsRealtime.TripDescriptor tripDescriptor = tripUpdate.getTrip();

        EstimatedVehicleJourney journey = new EstimatedVehicleJourney();


        DatedVehicleJourneyRef datedVehicleJourneyRef = new DatedVehicleJourneyRef();
        datedVehicleJourneyRef.setValue(tripDescriptor.getTripId());
        journey.setDatedVehicleJourneyRef(datedVehicleJourneyRef);

        FramedVehicleJourneyRefStructure vehicleJourneyRef = createVehicleJourneyRef(tripUpdate);
        journey.setFramedVehicleJourneyRef(vehicleJourneyRef);
        journey.setDataSource("MOBIITI");


        if (tripDescriptor.getRouteId() != null) {
            LineRef lineRef = new LineRef();
            lineRef.setValue(tripDescriptor.getRouteId());
            journey.setLineRef(lineRef);
        }


        GtfsRealtime.VehicleDescriptor vehicleDescriptor = tripUpdate.getVehicle();

        if (vehicleDescriptor.getId() != null) {
            VehicleRef vehicleRef = new VehicleRef();
            vehicleRef.setValue(vehicleDescriptor.getId());
            journey.setVehicleRef(vehicleRef);
        }

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();


        for (GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate : tripUpdate.getStopTimeUpdateList()) {
            EstimatedCall estimatedCall = mapEstimatedCallFromTripUpdate(stopTimeUpdate);
            estimatedCalls.getEstimatedCalls().add(estimatedCall);
        }

        journey.setEstimatedCalls(estimatedCalls);

        return journey;
    }

    private static EstimatedCall mapEstimatedCallFromTripUpdate(GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate) {

        EstimatedCall estimatedCall = new EstimatedCall();
        StopPointRef spRef = new StopPointRef();
        spRef.setValue(stopTimeUpdate.getStopId());
        estimatedCall.setStopPointRef(spRef);
        estimatedCall.setOrder(BigInteger.valueOf(stopTimeUpdate.getStopSequence()));

        if (stopTimeUpdate.hasDeparture() && stopTimeUpdate.getDeparture().getTime() != 0) {
            GtfsRealtime.TripUpdate.StopTimeEvent departureEvent = stopTimeUpdate.getDeparture();
            int departureDelay = departureEvent.getDelay();
            long departureTimeMillis = departureEvent.getTime() * 1000;
            ZonedDateTime departureTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(departureTimeMillis), ZoneId.systemDefault());
            estimatedCall.setAimedDepartureTime(departureTime);
            ZonedDateTime departureExpected = departureTime.plusSeconds(departureDelay);
            estimatedCall.setExpectedDepartureTime(departureExpected);
        }

        if (stopTimeUpdate.hasArrival() & stopTimeUpdate.getArrival().getTime() != 0) {
            GtfsRealtime.TripUpdate.StopTimeEvent arrivalEvent = stopTimeUpdate.getArrival();
            int arrivalDelay = arrivalEvent.getDelay();
            long arrivalTimeMillis = arrivalEvent.getTime() * 1000;
            ZonedDateTime arrivalTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(arrivalTimeMillis), ZoneId.systemDefault());
            estimatedCall.setAimedArrivalTime(arrivalTime);
            ZonedDateTime arrivalExpected = arrivalTime.plusSeconds(arrivalDelay);
            estimatedCall.setExpectedArrivalTime(arrivalExpected);
        }

        return estimatedCall;

    }

    /**
     * Read a tripUpdate and creates siri objects
     *
     * @param tripUpdate GTFS-RT object to read
     * @param datasetId
     * @return A list of siri objects
     */
    public List<MonitoredStopVisitCancellation> mapStopCancellationFromTripUpdate(GtfsRealtime.TripUpdate tripUpdate, String datasetId) {
        if (tripUpdate.getTrip().getScheduleRelationship() != null && !GtfsRealtime.TripDescriptor.ScheduleRelationship.UNSCHEDULED.equals(
                tripUpdate.getTrip().getScheduleRelationship())) {
            return Collections.emptyList();
        }
        List<MonitoredStopVisitCancellation> stopVisitCancellations = new ArrayList<>();

        FramedVehicleJourneyRefStructure vehicleJourneyRef = createVehicleJourneyRef(tripUpdate);
        String tripId = tripUpdate.getTrip().getTripId();

        LineRef lineRef = createLineRef(tripUpdate, datasetId, tripId);

        for (GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate : tripUpdate.getStopTimeUpdateList()) {
            MonitoredStopVisitCancellation monitoredStopVisitCancellation = new MonitoredStopVisitCancellation();

            String stopId = getStopId(stopTimeUpdate, datasetId, tripId);

            if (StringUtils.isEmpty(stopId)) {
                logger.error("Unable to determine stopId for dataset:{}, tripId:{}, stopSequence:{}, stopId:{}", datasetId, tripId, stopTimeUpdate.getStopSequence(), stopTimeUpdate.getStopId());
            }
            MonitoringRefStructure monitoringRefStruct = new MonitoringRefStructure();
            monitoringRefStruct.setValue(stopId);
            monitoredStopVisitCancellation.setMonitoringRef(monitoringRefStruct);
            monitoredStopVisitCancellation.setVehicleJourneyRef(vehicleJourneyRef);
            monitoredStopVisitCancellation.setLineRef(lineRef);

            ItemRefStructure itemRefStructure = new ItemRefStructure();

            //This id has to permit to recognize the SM "datasetId-tripId-stopId-lineId"
            itemRefStructure.setValue(datasetId + "-" + tripId + "-" + stopId + "-" + lineRef.getValue());

            monitoredStopVisitCancellation.setItemRef(itemRefStructure);

            stopVisitCancellations.add(monitoredStopVisitCancellation);
        }

        return stopVisitCancellations;
    }
}
