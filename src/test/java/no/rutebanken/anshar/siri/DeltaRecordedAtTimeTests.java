package no.rutebanken.anshar.siri;

import no.rutebanken.anshar.data.MonitoredStopVisits;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.siri.handlers.inbound.StopMonitoringInbound;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.*;

import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DeltaRecordedAtTimeTests extends SpringBootBaseTest {

    @Autowired
    private MonitoredStopVisits monitoredStopVisits;

    @Autowired
    private PrometheusMetricsService prometheusMetricsService;

    @Autowired
    StopMonitoringInbound stopMonitoringInbound;


    @Test
    @Disabled
    public void testSimpleRecordedAtTime() {

        List<MonitoredStopVisit> stopVisitsToIngest = createStopVists("STOP1");
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);


        String scrapeResult = prometheusMetricsService.scrape();
        System.out.println("scrapeResult: " + scrapeResult);
        String[] resTab = scrapeResult.split("\n");
        String deltaStr = Arrays.stream(resTab).filter(
                line -> line.contains("app_anshar_data_delta_recorded_at_time") && line.contains("DAT1")
        ).findFirst().get();

        Double deltaRecorded = extractValue(deltaStr);
        System.out.println("deltaRecorded: " + deltaRecorded);
        assertTrue(deltaRecorded > 29000, "delta should be arround 30000ms");
        assertTrue(deltaRecorded < 31000, "delta should be arround 30000ms");


        //Launching a second scrape with NO new data: deltaRecordedAtTime should be empty
        String scrapeResult2 = prometheusMetricsService.scrape();
        String[] resTab2 = scrapeResult2.split("\n");
        System.out.println("scrapeResult: " + scrapeResult2);

        String deltaStr2 = Arrays.stream(resTab2).filter(
                line -> line.contains("app_anshar_data_delta_recorded_at_time") && line.contains("DAT1")
        ).findFirst().get();

        System.out.println("deltaStr2: " + deltaStr2);
        Double deltaRecorded2 = extractValue(deltaStr2);
        assertEquals(0d, deltaRecorded2);

    }

    @Test
    @Disabled
    public void testMultipleDataset() {

        List<MonitoredStopVisit> stopVisitsToIngest = createStopVists("STOP1");
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);

        stopMonitoringInbound.ingestStopVisits("DAT2", stopVisitsToIngest);


        String scrapeResult = prometheusMetricsService.scrape();
        System.out.println("scrapeResult: " + scrapeResult);
        String[] resTab = scrapeResult.split("\n");
        String deltaStr = Arrays.stream(resTab).filter(
                line -> line.contains("app_anshar_data_delta_recorded_at_time") && line.contains("DAT1")
        ).findFirst().get();

        String deltaStrDAT2 = Arrays.stream(resTab).filter(
                line -> line.contains("app_anshar_data_delta_recorded_at_time") && line.contains("DAT2")
        ).findFirst().get();

        Double deltaRecorded = extractValue(deltaStr);
        System.out.println("deltaRecorded: " + deltaRecorded);
        assertTrue(deltaRecorded > 29000, "delta should be arround 30000ms");
        assertTrue(deltaRecorded < 31000, "delta should be arround 30000ms");


        //Launching a second scrape with NO new data: deltaRecordedAtTime should be empty
        String scrapeResult2 = prometheusMetricsService.scrape();
        String[] resTab2 = scrapeResult2.split("\n");
        System.out.println("scrapeResult: " + scrapeResult2);

        String deltaStr2 = Arrays.stream(resTab2).filter(
                line -> line.contains("app_anshar_data_delta_recorded_at_time") && line.contains("DAT1")
        ).findFirst().get();

        System.out.println("deltaStr2: " + deltaStr2);
        Double deltaRecorded2 = extractValue(deltaStr2);
        assertEquals(0d, deltaRecorded2);

    }

    @Test
    @Disabled
    public void testMultipleRecordedAtTime() {

        List<MonitoredStopVisit> stopVisitsToIngest = createStopVists("STOP1");
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);

        //Creating a second list of visits with the same delta. DeltaRecordedAtTime should still be around 30000ms
        List<MonitoredStopVisit> stopVisitsToIngest2 = createStopVists("STOP1");
        stopMonitoringInbound.ingestStopVisits("DAT1", stopVisitsToIngest);


        String scrapeResult = prometheusMetricsService.scrape();
        //  System.out.println("scrapeResult: " + scrapeResult);
        String[] resTab = scrapeResult.split("\n");
        String deltaStr = Arrays.stream(resTab).filter(
                line -> line.contains("app_anshar_data_delta_recorded_at_time") && line.contains("DAT1")
        ).findFirst().get();

        Double deltaRecorded = extractValue(deltaStr);
        System.out.println("deltaRecorded: " + deltaRecorded);
        assertTrue(deltaRecorded > 29000, "delta should be arround 30000ms");
        assertTrue(deltaRecorded < 31000, "delta should be arround 30000ms");


        //Launching a second scrape with NO new data: deltaRecordedAtTime should be empty
        String scrapeResult2 = prometheusMetricsService.scrape();
        String[] resTab2 = scrapeResult2.split("\n");
        //  System.out.println("scrapeResult: " + scrapeResult2);

        String deltaStr2 = Arrays.stream(resTab2).filter(
                line -> line.contains("app_anshar_data_delta_recorded_at_time") && line.contains("DAT1")
        ).findFirst().get();

        System.out.println("deltaStr2: " + deltaStr2);
        Double deltaRecorded2 = extractValue(deltaStr2);
        assertEquals(0d, deltaRecorded2);
    }


    private List<MonitoredStopVisit> createStopVists(String stopRef) {

        List<MonitoredStopVisit> stopVisits = new ArrayList<>();

        ZonedDateTime recordedBase = ZonedDateTime.now();
        System.out.println("recordedBase: " + recordedBase);
        ZonedDateTime recordedMinus30s = recordedBase.minusSeconds(30);
        System.out.println("recordedMinus30s: " + recordedMinus30s);
        ZonedDateTime recordedMinus60s = recordedBase.minusSeconds(60);
        System.out.println("recordedMinus60s: " + recordedMinus60s);

        MonitoredStopVisit stopVisit1 = createFakeMonitoredStopVisit(stopRef, recordedBase);
        stopVisits.add(stopVisit1);


        MonitoredStopVisit stopVisit2 = createFakeMonitoredStopVisit(stopRef, recordedMinus30s);
        stopVisits.add(stopVisit2);


        MonitoredStopVisit stopVisit3 = createFakeMonitoredStopVisit(stopRef, recordedMinus60s);
        stopVisits.add(stopVisit3);

        return stopVisits;
    }

    private MonitoredStopVisit createFakeMonitoredStopVisit(String stopRef, ZonedDateTime recordedAtTime) {
        String randomUUID = UUID.randomUUID().toString();
        MonitoredStopVisit stopVisit = new MonitoredStopVisit();
        MonitoringRefStructure monRefStruc = new MonitoringRefStructure();
        monRefStruc.setValue(stopRef);
        stopVisit.setMonitoringRef(monRefStruc);

        stopVisit.setRecordedAtTime(recordedAtTime);
        stopVisit.setItemIdentifier(randomUUID);
        MonitoredVehicleJourneyStructure vehicleJourney = new MonitoredVehicleJourneyStructure();
        LineRef lineRef = new LineRef();
        lineRef.setValue(randomUUID);
        vehicleJourney.setLineRef(lineRef);
        FramedVehicleJourneyRefStructure frameVJ = new FramedVehicleJourneyRefStructure();
        frameVJ.setDatedVehicleJourneyRef(randomUUID);
        vehicleJourney.setFramedVehicleJourneyRef(frameVJ);
        vehicleJourney.setMonitored(true);

        MonitoredCallStructure monitoredCallStructure = new MonitoredCallStructure();
        StopPointRefStructure stopPointRefStructure = new StopPointRefStructure();
        stopPointRefStructure.setValue(stopRef);
        monitoredCallStructure.setStopPointRef(stopPointRefStructure);
        Random random = new Random();
        ZonedDateTime now = ZonedDateTime.now();
        int randomArrivalDelay = random.nextInt(60) + 1;
        ZonedDateTime randomArrival = now.minusMinutes(randomArrivalDelay);

        monitoredCallStructure.setAimedArrivalTime(randomArrival);
        monitoredCallStructure.setAimedDepartureTime(randomArrival);

        int randomDelay = random.nextInt(60) + 1;
        monitoredCallStructure.setExpectedArrivalTime(randomArrival.plusMinutes(randomDelay));
        monitoredCallStructure.setExpectedDepartureTime(randomArrival.plusMinutes(randomDelay));

        vehicleJourney.setMonitoredCall(monitoredCallStructure);


        stopVisit.setMonitoredVehicleJourney(vehicleJourney);


        return stopVisit;
    }

    private double extractValue(String line) {
        return Double.valueOf(line.split(" ")[1]);
    }


}
