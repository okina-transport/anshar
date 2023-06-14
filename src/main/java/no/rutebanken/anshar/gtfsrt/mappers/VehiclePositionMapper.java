package no.rutebanken.anshar.gtfsrt.mappers;


import com.google.protobuf.Timestamp;
import com.google.transit.realtime.GtfsRealtime;
import io.micrometer.core.instrument.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.*;


import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;




/***
 * Utility class to convert vehiclePosition (GTFS RT) to vehicleActivity (SIRI)
 */

public class VehiclePositionMapper {

    private static final DateFormat gtfsRtDateFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
    private static final Logger logger = LoggerFactory.getLogger(VehiclePositionMapper.class);



    /**
     * Main function that converts vehiclePosition (GTFS-RT) to a vehicleActivity(SIRI)
     *
     * @param vehiclePosition
     *      A vehiclePosition coming from GTFS-RT
     * @return
     *      A vehicleActivity (SIRI format)
     */
    public static VehicleActivityStructure mapVehicleActivityFromVehiclePosition(GtfsRealtime.VehiclePosition vehiclePosition) {


        VehicleActivityStructure activity = new VehicleActivityStructure();

        GtfsRealtime.TripDescriptor tripDescriptor = vehiclePosition.getTrip();

        FramedVehicleJourneyRefStructure framedVehicleJourneyRefBuilder = new FramedVehicleJourneyRefStructure();
        framedVehicleJourneyRefBuilder.setDatedVehicleJourneyRef(tripDescriptor.getTripId());
        DataFrameRefStructure dataFrameRef = new DataFrameRefStructure();
        dataFrameRef.setValue("");
        framedVehicleJourneyRefBuilder.setDataFrameRef(dataFrameRef);

        VehicleActivityStructure.MonitoredVehicleJourney monitoredVehiclejourney = new VehicleActivityStructure.MonitoredVehicleJourney();

        monitoredVehiclejourney.setFramedVehicleJourneyRef(framedVehicleJourneyRefBuilder);
        monitoredVehiclejourney.setDataSource("MOBIITI");




        mapTridData(monitoredVehiclejourney, vehiclePosition.getTrip());
        mapVehicleRef(activity, monitoredVehiclejourney, vehiclePosition.getVehicle());
        mapRecordedAtTime(activity, vehiclePosition);
        mapPosition(monitoredVehiclejourney, vehiclePosition.getPosition());
        mapStatus(activity, monitoredVehiclejourney, vehiclePosition);
        mapOccupancy(monitoredVehiclejourney, vehiclePosition);
        mapCongestion(monitoredVehiclejourney, vehiclePosition);

        activity.setMonitoredVehicleJourney(monitoredVehiclejourney);
        return activity;
    }

    private static void mapCongestion(VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney, GtfsRealtime.VehiclePosition vehiclePosition) {
        if (vehiclePosition.getCongestionLevel() != null){
            monitoredVehicleJourney.setInCongestion(GtfsRealtime.VehiclePosition.CongestionLevel.CONGESTION.equals(vehiclePosition.getCongestionLevel()));
        }
    }


    private static void mapOccupancy(VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney,GtfsRealtime.VehiclePosition vehiclePosition) {
        if(vehiclePosition.getOccupancyStatus() != null){
            switch (vehiclePosition.getOccupancyStatus()) {
                case FULL:
                    monitoredVehicleJourney.setOccupancy(OccupancyEnumeration.FULL);
                    break;
                case STANDING_ROOM_ONLY:
                    monitoredVehicleJourney.setOccupancy(OccupancyEnumeration.STANDING_AVAILABLE);
                    break;
                case FEW_SEATS_AVAILABLE:
                    monitoredVehicleJourney.setOccupancy(OccupancyEnumeration.SEATS_AVAILABLE);
                    break;
            }
        }
    }

