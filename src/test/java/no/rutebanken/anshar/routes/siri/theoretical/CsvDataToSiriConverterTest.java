package no.rutebanken.anshar.routes.siri.theoretical;

import org.junit.jupiter.api.Test;
import uk.org.siri.siri20.MonitoredStopVisit;
import uk.org.siri.siri20.MonitoredVehicleJourneyStructure;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CsvDataToSiriConverterTest {

    private static final ZoneId ZONE_ID_EUROPE_PARIS = ZoneId.of("Europe/Paris");
    public static final String STOP_MONITORING_REF = "44033";
    public static final String STOP_POINT_NAME = "Gendarmerie";
    public static final String VEHICLE_JOURNEY_IDENTIFIER = "40327";
    public static final String LINE_IDENTIFIER = "4403";
    public static final String LINE_NAME = "VALS - LABEGUDE - AUBENAS";
    public static final String DIRECTION_NAME = "A";
    public static final String ORIGIN_REF = "44119";
    public static final String ORIGIN_NAME = "Vals Collège";
    public static final String DESTINATION_REF = "44619";
    public static final String DESTINATION_NAME = "AUBENAS Gare Routière QUAI 19";
    private final CsvDataToSiriConverter converter = new CsvDataToSiriConverter();

    @Test
    void convertTheoreticalDataToSiriTest() {
        TheoreticalStopMonitoringInfo data = TheoreticalStopMonitoringInfo.builder()
                .date(LocalDate.of(2023, 10, 1))
                .monitoringRef(STOP_MONITORING_REF)
                .stopPointName(STOP_POINT_NAME)
                .monitoredVehicleJourneyRef(VEHICLE_JOURNEY_IDENTIFIER)
                .lineRef(LINE_IDENTIFIER)
                .publishedLineName(LINE_NAME)
                .directionName(DIRECTION_NAME)
                .aimedDepartureTime(LocalTime.of(12, 30, 0))
                .aimedArrivalTime(LocalTime.of(12, 30, 0))
                .originRef(ORIGIN_REF)
                .originName(ORIGIN_NAME)
                .destinationRef(DESTINATION_REF)
                .destinationName(DESTINATION_NAME)
                .build();

        MonitoredStopVisit result = converter.mapToStopVisit(data);

        assertThat(result).isNotNull();
        assertThat(result.getMonitoringRef()).isNotNull();
        assertThat(result.getMonitoringRef().getValue()).isEqualTo(data.getMonitoringRef());
        assertThat(result.getMonitoredVehicleJourney()).isNotNull();
        assertThat(result.getItemIdentifier()).isEqualTo(data.getMonitoringRef() + "_" + data.getMonitoredVehicleJourneyRef()+ "_20231001_123000");
        assertThat(result.getRecordedAtTime()).isNotNull();

        MonitoredVehicleJourneyStructure mvj = result.getMonitoredVehicleJourney();
        assertThat(mvj.getLineRef()).isNotNull();
        assertThat(mvj.getLineRef().getValue()).isEqualTo(data.getLineRef());
        assertThat(mvj.getDestinationRef().getValue()).isEqualTo(data.getDestinationRef());
        assertThat(mvj.getDestinationNames()).extracting("value").contains(data.getDestinationName());
        assertThat(mvj.getOriginRef().getValue()).isEqualTo(data.getOriginRef());
        assertThat(mvj.getOriginNames()).extracting("value").contains(data.getOriginName());
        assertThat(mvj.getMonitoredCall().getStopPointRef().getValue()).isEqualTo(data.getMonitoringRef());
        assertThat(mvj.getMonitoredCall().getAimedArrivalTime()).isEqualTo(ZonedDateTime.of(2023, 10, 1, 12, 30, 0, 0, ZONE_ID_EUROPE_PARIS));
        assertThat(mvj.getMonitoredCall().getAimedDepartureTime()).isEqualTo(ZonedDateTime.of(2023, 10, 1, 12, 30, 0, 0, ZONE_ID_EUROPE_PARIS));
        assertThat(mvj.getMonitoredCall().getDestinationDisplaies()).extracting("value").contains(data.getDestinationName());
    }


}