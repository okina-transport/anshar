package no.rutebanken.anshar.consistency;

import no.rutebanken.anshar.consistency.exception.DatasetNotFoundException;
import no.rutebanken.anshar.consistency.model.ConsistencyReport;
import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.routes.mapping.LineUpdaterService;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.mapping.VehicleJourneyService;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.DatasetService;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.siri.siri21.Siri;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class ConsistencyServiceTest {

    public static final String DATASET_ID = "test";
    private static final Siri ANSHAR_CACHE_SIRI_ET;
    private static final Siri ANSHAR_CACHE_SIRI_FM;
    private static final Siri ANSHAR_CACHE_SIRI_GM;
    private static final Siri ANSHAR_CACHE_SIRI_SM;
    private static final Siri ANSHAR_CACHE_SIRI_SX;
    private static final Siri ANSHAR_CACHE_SIRI_VM;

    static {
        try {
            ANSHAR_CACHE_SIRI_ET = new Siri();
            ANSHAR_CACHE_SIRI_FM = new Siri();
            ANSHAR_CACHE_SIRI_GM = CustomSiriXml.parseXml(Files.readString(Path.of(
                    "src/test/resources/consistency/anshar_cache_siri_gm.xml"
            )));
            ANSHAR_CACHE_SIRI_SM = CustomSiriXml.parseXml(Files.readString(Path.of(
                    "src/test/resources/consistency/anshar_cache_siri_sm.xml"
            )));
            ANSHAR_CACHE_SIRI_SX = CustomSiriXml.parseXml(Files.readString(Path.of(
                    "src/test/resources/consistency/anshar_cache_siri_sx.xml"
            )));
            ANSHAR_CACHE_SIRI_VM = CustomSiriXml.parseXml(Files.readString(Path.of(
                    "src/test/resources/consistency/anshar_cache_siri_vm.xml"
            )));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Mock
    SiriHandler siriHandler;
    @Mock
    LineUpdaterService lineUpdaterService;
    @Mock
    StopPlaceUpdaterService stopPlaceUpdaterService;
    @Mock
    VehicleJourneyService vehicleJourneyService;
    @Mock
    DatasetService datasetService;
    @InjectMocks
    ConsistencyService tested;

    @Test
    public void test_buildReportForDataset_whenDatasetDoesNotExist_thenThrowsException() {
        // Arrage
        Mockito.when(datasetService.exists(DATASET_ID)).thenReturn(false);

        // Act & Assert
        assertThrows(DatasetNotFoundException.class, () -> tested.buildReportForDataset(DATASET_ID));
    }

    @Test
    public void test_buildReportForDataset_whenThereIsNoDataInCache_thenBuildEmptyReport() throws InvocationTargetException, IllegalAccessException {
        // Arrange
        Mockito.when(datasetService.exists(DATASET_ID)).thenReturn(true);
        Mockito.when(siriHandler.buildSiriResponse(any(), any())).thenReturn(new Siri());

        // Act
        var report = tested.buildReportForDataset(DATASET_ID);

        // Assert
        assertEquals(DATASET_ID, report.getDataset(), "dataset must match");
        assertNotNull(report.getStart(), "start must be set");
        assertNotNull(report.getEnd(), "end must be set");
        assertTrue(MapUtils.isEmpty(report.getConsistencies()), "consistencies must be empty");
    }

    @Test
    public void test_buildReportForDataset_whenThereIsDataInCache_thenExtractAllIdsFromSiri() throws InvocationTargetException, IllegalAccessException {
        // Arrange
        Mockito.when(datasetService.exists(DATASET_ID)).thenReturn(true);
        Mockito.when(siriHandler.buildSiriResponse(any(), any())).thenAnswer(
                invocation -> {
                    Siri inputRequest = invocation.getArgument(1, Siri.class);
                    if (CollectionUtils.isNotEmpty(inputRequest.getServiceRequest().getEstimatedTimetableRequests())) {
                        return ANSHAR_CACHE_SIRI_ET;
                    } else if (CollectionUtils.isNotEmpty(inputRequest.getServiceRequest().getFacilityMonitoringRequests())) {
                        return ANSHAR_CACHE_SIRI_FM;
                    } else if (CollectionUtils.isNotEmpty(inputRequest.getServiceRequest().getGeneralMessageRequests())) {
                        return ANSHAR_CACHE_SIRI_GM;
                    } else if (CollectionUtils.isNotEmpty(inputRequest.getServiceRequest().getStopMonitoringRequests())) {
                        return ANSHAR_CACHE_SIRI_SM;
                    } else if (CollectionUtils.isNotEmpty(inputRequest.getServiceRequest().getSituationExchangeRequests())) {
                        return ANSHAR_CACHE_SIRI_SX;
                    } else if (CollectionUtils.isNotEmpty(inputRequest.getServiceRequest().getVehicleMonitoringRequests())) {
                        return ANSHAR_CACHE_SIRI_VM;
                    }
                    return null;
                });
        Mockito.when(lineUpdaterService.exists(any())).thenReturn(false); // match all ids
        Mockito.when(stopPlaceUpdaterService.exists(any())).thenReturn(false); // match all ids
        Mockito.when(vehicleJourneyService.exists(any())).thenReturn(false); // match all ids

        // Act
        var report = tested.buildReportForDataset(DATASET_ID);

        // Assert
        // sort consistency.lines/stops/vehicleJourneys.matchedIds for assertions
        report.getConsistencies().forEach((type, c) -> {
            if (c.getLines() != null) {
                c.getLines().getUnmatchedIds().sort(String::compareToIgnoreCase);
            }
            if (c.getStops() != null) {
                c.getStops().getUnmatchedIds().sort(String::compareToIgnoreCase);
            }
            if (c.getVehicleJourneys() != null) {
                c.getVehicleJourneys().getUnmatchedIds().sort(String::compareToIgnoreCase);
            }
        });
        var expectedGMConsistency =
                new ConsistencyReport.Consistency();
        expectedGMConsistency.setLines(new ConsistencyReport.MatchResult(0, List.of("NAOLIBORG:Line:30:LOC")));
        expectedGMConsistency.setStops(new ConsistencyReport.MatchResult(0, List.of(
                "FR_NAOLIB:Quay:1509",
                "FR_NAOLIB:Quay:1546",
                "FR_NAOLIB:Quay:320"
        )));

        var expectedSXConsistency = new ConsistencyReport.Consistency();
        expectedSXConsistency.setLines(new ConsistencyReport.MatchResult(0, List.of(
                "NAOLIBORG:Line:107:LOC",
                "NAOLIBORG:Line:117:LOC",
                "NAOLIBORG:Line:127:LOC",
                "NAOLIBORG:Line:147:LOC",
                "NAOLIBORG:Line:157:LOC",
                "NAOLIBORG:Line:80:LOC"
        )));
        expectedSXConsistency.setStops(new ConsistencyReport.MatchResult(0, List.of(
                "FR_NAOLIB:Quay:414",
                "FR_NAOLIB:Quay:415",
                "FR_NAOLIB:Quay:437",
                "FR_NAOLIB:Quay:438"
        )));

        var expectedSMConsistency = new ConsistencyReport.Consistency();
        expectedSMConsistency.setLines(new ConsistencyReport.MatchResult(0, List.of(
                "NAOLIBORG:Line:23:LOC",
                "NAOLIBORG:Line:24:LOC"
        )));
        expectedSMConsistency.setStops(new ConsistencyReport.MatchResult(0, List.of(
                "FR_NAOLIB:Quay:29",
                "FR_NAOLIB:Quay:30",
                "FR_NAOLIB:Quay:515",
                "FR_NAOLIB:Quay:516"
        )));
        expectedSMConsistency.setVehicleJourneys(new ConsistencyReport.MatchResult(0, List.of(
                "NAOLIBORG:VehicleJourney:44542228-CR_24_25-HD25P1J1-L-Ma-Me-J-20:LOC",
                "NAOLIBORG:VehicleJourney:666666-CR_24_25-HD25P1J1-L-Ma-Me-J-20:LOC"
        )));

        var expectedVMConsistency = new ConsistencyReport.Consistency();
        expectedVMConsistency.setLines(new ConsistencyReport.MatchResult(0, List.of(
                "BORDEAUX_METROPOLE:Line:25:LOC",
                "BORDEAUX_METROPOLE:Line:59:LOC",
                "BORDEAUX_METROPOLE:Line:82:LOC"
        )));
        expectedVMConsistency.setVehicleJourneys(new ConsistencyReport.MatchResult(0, List.of(
                "BORDEAUX_METROPOLE:ServiceJourney:268438016_7:LOC",
                "BORDEAUX_METROPOLE:ServiceJourney:268441025_7:LOC",
                "BORDEAUX_METROPOLE:ServiceJourney:268608182_7:LOC"
        )));

        assertEquals(DATASET_ID, report.getDataset(), "dataset must match");
        assertNotNull(report.getStart(), "start must be set");
        assertNotNull(report.getEnd(), "end must be set");
        assertNull(report.getConsistencies().get(SiriDataType.ESTIMATED_TIMETABLE), "ET flow must be null");
        assertNull(report.getConsistencies().get(SiriDataType.FACILITY_MONITORING), "FM flow must be null");
        assertEquals(expectedGMConsistency, report.getConsistencies().get(SiriDataType.GENERAL_MESSAGE), "GM flow must match");
        assertEquals(expectedSXConsistency, report.getConsistencies().get(SiriDataType.SITUATION_EXCHANGE), "SX flow must match");
        assertEquals(expectedSMConsistency, report.getConsistencies().get(SiriDataType.STOP_MONITORING), "SM flow must match");
        assertEquals(expectedVMConsistency, report.getConsistencies().get(SiriDataType.VEHICLE_MONITORING), "VM flow must match");
    }

    @Test
    public void test_buildReportForDataset_whenThereIsDataInCache_thenMatchedIdsAndUnmatchedIdsAreGeneratedProperly() throws InvocationTargetException, IllegalAccessException {
        // Arrange
        Mockito.when(datasetService.exists(DATASET_ID)).thenReturn(true);
        Mockito.when(siriHandler.buildSiriResponse(any(), any())).thenAnswer(invocation -> {
            if (CollectionUtils.isNotEmpty(invocation.getArgument(1, Siri.class).getServiceRequest().getStopMonitoringRequests())) {
                return ANSHAR_CACHE_SIRI_SM;
            } else {
                return new Siri();
            }
        });
        Mockito.when(lineUpdaterService.exists(any())).thenAnswer(
                i -> i.getArgument(0).toString().equals("NAOLIBORG:Line:23:LOC")
        );
        Mockito.when(stopPlaceUpdaterService.exists(any())).thenAnswer(
                i -> List.of("FR_NAOLIB:Quay:29", "FR_NAOLIB:Quay:30").contains(i.getArgument(0).toString()));
        Mockito.when(vehicleJourneyService.exists(any())).thenAnswer(i -> i.getArgument(0).toString().equals("NAOLIBORG:VehicleJourney:44542228-CR_24_25-HD25P1J1-L-Ma-Me-J-20:LOC"));

        // Act
        var report = tested.buildReportForDataset(DATASET_ID);

        // Assert
        // sort consistency.stops.matchedIds/unmatchedIds for assertions
        report.getConsistencies().forEach((type, c) -> c.getStops().getUnmatchedIds().sort(String::compareToIgnoreCase));
        assertEquals(DATASET_ID, report.getDataset(), "dataset must match");
        assertNotNull(report.getStart(), "start must be set");
        assertNotNull(report.getEnd(), "end must be set");

        assertEquals(1,
                report.getConsistencies().get(SiriDataType.STOP_MONITORING).getLines().getNbMatch(),
                "lines.nbMatch should be computed properly");
        assertEquals(List.of("NAOLIBORG:Line:24:LOC"),
                report.getConsistencies().get(SiriDataType.STOP_MONITORING).getLines().getUnmatchedIds(),
                "lines.unmatchedIds should be computed properly");
        assertEquals(2,
                report.getConsistencies().get(SiriDataType.STOP_MONITORING).getStops().getNbMatch(),
                "stops.nbMatch should be computed properly");
        assertEquals(List.of("FR_NAOLIB:Quay:515", "FR_NAOLIB:Quay:516"),
                report.getConsistencies().get(SiriDataType.STOP_MONITORING).getStops().getUnmatchedIds(),
                "stops.unmatchedIds should be computed properly");
        assertEquals(1,
                report.getConsistencies().get(SiriDataType.STOP_MONITORING).getVehicleJourneys().getNbMatch(),
                "vehicleJourneys.nbMatch should be computed properly");
        assertEquals(List.of("NAOLIBORG:VehicleJourney:666666-CR_24_25-HD25P1J1-L-Ma-Me-J-20:LOC"),
                report.getConsistencies().get(SiriDataType.STOP_MONITORING).getVehicleJourneys().getUnmatchedIds(),
                "vehicleJourneys.unmatchedIds should be computed properly");
    }


}