    private static void mapStatus(VehicleActivityStructure activity , VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney, GtfsRealtime.VehiclePosition vehiclePosition) {

        if (GtfsRealtime.VehiclePosition.VehicleStopStatus.STOPPED_AT.equals(vehiclePosition.getCurrentStatus())) {
            MonitoredCallStructure monitoredCallStruct = new MonitoredCallStructure();
            LocationStructure locStruct = new LocationStructure();
            locStruct.setLongitude(BigDecimal.valueOf(vehiclePosition.getPosition().getLongitude()));
            locStruct.setLatitude(BigDecimal.valueOf(vehiclePosition.getPosition().getLatitude()));
            monitoredCallStruct.setVehicleLocationAtStop(locStruct);

            if (StringUtils.isNotEmpty(vehiclePosition.getStopId())){
                StopPointRefStructure stopPointRef = new StopPointRefStructure();
                stopPointRef.setValue(vehiclePosition.getStopId());
                monitoredCallStruct.setStopPointRef(stopPointRef);
            }
            monitoredCallStruct.setOrder(BigInteger.valueOf(vehiclePosition.getCurrentStopSequence()));
            monitoredVehicleJourney.setMonitoredCall(monitoredCallStruct);

        }else if(GtfsRealtime.VehiclePosition.VehicleStopStatus.INCOMING_AT.equals(vehiclePosition.getCurrentStatus())){
            // Arbitrary behaviour : "incoming at" status set to 90% progress
            ProgressBetweenStopsStructure progressBetStruct = new ProgressBetweenStopsStructure();
            progressBetStruct.setPercentage(BigDecimal.valueOf(90));
            activity.setProgressBetweenStops(progressBetStruct);
        }
    }

    private static void mapPosition(VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney, GtfsRealtime.Position position) {
        if (position == null)
            return;

        LocationStructure locationStructure = new LocationStructure();
        locationStructure.setLatitude(BigDecimal.valueOf(position.getLatitude()));
        locationStructure.setLongitude(BigDecimal.valueOf(position.getLongitude()));
        monitoredVehicleJourney.setBearing(position.getSpeed());
        monitoredVehicleJourney.setVelocity(BigInteger.valueOf((long)position.getSpeed()));
        monitoredVehicleJourney.setVehicleLocation(locationStructure);
    }

    private static void mapRecordedAtTime(VehicleActivityStructure activity, GtfsRealtime.VehiclePosition vehiclePosition) {
        if (vehiclePosition.getTimestamp() != 0) {
            ZonedDateTime timestamp = ZonedDateTime.ofInstant(Instant.ofEpochMilli(vehiclePosition.getTimestamp()), ZoneId.systemDefault());
            activity.setRecordedAtTime(timestamp);
        }
    }

    private static void mapTridData(VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney, GtfsRealtime.TripDescriptor tripDescriptor) {
        if (tripDescriptor.getRouteId() != null) {
            LineRef lineRef = new LineRef();
            lineRef.setValue(tripDescriptor.getRouteId());
            monitoredVehicleJourney.setLineRef(lineRef);
        }

        if (StringUtils.isNotEmpty(tripDescriptor.getStartDate()) && StringUtils.isNotEmpty(tripDescriptor.getStartTime())) {
            Timestamp.Builder tsBuilder = Timestamp.newBuilder();
            try {
                Date date = gtfsRtDateFormat.parse(tripDescriptor.getStartDate() + " " + tripDescriptor.getStartTime());
                tsBuilder.setSeconds(date.getTime() / 1000);
                ZonedDateTime startDate = ZonedDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
                monitoredVehicleJourney.setOriginAimedDepartureTime(startDate);
            } catch (ParseException e) {
                logger.error("Unable to parse trip descriptor start date/time:" + tripDescriptor.getStartDate() + " " + tripDescriptor.getStartTime());
            }
        }
    }

    private static void mapVehicleRef(VehicleActivityStructure activity, VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney, GtfsRealtime.VehicleDescriptor vehicleDescriptor) {
        if (vehicleDescriptor != null && vehicleDescriptor.getId() != null) {
            VehicleRef vehicleRef = new VehicleRef();
            vehicleRef.setValue(vehicleDescriptor.getId());
            monitoredVehicleJourney.setVehicleRef(vehicleRef);
            VehicleMonitoringRefStructure vehicleRefStruct = new VehicleMonitoringRefStructure();
            vehicleRefStruct.setValue(vehicleDescriptor.getId());
            activity.setVehicleMonitoringRef(vehicleRefStruct);
        }
    }



}
