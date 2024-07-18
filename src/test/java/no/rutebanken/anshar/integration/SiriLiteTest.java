/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.integration;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import no.rutebanken.anshar.data.*;
import no.rutebanken.anshar.data.frGeneralMessageStructure.Content;
import no.rutebanken.anshar.helpers.TestObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.*;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;


public class SiriLiteTest extends BaseHttpTest {

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private MonitoredStopVisits monitoredStopVisits;

    @Autowired
    private VehicleActivities vehicleActivities;

    @Autowired
    private Situations situations;

    @Autowired
    private GeneralMessages generalMessages;

    @Autowired
    private FacilityMonitoring facilityMonitoring;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private DiscoveryCache discoveryCache;


    private String lineRef1 = "TEST:Line:1";
    private String lineRef2 = "TEST:Line:2";
    private String vehRef = "vehRef1";

    private String stopReference1 = "stop1";
    private String stopReference2 = "stop2";

    private String vehMonitoringRef = "vehMonRef1";


    @BeforeEach
    public void addData() {
        super.init();
        clear();
        feedCaches();
    }


    public void clear() {
        subscriptionManager.clearAllSubscriptions();
        estimatedTimetables.clearAll();
        monitoredStopVisits.clearAll();
        vehicleActivities.clearAll();
        situations.clearAll();
        generalMessages.clearAll();
        facilityMonitoring.clearAll();

    }


    public void feedCaches() {

        if (estimatedTimetables.getSize() == 0) {
            EstimatedVehicleJourney element = TestObjectFactory.createEstimatedVehicleJourney(lineRef1, vehRef, 0, 30, ZonedDateTime.now().plusMinutes(1), true);
            estimatedTimetables.add("test", element);

            EstimatedVehicleJourney element2 = TestObjectFactory.createEstimatedVehicleJourney(lineRef2, vehRef, 0, 30, ZonedDateTime.now().plusMinutes(1), true);
            estimatedTimetables.add("test2", element2);
        }

        if (monitoredStopVisits.getSize() == 0) {
            String itempIdentifier = UUID.randomUUID().toString();
            MonitoredStopVisit element = TestObjectFactory.createMonitoredStopVisit(ZonedDateTime.now().plusMinutes(1), stopReference1, itempIdentifier);
            monitoredStopVisits.add("test", element);

            String itempIdentifier2 = UUID.randomUUID().toString();
            MonitoredStopVisit element2 = TestObjectFactory.createMonitoredStopVisit(ZonedDateTime.now().plusMinutes(1), stopReference2, itempIdentifier2);
            monitoredStopVisits.add("test2", element2);
        }

        if (vehicleActivities.getSize() == 0) {


            VehicleActivityStructure va1 = TestObjectFactory.createVehicleActivityStructure(ZonedDateTime.now(), vehRef, lineRef1, vehMonitoringRef);
            vehicleActivities.add("test", va1);

            VehicleActivityStructure va2 = TestObjectFactory.createVehicleActivityStructure(ZonedDateTime.now(), vehRef, lineRef2, vehMonitoringRef);
            vehicleActivities.add("test2", va2);
        }

        if (situations.getSize() == 0) {
            PtSituationElement element = TestObjectFactory.createPtSituationElement("atb", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(4));
            situations.add("test", element);
        }

        if (generalMessages.getSize() == 0) {
            GeneralMessage msg = TestObjectFactory.createGeneralMessage();
            Content content1 = new Content();
            content1.setStopPointRefs(Arrays.asList("stop1"));
            msg.setContent(content1);

            //adding gm with 1 stopRef
            generalMessages.add("test", msg);
        }

        if (facilityMonitoring.getSize() == 0) {
            FacilityConditionStructure msg = TestObjectFactory.createFacilityMonitoring();
            facilityMonitoring.add("test", msg);
        }

        if (subscriptionManager.getAllSubscriptions(SiriDataType.STOP_MONITORING).size() == 0) {
            SubscriptionSetup subscriptionSetup = TestObjectFactory.getSubscriptionSetup(SiriDataType.STOP_MONITORING);
            subscriptionSetup.getStopMonitoringRefValues().add(stopReference1);
            subscriptionSetup.setDatasetId("DAT1");
            subscriptionManager.addSubscription("sub1", subscriptionSetup);

            SubscriptionSetup subscriptionSetup2 = TestObjectFactory.getSubscriptionSetup(SiriDataType.STOP_MONITORING);
            subscriptionSetup2.getStopMonitoringRefValues().add(stopReference2);
            subscriptionSetup2.setDatasetId("DAT1");
            subscriptionManager.addSubscription("sub2", subscriptionSetup2);
        }

    }


