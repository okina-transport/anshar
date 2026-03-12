package no.rutebanken.anshar.mapping;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.mapping.LineUpdaterService;
import no.rutebanken.anshar.routes.mapping.VehicleJourney.VehicleJourney;
import no.rutebanken.anshar.routes.mapping.VehicleJourney.VehicleJourneyCache;
import no.rutebanken.anshar.routes.siri.processor.UpdateVJIdProcessor;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.util.MappingUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RecomputeVehicleJourneyIdFromTheoretical extends SpringBootBaseTest {

    private static final DateTimeFormatter DF_YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DF_HHMMSS = DateTimeFormatter.ofPattern("HHmmss");

    @Mock
    private VehicleJourneyCache vjCache;

    @Mock
    private LineUpdaterService lineUpdaterService;

    private static final String DATASET_ID = "DAT1";
    private static final String LINE_ID = "LINE1";
    private static final String STOP_ID = "STOP1";
    private static final String DIRECTION_ID = "A";
    private static final String DESTINATION_ID = "STOP2";
    private static final String EXPECTED_VJ_ID_FROM_TH = "TH_VJ_ID_1";

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @BeforeEach
    public void initIdProcessing() {
        subscriptionConfig.getIdProcessingParameters().clear();
        IdProcessingParameters lineIPP = new IdProcessingParameters();
        lineIPP.setObjectType(ObjectType.LINE);
        lineIPP.setDatasetId(DATASET_ID);
        subscriptionConfig.getIdProcessingParameters().add(lineIPP);

    }


    @Test
    public void test_vjid_replacement_by_theoretical() {

        ZonedDateTime now = ZonedDateTime.now();
        String key = MappingUtils.buildIneoVJKey(now.format(DF_YYYYMMDD), now.format(DF_HHMMSS), LINE_ID, DIRECTION_ID, STOP_ID, DATASET_ID);

        VehicleJourney expectedVJ = new VehicleJourney(EXPECTED_VJ_ID_FROM_TH, "", "", 1);

        when(vjCache.findVehicleJourney(key)).thenReturn(Optional.of(expectedVJ));
        when(lineUpdaterService.getLineNumber(LINE_ID)).thenReturn(Optional.of(LINE_ID));
        UpdateVJIdProcessor updateVjProc = new UpdateVJIdProcessor(DATASET_ID, vjCache, lineUpdaterService);


        // siri has been generated with wrong VJ id
        Siri siri = generateSiriWithVJ(now);

        // launching vj id replacement
        updateVjProc.process(siri);

        // expected vj to be replaced by TH data
        MonitoredStopVisit firstSM = siri.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().getFirst();
        assertEquals(EXPECTED_VJ_ID_FROM_TH, firstSM.getMonitoredVehicleJourney().getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());
        String expectedItemIdentifier = STOP_ID + "_" + EXPECTED_VJ_ID_FROM_TH + "_" + now.format(DF_YYYYMMDD) + "_" + now.format(DF_HHMMSS);
        assertEquals(expectedItemIdentifier, firstSM.getItemIdentifier());

    }

    @Test
    public void test_vjid_not_replaced_because_unknown() {

        ZonedDateTime now = ZonedDateTime.now();
        String key = MappingUtils.buildIneoVJKey(now.plusMinutes(10).format(DF_YYYYMMDD), now.plusMinutes(10).format(DF_HHMMSS), LINE_ID, DIRECTION_ID, STOP_ID, DATASET_ID);

        when(vjCache.findVehicleJourney(key)).thenReturn(Optional.empty());
        when(lineUpdaterService.getLineNumber(LINE_ID)).thenReturn(Optional.of(LINE_ID));
        UpdateVJIdProcessor updateVjProc = new UpdateVJIdProcessor(DATASET_ID, vjCache, lineUpdaterService);

        // siri has been generated with wrong VJ id
        Siri siri = generateSiriWithVJ(now.plusMinutes(10));

        // launching vj id replacement
        updateVjProc.process(siri);

        // expected vj to not be changed
        MonitoredStopVisit firstSM = siri.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().getFirst();
        assertEquals("WRONG VJ FROM TR", firstSM.getMonitoredVehicleJourney().getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());

        String expectedItemIdentifier = STOP_ID + "_WRONG VJ FROM TR_" + now.format(DF_YYYYMMDD) + "_" + now.plusMinutes(10).format(DF_HHMMSS);
        assertEquals(expectedItemIdentifier, firstSM.getItemIdentifier());
    }


    private Siri generateSiriWithVJ(ZonedDateTime arrivalTime) {
        Siri siri = new Siri();
        ServiceDelivery sd = new ServiceDelivery();
        StopMonitoringDeliveryStructure smds = new StopMonitoringDeliveryStructure();
        MonitoredStopVisit monitoredStopVisit = new MonitoredStopVisit();
        monitoredStopVisit.setItemIdentifier("WRONG item identifier that must be replaced");
        MonitoringRefStructure monitoringRefStructure = new MonitoringRefStructure();
        monitoringRefStructure.setValue(STOP_ID);
        monitoredStopVisit.setMonitoringRef(monitoringRefStructure);
        MonitoredVehicleJourneyStructure monitoredVehicleJourney = new MonitoredVehicleJourneyStructure();
        LineRef lineRef = new LineRef();
        lineRef.setValue(LINE_ID);
        monitoredVehicleJourney.setLineRef(lineRef);
        NaturalLanguageStringStructure directionName = new NaturalLanguageStringStructure();
        directionName.setValue(DIRECTION_ID);
        monitoredVehicleJourney.getDirectionNames().add(directionName);
        DestinationRef destinationRef = new DestinationRef();
        destinationRef.setValue(DESTINATION_ID);
        monitoredVehicleJourney.setDestinationRef(destinationRef);
        FramedVehicleJourneyRefStructure framedVehicleJourneyRef = new FramedVehicleJourneyRefStructure();
        framedVehicleJourneyRef.setDatedVehicleJourneyRef("WRONG VJ FROM TR");
        monitoredVehicleJourney.setFramedVehicleJourneyRef(framedVehicleJourneyRef);
        MonitoredCallStructure monitoredCall = new MonitoredCallStructure();
        monitoredCall.setAimedArrivalTime(arrivalTime);
        monitoredVehicleJourney.setMonitoredCall(monitoredCall);
        monitoredVehicleJourney.setMonitored(true);
        monitoredStopVisit.setMonitoredVehicleJourney(monitoredVehicleJourney);
        smds.getMonitoredStopVisits().add(monitoredStopVisit);
        sd.getStopMonitoringDeliveries().add(smds);
        siri.setServiceDelivery(sd);

        return siri;
    }


}
