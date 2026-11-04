package no.rutebanken.anshar.util;

import no.rutebanken.anshar.subscription.SiriDataType;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.GeneralMessage;
import uk.org.siri.siri21.GeneralMessageDeliveryStructure;
import uk.org.siri.siri21.MonitoredStopVisit;
import uk.org.siri.siri21.MonitoredStopVisitCancellation;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;
import uk.org.siri.siri21.StopMonitoringDeliveryStructure;
import uk.org.siri.siri21.VehicleActivityStructure;
import uk.org.siri.siri21.VehicleMonitoringDeliveryStructure;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiriUtilsTest {

    private static Siri siriWith(ServiceDelivery delivery) {
        Siri siri = new Siri();
        siri.setServiceDelivery(delivery);
        return siri;
    }

    @Test
    void getSiriDataType_nullSiri_returnsEmpty() {
        assertTrue(SiriUtils.getSiriDataType(null).isEmpty());
    }

    @Test
    void getSiriDataType_noServiceDelivery_returnsEmpty() {
        assertTrue(SiriUtils.getSiriDataType(new Siri()).isEmpty());
    }

    @Test
    void getSiriDataType_emptyServiceDelivery_returnsEmpty() {
        assertTrue(SiriUtils.getSiriDataType(siriWith(new ServiceDelivery())).isEmpty());
    }

    @Test
    void getSiriDataType_deliveryWithoutContent_returnsEmpty() {
        ServiceDelivery delivery = new ServiceDelivery();
        delivery.getVehicleMonitoringDeliveries().add(new VehicleMonitoringDeliveryStructure());
        delivery.getGeneralMessageDeliveries().add(new GeneralMessageDeliveryStructure());
        assertTrue(SiriUtils.getSiriDataType(siriWith(delivery)).isEmpty());
    }

    @Test
    void getSiriDataType_situationExchange() {
        SituationExchangeDeliveryStructure sx = new SituationExchangeDeliveryStructure();
        SituationExchangeDeliveryStructure.Situations situations = new SituationExchangeDeliveryStructure.Situations();
        situations.getPtSituationElements().add(new PtSituationElement());
        sx.setSituations(situations);
        ServiceDelivery delivery = new ServiceDelivery();
        delivery.getSituationExchangeDeliveries().add(sx);

        assertEquals(Optional.of(SiriDataType.SITUATION_EXCHANGE), SiriUtils.getSiriDataType(siriWith(delivery)));
    }

    @Test
    void getSiriDataType_vehicleMonitoring() {
        VehicleMonitoringDeliveryStructure vm = new VehicleMonitoringDeliveryStructure();
        vm.getVehicleActivities().add(new VehicleActivityStructure());
        ServiceDelivery delivery = new ServiceDelivery();
        delivery.getVehicleMonitoringDeliveries().add(vm);

        assertEquals(Optional.of(SiriDataType.VEHICLE_MONITORING), SiriUtils.getSiriDataType(siriWith(delivery)));
    }

    @Test
    void getSiriDataType_estimatedTimetable() {
        EstimatedTimetableDeliveryStructure et = new EstimatedTimetableDeliveryStructure();
        et.getEstimatedJourneyVersionFrames().add(new EstimatedVersionFrameStructure());
        ServiceDelivery delivery = new ServiceDelivery();
        delivery.getEstimatedTimetableDeliveries().add(et);

        assertEquals(Optional.of(SiriDataType.ESTIMATED_TIMETABLE), SiriUtils.getSiriDataType(siriWith(delivery)));
    }

    @Test
    void getSiriDataType_stopMonitoring() {
        StopMonitoringDeliveryStructure sm = new StopMonitoringDeliveryStructure();
        sm.getMonitoredStopVisits().add(new MonitoredStopVisit());
        ServiceDelivery delivery = new ServiceDelivery();
        delivery.getStopMonitoringDeliveries().add(sm);

        assertEquals(Optional.of(SiriDataType.STOP_MONITORING), SiriUtils.getSiriDataType(siriWith(delivery)));
    }

    @Test
    void getSiriDataType_stopMonitoringWithOnlyCancellations() {
        StopMonitoringDeliveryStructure sm = new StopMonitoringDeliveryStructure();
        sm.getMonitoredStopVisitCancellations().add(new MonitoredStopVisitCancellation());
        ServiceDelivery delivery = new ServiceDelivery();
        delivery.getStopMonitoringDeliveries().add(sm);

        assertEquals(Optional.of(SiriDataType.STOP_MONITORING), SiriUtils.getSiriDataType(siriWith(delivery)));
    }

    @Test
    void hasDataOfType_stopMonitoringWithOnlyCancellations_staysFalse() {
        // InitialDeliveryGenerator relies on hasDataOfType ignoring cancellations
        StopMonitoringDeliveryStructure sm = new StopMonitoringDeliveryStructure();
        sm.getMonitoredStopVisitCancellations().add(new MonitoredStopVisitCancellation());
        ServiceDelivery delivery = new ServiceDelivery();
        delivery.getStopMonitoringDeliveries().add(sm);

        assertFalse(SiriUtils.hasDataOfType(siriWith(delivery), SiriDataType.STOP_MONITORING));
    }

    @Test
    void getSiriDataType_generalMessage() {
        GeneralMessageDeliveryStructure gm = new GeneralMessageDeliveryStructure();
        gm.getGeneralMessages().add(new GeneralMessage());
        ServiceDelivery delivery = new ServiceDelivery();
        delivery.getGeneralMessageDeliveries().add(gm);

        assertEquals(Optional.of(SiriDataType.GENERAL_MESSAGE), SiriUtils.getSiriDataType(siriWith(delivery)));
    }

    @Test
    void getSiriDataType_multipleTypes_returnsFirstInEnumOrder() {
        VehicleMonitoringDeliveryStructure vm = new VehicleMonitoringDeliveryStructure();
        vm.getVehicleActivities().add(new VehicleActivityStructure());
        GeneralMessageDeliveryStructure gm = new GeneralMessageDeliveryStructure();
        gm.getGeneralMessages().add(new GeneralMessage());
        ServiceDelivery delivery = new ServiceDelivery();
        delivery.getGeneralMessageDeliveries().add(gm);
        delivery.getVehicleMonitoringDeliveries().add(vm);

        // VEHICLE_MONITORING is declared before GENERAL_MESSAGE in SiriDataType
        assertEquals(Optional.of(SiriDataType.VEHICLE_MONITORING), SiriUtils.getSiriDataType(siriWith(delivery)));
    }
}