    ////////////////////////////////////////////////
    /////// CHECK GENERAL PARAMETERS
    ////////////////////////////////////////////////
    @Test
    public void testNoPointInServiceFormat() {
        given()
                .when()
                .get("/siri/2.0/stop-monitoringjson")
                .then()
                .statusCode(400)
                .body(containsString("Unsupported service and format"));
    }

    @Test
    public void testUnsupportedService() {
        given()
                .when()
                .get("/siri/2.0/uyagzduyhazgd.json")
                .then()
                .statusCode(400)
                .body(containsString("Unsupported service"));
    }

    @Test
    public void testUnsupportedFormat() {
        given()
                .when()
                .get("/siri/2.0/stop-monitoring.txt")
                .then()
                .statusCode(400)
                .body(containsString("Unsupported format"));
    }

    @Test
    public void testWrongVersion() {
        given()
                .when()
                .get("/siri/1.4/stop-monitoring.json")
                .then()
                .statusCode(400)
                .body(containsString("Unsupported version"));
    }


    ////////////////////////////////////////////////
    /////// STOP POINT DISCOVERY
    ////////////////////////////////////////////////

    @Test
    public void testStopDiscoveryJSON() {

        discoveryCache.addStop("DAT1", stopReference1);
        discoveryCache.addStop("DAT1", stopReference2);

        given()
                .header("useOriginalId", "true")
                .header("datasetId", "DAT1")
                .when()
                .get("/siri/2.0/stoppoints-discovery.json")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .rootPath("Siri.StopPointsDelivery")
                .body("AnnotatedStopPointRef", Matchers.hasSize(2))
                .body("AnnotatedStopPointRef[0].StopPointRef.value", Matchers.oneOf(stopReference1, stopReference2))
                .body("AnnotatedStopPointRef[1].StopPointRef.value", Matchers.oneOf(stopReference1, stopReference2));
    }


    ////////////////////////////////////////////////
    /////// LINES DISCOVERY
    ////////////////////////////////////////////////

    @Test
    public void testLinesDiscoveryJSON() {
        given()
                .when()
                .get("/siri/2.0/lines-discovery.json")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .rootPath("Siri.LinesDelivery")
                .body("AnnotatedLineRef", Matchers.hasSize(2))
                .body("AnnotatedLineRef[0].LineRef.value", Matchers.oneOf(lineRef1, lineRef2))
                .body("AnnotatedLineRef[1].LineRef.value", Matchers.oneOf(lineRef1, lineRef2));
    }

    ////////////////////////////////////////////////
    /////// VEHICLE MONITORING
    ////////////////////////////////////////////////

    @Test
    public void testVMJSON() {
        given()
                .when()
                .get("/siri/2.0/vehicle-monitoring.json")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .rootPath("Siri.ServiceDelivery.VehicleMonitoringDelivery[0].")
                .body("VehicleActivity", Matchers.nullValue());
    }

    @Test
    public void testVMJSONFilterOnLineRef() {
        given()
                .when()
                .get("/siri/2.0/vehicle-monitoring.json?LineRef=" + lineRef1)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .rootPath("Siri.ServiceDelivery.VehicleMonitoringDelivery[0].")
                .body("VehicleActivity", Matchers.hasSize(1))
                .body("VehicleActivity[0].MonitoredVehicleJourney.LineRef.value", equalTo(lineRef1));
    }

    @Test
    public void testVMJSONFilterOnDatasetId() {
        given()
                .when()
                .get("/siri/2.0/vehicle-monitoring.json?datasetId=test2")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .rootPath("Siri.ServiceDelivery.VehicleMonitoringDelivery[0].")
                .body("VehicleActivity", Matchers.hasSize(1))
                .body("VehicleActivity[0].MonitoredVehicleJourney.LineRef.value", equalTo(lineRef2));
    }


    ////////////////////////////////////////////////
    /////// SITUATION EXCHANGE
    ////////////////////////////////////////////////
    @Test
    public void testSXJSONFilterOnDatasetId() {
        given()
                .when()
                .get("/siri/2.0/situation-exchange.json?datasetId=test")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .rootPath("Siri.ServiceDelivery.SituationExchangeDelivery[0].Situations")
                .body("PtSituationElement", Matchers.hasSize(1))
                .body("PtSituationElement[0].ParticipantRef.value", equalTo("atb"))
                .body("PtSituationElement[0].SituationNumber.value", equalTo("1234"));
    }

