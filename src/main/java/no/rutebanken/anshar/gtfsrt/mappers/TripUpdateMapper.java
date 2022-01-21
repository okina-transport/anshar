package no.rutebanken.anshar.gtfsrt.mappers;

import com.google.transit.realtime.GtfsRealtime;
import uk.org.siri.siri20.DatedVehicleJourneyRef;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.StopPointRef;
import uk.org.siri.siri20.VehicleRef;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;



/***
 * Utility class to convert Trip Update (GTFS RT)  to estimated time table (SIRI)
 */

public class TripUpdateMapper {




    /**
     * Main function that converts tripUpdate (GTFS-RT) to estimated time table (SIRI)
     *
     * @param tripUpdate
     *      A tripUpdate coming from GTFS-RT
     * @return
     *      An estimated time table (SIRI format)
     */
    public static EstimatedVehicleJourney mapVehicleJourneyFromTripUpdate(GtfsRealtime.TripUpdate tripUpdate) {

        GtfsRealtime.TripDescriptor tripDescriptor = tripUpdate.getTrip();

        EstimatedVehicleJourney journey = new EstimatedVehicleJourney();


        DatedVehicleJourneyRef vehicleJourneyRef = new DatedVehicleJourneyRef();
        vehicleJourneyRef.setValue(tripDescriptor.getTripId());
        journey.setDatedVehicleJourneyRef(vehicleJourneyRef);


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
