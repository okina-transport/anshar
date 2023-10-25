package no.rutebanken.anshar.routes.siri.transformer.JsonDTOs;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;


@Getter
@Setter
public class VehicleMonitoringJsonDTO {


    @JsonProperty("ServiceDelivery")
    private ServiceDelivery serviceDelivery;


    @Getter
    @Setter
    public static class ServiceDelivery {


        @JsonProperty("ResponseTimestamp")
        private String responseTimestamp;

        @JsonProperty("RequestMessageRef")
        private String requestMessageRef;

        @JsonProperty("VehicleMonitoringDelivery")
        private List<VehicleMonitoringDelivery> vehicleMonitoringDelivery;


        @Getter
        @Setter
        public static class VehicleMonitoringDelivery {
            @JsonProperty("ResponseTimestamp")
            private String responseTimestamp;

            @JsonProperty("ValidUntil")
            private String validUntil;

            @JsonProperty("ShortestPossibleCycle")
            private String shortestPossibleCycle;

            @JsonProperty("VehicleActivity")
            private List<VehicleActivity> vehicleActivity;

        }

        @Getter
        @Setter
        public static class VehicleActivity {
            @JsonProperty("RecordedAtTime")
            private String recordedAtTime;

            @JsonProperty("MonitoredVehicleJourney")
            private MonitoredVehicleJourney monitoredVehicleJourney;

        }

        @Getter
        @Setter
        public static class MonitoredVehicleJourney {
            @JsonProperty("LineRef")
            private String lineRef;

            @JsonProperty("DirectionRef")
            private int directionRef;

            @JsonProperty("FramedVehicleJourneyRef")
            private FramedVehicleJourneyRef framedVehicleJourneyRef;

            @JsonProperty("VehicleMode")
            private String vehicleMode;

            @JsonProperty("PublishedLineName")
            private String publishedLineName;

            @JsonProperty("DestinationName")
            private String destinationName;

            @JsonProperty("DestinationShortName")
            private String destinationShortName;

            @JsonProperty("DestinationRef")
            private String destinationRef;

            @JsonProperty("Via")
            private String via;

            @JsonProperty("VehicleLocation")
            private VehicleLocation vehicleLocation;

            @JsonProperty("Bearing")
            private double bearing;

            @JsonProperty("Delay")
            private String delay;

            @JsonProperty("VehicleRef")
            private String vehicleRef;

            @JsonProperty("VehicleType")
            private String vehicleType;

            @JsonProperty("VehicleStatus")
            private String vehicleStatus;

            @JsonProperty("MonitoredCall")
            private MonitoredCall monitoredCall;

            @JsonProperty("PreviousCall")
            private List<PreviousCall> previousCall;

            @JsonProperty("OnwardCall")
            private List<OnwardCall> onwardCall;

        }

        @Getter
        @Setter
        public static class FramedVehicleJourneyRef {
            @JsonProperty("DatedVehicleJourneySAERef")
            private String datedVehicleJourneySAERef;

        }

        @Getter
        @Setter
        public static class VehicleLocation {
            @JsonProperty("Longitude")
            private double longitude;

            @JsonProperty("Latitude")
            private double latitude;

        }

        @Getter
        @Setter
        public static class MonitoredCall {
            @JsonProperty("StopPointName")
            private String stopPointName;

            @JsonProperty("StopCode")
            private String stopCode;

            @JsonProperty("Order")
            private int order;

            @JsonProperty("ExpectedDepartureTime")
            private String expectedDepartureTime;

            @JsonProperty("ExpectedArrivalTime")
            private String expectedArrivalTime;

            @JsonProperty("Extension")
            private Extension extension;

        }

        @Getter
        @Setter
        public static class PreviousCall {
            @JsonProperty("StopPointName")
            private String stopPointName;

            @JsonProperty("StopCode")
            private String stopCode;

            @JsonProperty("Order")
            private int order;

        }

        @Getter
        @Setter
        public static class OnwardCall {
            @JsonProperty("StopPointName")
            private String stopPointName;

            @JsonProperty("StopCode")
            private String stopCode;

            @JsonProperty("Order")
            private int order;

            @JsonProperty("ExpectedDepartureTime")
            private String expectedDepartureTime;

            @JsonProperty("ExpectedArrivalTime")
            private String expectedArrivalTime;

        }

        @Getter
        @Setter
        public static class Extension {
            @JsonProperty("IsRealTime")
            private boolean isRealTime;

            @JsonProperty("SAELineRef")
            private String saeLineRef;

            @JsonProperty("DataSource")
            private String dataSource;


        }
    }
}