    ////////////////////////////////////////////////
    /////// GENERAL MESSAGE
    ////////////////////////////////////////////////

    @Test
    public void testGMJSONFilterOnDatasetId() {
        given()
                .when()
                .get("/siri/2.0/general-message.json?datasetId=test")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .rootPath("Siri.ServiceDelivery.GeneralMessageDelivery[0]")
                .body("GeneralMessage", Matchers.hasSize(1))
                .body("GeneralMessage[0].Content.StopPointRef", Matchers.hasSize(1))
                .body("GeneralMessage[0].Content.StopPointRef[0]", equalTo("stop1"));
    }

    ////////////////////////////////////////////////
    /////// FACILITY MONITORING
    ////////////////////////////////////////////////

    @Test
    public void testFMJSONFilterOnDatasetId() {
        given()
                .when()
                .get("/siri/2.0/facility-monitoring.json?datasetId=test")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .rootPath("Siri.ServiceDelivery.FacilityMonitoringDelivery[0]")
                .body("FacilityCondition", Matchers.hasSize(1))
                .body("FacilityCondition[0].FacilityRef.value", equalTo("facility"));
    }


    ////////////////////////////////////////////////
    /////// ESTIMATED TIMETABLES
    ////////////////////////////////////////////////

    @Test
    public void testETJSONFilterOnDatasetId() {
        given()
                .when()
                .get("/siri/2.0/estimated-timetables.json?datasetId=test")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .rootPath("Siri.ServiceDelivery.EstimatedTimetableDelivery[0].EstimatedJourneyVersionFrame[0].EstimatedVehicleJourney[0]")
                .body("LineRef.value", equalTo(lineRef1))
                .body("VehicleRef.value", equalTo(vehRef));
    }

    @Test
    public void testETXMLFilterOnDatasetId() {
        given()
                .when()
                .get("/siri/2.0/estimated-timetables.xml?datasetId=test")
                .then()
                .statusCode(200)
                .contentType(ContentType.XML)
                .rootPath("Siri.ServiceDelivery.EstimatedTimetableDelivery.EstimatedJourneyVersionFrame.EstimatedVehicleJourney[0]")
                .body("LineRef", oneOf(lineRef1, lineRef2));
    }


    ////////////////////////////////////////////////
    /////// STOP MONITORING
    ////////////////////////////////////////////////

    @Test
    public void testStopMonitoring() {
        given()
                .when()
                .get("/siri/2.0/stop-monitoring.json")
                .then()
                .statusCode(200);
    }

    @Test
    public void testSMJSON() {
        given()
                .when()
                .get("/siri/2.0/stop-monitoring.json")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .rootPath("Siri.ServiceDelivery.StopMonitoringDelivery[0]")
                .body("MonitoredStopVisit", Matchers.nullValue());

    }

    @Test
    public void testSMJSONFilterOnMonitoringRef() {
        given()
                .when()
                .get("/siri/2.0/stop-monitoring.json?MonitoringRef=" + stopReference1)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .rootPath("Siri.ServiceDelivery.StopMonitoringDelivery[0]")
                .body("MonitoredStopVisit", Matchers.hasSize(1))
                .body("MonitoredStopVisit[0].MonitoringRef.value", Matchers.equalTo(stopReference1));

    }

    @Test
    public void testSMJSONFilterOnDatasetId() {
        given()
                .when()
                .get("/siri/2.0/stop-monitoring.json?datasetId=test2")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .rootPath("Siri.ServiceDelivery.StopMonitoringDelivery[0]")
                .body("MonitoredStopVisit", Matchers.hasSize(1))
                .body("MonitoredStopVisit[0].MonitoringRef.value", Matchers.equalTo(stopReference2));
    }


    @Test
    public void testSMXMLFilterOnDatasetId() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        RestAssured.rootPath = "";
        given()
                .when()
                .get("/siri/2.0/stop-monitoring.xml?datasetId=test")
                .then()
                .statusCode(200)
                .contentType(ContentType.XML)
                .rootPath("Siri.ServiceDelivery.StopMonitoringDelivery")
                .body("MonitoredStopVisit[0].MonitoringRef", Matchers.equalTo(stopReference1));
    }


}
