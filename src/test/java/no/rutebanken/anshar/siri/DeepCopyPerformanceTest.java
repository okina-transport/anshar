package no.rutebanken.anshar.siri;

import no.rutebanken.anshar.helpers.TestObjectFactory;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.*;

import java.io.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class DeepCopyPerformanceTest extends SpringBootBaseTest {

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    private static final Logger logger = LoggerFactory.getLogger(DeepCopyPerformanceTest.class);


    @Test
    public void testImmut() {

        Siri originalSiri = createHugeObject();
        Siri transformed = Stream.of(originalSiri).collect(Collectors.toCollection(() -> new ArrayList<>())).get(0);
        transformed.setServiceDelivery(null);
        logger.info("nb iterations:");
    }

    @Test
    public void compareKryoToSerial() {

        Siri originalSiri = createHugeObject();
        int nbIterations = 10000;

        logger.info("nb iterations:" + nbIterations);


        ///////////KRYO
        long kryoStart = System.currentTimeMillis();
        logger.info("kryo start time:" + kryoStart);

        for (int i = 0; i < nbIterations; i++) {
            Siri transformed = SiriObjectFactory.deepCopy(originalSiri);
        }


        long kryoEnd = System.currentTimeMillis();
        logger.info("kryo end time:" + kryoEnd);
        long duree = kryoEnd - kryoStart;
        logger.info("duree kryo (millis):" + duree);


        ///////////Serialisation/deserialisation
        long start = System.currentTimeMillis();
        logger.info("Start time:" + start);

        for (int i = 0; i < nbIterations; i++) {
            //Siri transformed = CopyOf.copyOf(originalSiri);

            Siri transformed = Stream.of(originalSiri).collect(Collectors.toCollection(() -> new ArrayList<>())).get(0);
        }

        long end = System.currentTimeMillis();
        logger.info("End time:" + end);
        long duree2 = end - start;
        logger.info("duree  (millis):" + duree2);


    }

    public static <T> T dupliquerObjet(T objetOriginal) {
        try (
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(bos)
        ) {
            out.writeObject(objetOriginal);

            try (ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
                 ObjectInputStream in = new ObjectInputStream(bis)) {
                return (T) in.readObject();
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Siri createHugeObject() {
        Siri result = new Siri();
        ServiceDelivery servDel = new ServiceDelivery();
        servDel.setResponseTimestamp(ZonedDateTime.now());

        //Add VM to siri
        VehicleActivityStructure vehActStruct = TestObjectFactory.createVehicleActivityStructure(ZonedDateTime.now(), "aa" + "1234", "");
        VehicleActivityStructure vehActStruct2 = TestObjectFactory.createVehicleActivityStructure(ZonedDateTime.now(), "aa2" + "1234", "2");
        VehicleActivityStructure vehActStruct3 = TestObjectFactory.createVehicleActivityStructure(ZonedDateTime.now(), "aa3" + "1234", "3");
        VehicleMonitoringDeliveryStructure vehMonStruct = new VehicleMonitoringDeliveryStructure();
        vehMonStruct.getVehicleActivities().add(vehActStruct);
        vehMonStruct.getVehicleActivities().add(vehActStruct2);
        vehMonStruct.getVehicleActivities().add(vehActStruct3);
        servDel.getVehicleMonitoringDeliveries().add(vehMonStruct);

        //Add SM to siri
        MonitoredStopVisit stopVisit1 = TestObjectFactory.createMonitoredStopVisit(ZonedDateTime.now(), "test");
        MonitoredStopVisit stopVisit2 = TestObjectFactory.createMonitoredStopVisit(ZonedDateTime.now(), "test2");
        MonitoredStopVisit stopVisit3 = TestObjectFactory.createMonitoredStopVisit(ZonedDateTime.now(), "test3");

        StopMonitoringDeliveryStructure stopDelStruct = new StopMonitoringDeliveryStructure();
        stopDelStruct.getMonitoredStopVisits().add(stopVisit1);
        stopDelStruct.getMonitoredStopVisits().add(stopVisit2);
        stopDelStruct.getMonitoredStopVisits().add(stopVisit3);

        servDel.getStopMonitoringDeliveries().add(stopDelStruct);

        //Add ET
        EstimatedVehicleJourney estJourney = TestObjectFactory.createEstimatedVehicleJourney("1234-update", "4321", 0, 30, ZonedDateTime.now().plusHours(1), true);
        EstimatedVehicleJourney estJourney2 = TestObjectFactory.createEstimatedVehicleJourney("1234-update2", "43212", 0, 30, ZonedDateTime.now().plusHours(1), true);
        EstimatedVehicleJourney estJourney3 = TestObjectFactory.createEstimatedVehicleJourney("1234-update3", "43213", 0, 30, ZonedDateTime.now().plusHours(1), true);
        EstimatedTimetableDeliveryStructure estDelStruct = new EstimatedTimetableDeliveryStructure();
        EstimatedVersionFrameStructure frameStruct = new EstimatedVersionFrameStructure();
        frameStruct.getEstimatedVehicleJourneies().add(estJourney);
        frameStruct.getEstimatedVehicleJourneies().add(estJourney2);
        frameStruct.getEstimatedVehicleJourneies().add(estJourney3);
        estDelStruct.getEstimatedJourneyVersionFrames().add(frameStruct);
        servDel.getEstimatedTimetableDeliveries().add(estDelStruct);


        //Add SX

        SituationExchangeDeliveryStructure sitExDelStruct = new SituationExchangeDeliveryStructure();
        PtSituationElement sit1 = TestObjectFactory.createPtSituationElement("atb", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusMinutes(1));
        PtSituationElement sit2 = TestObjectFactory.createPtSituationElement("atbazdazd", "1234zdazd", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusMinutes(1));
        PtSituationElement sit3 = TestObjectFactory.createPtSituationElement("atbergrg", "1234azdzd", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusMinutes(1));


        SituationExchangeDeliveryStructure.Situations sit = new SituationExchangeDeliveryStructure.Situations();
        sit.getPtSituationElements().add(sit1);
        sit.getPtSituationElements().add(sit2);
        sit.getPtSituationElements().add(sit3);

        sitExDelStruct.setSituations(sit);
        servDel.getSituationExchangeDeliveries().add(sitExDelStruct);

        result.setServiceDelivery(servDel);


        return result;
    }


}
