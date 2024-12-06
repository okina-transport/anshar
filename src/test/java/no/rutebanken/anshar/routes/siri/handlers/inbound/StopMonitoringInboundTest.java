package no.rutebanken.anshar.routes.siri.handlers.inbound;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.siri.siri21.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class StopMonitoringInboundTest {

    private static final String LINE_NAME = "STAS:Line:10:LOC";
    private static final String VEHICLE_JOURNEY_NAME = "STAS:VehicleJourney:9948:LOC";
    private static final String STOP_POINT_NAME_1 = "STAS:StopPoint:BP:33FZ10:LOC";
    private static final String STOP_POINT_NAME_2 = "STAS:StopPoint:BP:33FZ11:LOC";
    private static final String ITEM_IDENTIFIER_1 = "17989886865";
    private static final String ITEM_IDENTIFIER_2 = "1798988778";
    private static final String ITEM_IDENTIFIER_3 = "1798988999";
    private static final LocalDateTime departure = LocalDateTime.of(2020, 1, 1, 11, 0, 0, 0);
    private static final LocalDateTime arrival = LocalDateTime.of(2020, 1, 1, 12, 0, 0, 0);

    @InjectMocks
    private StopMonitoringInbound stopMonitoringInbound;

    @Test
    void updateStopMonitoringItemIdentifierTest() {
        ZonedDateTime departureTime = ZonedDateTime.of(departure, ZoneId.systemDefault());
        ZonedDateTime arrivalTime = ZonedDateTime.of(arrival, ZoneId.systemDefault());

        MonitoredStopVisit monitoredStopVisit1 = buildMonitoredStopVisit(ITEM_IDENTIFIER_1, STOP_POINT_NAME_1, LINE_NAME,
                VEHICLE_JOURNEY_NAME, departureTime, null);
        MonitoredStopVisit monitoredStopVisit2 = buildMonitoredStopVisit(ITEM_IDENTIFIER_2, STOP_POINT_NAME_2, LINE_NAME,
                VEHICLE_JOURNEY_NAME, null, arrivalTime);
        MonitoredStopVisit monitoredStopVisit3 = buildMonitoredStopVisit(ITEM_IDENTIFIER_3, STOP_POINT_NAME_2, LINE_NAME,
                VEHICLE_JOURNEY_NAME, null, null);

        List<MonitoredStopVisit> inputList = new ArrayList<>(3);
        inputList.add(monitoredStopVisit1);
        inputList.add(monitoredStopVisit2);
        inputList.add(monitoredStopVisit3);

        stopMonitoringInbound.updateStopMonitoringItemIdentifier(inputList);

        assertThat(inputList).hasSize(3);
        assertThat(monitoredStopVisit1.getItemIdentifier()).isEqualTo(STOP_POINT_NAME_1+LINE_NAME+VEHICLE_JOURNEY_NAME+departureTime.toInstant());
        assertThat(monitoredStopVisit2.getItemIdentifier()).isEqualTo(STOP_POINT_NAME_2+LINE_NAME+VEHICLE_JOURNEY_NAME+arrivalTime.toInstant());
        assertThat(monitoredStopVisit3.getItemIdentifier()).isEqualTo(ITEM_IDENTIFIER_3);
    }

    private MonitoredStopVisit buildMonitoredStopVisit(String itemIdentifier, String stopName,  String lineName,
                                                       String vehicleJourneyName, ZonedDateTime departure, ZonedDateTime arrival) {
        MonitoredStopVisit stopVisit = new MonitoredStopVisit();
        stopVisit.setItemIdentifier(itemIdentifier);

        MonitoringRefStructure monitoringRefStructure = new MonitoringRefStructure();
        monitoringRefStructure.setValue(stopName);
        stopVisit.setMonitoringRef(monitoringRefStructure);

        MonitoredVehicleJourneyStructure monitoredVehicleJourneyStructure = buildVehicleJourneyRef(lineName, vehicleJourneyName, departure, arrival);
        stopVisit.setMonitoredVehicleJourney(monitoredVehicleJourneyStructure);
        return stopVisit;
    }

    private MonitoredVehicleJourneyStructure buildVehicleJourneyRef(String lineName, String vehicleJourneyName, ZonedDateTime departure, ZonedDateTime arrival) {
        MonitoredVehicleJourneyStructure monitoredVehicleJourneyStructure = new MonitoredVehicleJourneyStructure();

        LineRef lineRef = new LineRef();
        lineRef.setValue(lineName);
        monitoredVehicleJourneyStructure.setLineRef(lineRef);

        FramedVehicleJourneyRefStructure framedVehicleJourneyRefStructure = new FramedVehicleJourneyRefStructure();
        framedVehicleJourneyRefStructure.setDatedVehicleJourneyRef(vehicleJourneyName);
        monitoredVehicleJourneyStructure.setFramedVehicleJourneyRef(framedVehicleJourneyRefStructure);

        MonitoredCallStructure monitoredCallStructure = new MonitoredCallStructure();
        monitoredCallStructure.setAimedDepartureTime(departure);
        monitoredCallStructure.setAimedArrivalTime(arrival);

        monitoredVehicleJourneyStructure.setMonitoredCall(monitoredCallStructure);

        return monitoredVehicleJourneyStructure;
    }

}