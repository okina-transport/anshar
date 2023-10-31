package no.rutebanken.anshar.gtfsrt.mappers;

import com.google.transit.realtime.GtfsRealtime;
import net.logstash.logback.encoder.org.apache.commons.lang3.StringUtils;
import no.rutebanken.anshar.routes.mapping.StopTimesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.org.siri.siri20.*;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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


        LineRef lineRef = createLineRef(tripUpdate);
        FramedVehicleJourneyRefStructure vehicleJourneyRef = createVehicleJourneyRef(tripUpdate);

        String tripId = tripUpdate.getTrip().getTripId();

        for (GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate : tripUpdate.getStopTimeUpdateList()) {
            MonitoredStopVisit stopVisit = new MonitoredStopVisit();

            String stopId = getStopId(stopTimeUpdate, datasetId, tripId);
            if (StringUtils.isEmpty(stopId)) {
                logger.error("Unable to determine stopId for dataset:{}, tripId:{}, stopSequence:{}, stopId:{}", datasetId, tripId, stopTimeUpdate.getStopSequence(), stopTimeUpdate.getStopId());
            }
            mapMonitoringRef(stopVisit, stopId);
            MonitoredVehicleJourneyStructure monitoredVehicleStruct = new MonitoredVehicleJourneyStructure();
            monitoredVehicleStruct.setLineRef(lineRef);
            monitoredVehicleStruct.setFramedVehicleJourneyRef(vehicleJourneyRef);
            monitoredVehicleStruct.setMonitored(true);
            MonitoredCallStructure monitoredCallStructure = new MonitoredCallStructure();
            StopPointRef stopPointRef = new StopPointRef();
            stopPointRef.setValue(stopId);
            monitoredCallStructure.setStopPointRef(stopPointRef);
            monitoredCallStructure.setOrder(BigInteger.valueOf(stopTimeUpdate.getStopSequence()));
            mapArrival(monitoredCallStructure, stopTimeUpdate);
            mapDeparture(monitoredCallStructure, stopTimeUpdate);
            monitoredVehicleStruct.setMonitoredCall(monitoredCallStructure);
            stopVisit.setMonitoredVehicleJourney(monitoredVehicleStruct);
            stopVisitList.add(stopVisit);
        }

        return stopVisitList;
    }


    /**
     * Read the tripUpdate and map departure times (aimed and expected) to siri object
     *
     * @param monitoredCallStructure the siri object
     * @param stopTimeUpdate         source object from GTFS-RT file that contains data to read
     */
    private static void mapDeparture(MonitoredCallStructure monitoredCallStructure, GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate) {
        if (!stopTimeUpdate.hasDeparture() || stopTimeUpdate.getDeparture().getTime() == 0) {
            return;
        }

        long aimedDepartureSeconds = stopTimeUpdate.getDeparture().getTime();
        ZonedDateTime aimedDeparture = ZonedDateTime.ofInstant(Instant.ofEpochMilli(aimedDepartureSeconds * 1000), ZoneId.systemDefault());
        monitoredCallStructure.setAimedDepartureTime(aimedDeparture);

        long expectedSeconds = aimedDepartureSeconds + stopTimeUpdate.getDeparture().getDelay();
        ZonedDateTime expectedDeparture = ZonedDateTime.ofInstant(Instant.ofEpochMilli(expectedSeconds * 1000), ZoneId.systemDefault());
        monitoredCallStructure.setExpectedDepartureTime(expectedDeparture);
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

        long aimedArrivalSeconds = stopTimeUpdate.getArrival().getTime();
        ZonedDateTime aimedArrival = ZonedDateTime.ofInstant(Instant.ofEpochMilli(aimedArrivalSeconds * 1000), ZoneId.systemDefault());
        monitoredCallStructure.setAimedArrivalTime(aimedArrival);

        long expectedSeconds = aimedArrivalSeconds + stopTimeUpdate.getArrival().getDelay();
        ZonedDateTime expectedArrival = ZonedDateTime.ofInstant(Instant.ofEpochMilli(expectedSeconds * 1000), ZoneId.systemDefault());
        monitoredCallStructure.setExpectedArrivalTime(expectedArrival);

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
    private static LineRef createLineRef(GtfsRealtime.TripUpdate tripUpdate) {
        String routeId = tripUpdate.getTrip() != null ? tripUpdate.getTrip().getRouteId() : "";
        LineRef lineRef = new LineRef();

        lineRef.setValue(routeId);
        return lineRef;
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


}
