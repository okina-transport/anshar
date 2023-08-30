package no.rutebanken.anshar.integration;

import io.restassured.http.ContentType;
import no.rutebanken.anshar.data.FacilityMonitoring;
import no.rutebanken.anshar.helpers.TestObjectFactory;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rutebanken.siri20.util.SiriXml;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.FacilityConditionStructure;
import uk.org.siri.siri20.FacilityRef;
import uk.org.siri.siri20.FacilityStructure;
import uk.org.siri.siri20.Siri;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class FMRequestResponseTest extends BaseHttpTest {

    @Autowired
    private
    FacilityMonitoring repo;

    private final String situationNumber = "TTT:FacilityMonitoring:1234";

    @BeforeEach
    public void addData() {
        super.init();
        repo.clearAll();
        repo.add(dataSource, createFacilityMonitoring(situationNumber));
    }

    @Test
    public void testFMRequest() throws Exception {

        //Test SIRI Request
        Siri siriRequest = SiriObjectFactory.createServiceRequest(TestObjectFactory.getSubscriptionSetup(SiriDataType.FACILITY_MONITORING));
        given()
                .when()
                .contentType(ContentType.XML)
                .body(SiriXml.toXml(siriRequest))
                .post("anshar/services?datasetId=TTT")
                .then()
                .statusCode(200)
                .rootPath("Siri.ServiceDelivery.FacilityMonitoringDelivery.FacilityCondition.Facility")
                .body("FacilityCode", equalTo(situationNumber))

        ;
    }

    @Test
    public void testLiteFMRequest() throws Exception {

        //Test SIRI Lite Request
        given()
                .when()
                .get("anshar/rest/fm?datasetId=TTT")
                .then()
                .statusCode(200)
                .contentType(ContentType.XML)
                .rootPath("Siri.ServiceDelivery.FacilityMonitoringDelivery.FacilityCondition.Facility")
                .body("FacilityCode", equalTo(situationNumber))
        ;
    }

    private FacilityConditionStructure createFacilityMonitoring(String infoRef) {

        FacilityConditionStructure fcs = new FacilityConditionStructure();
        FacilityRef facilityRef = new FacilityRef();
        FacilityStructure facilityStructure = new FacilityStructure();

        facilityRef.setValue(infoRef);

        facilityStructure.setFacilityCode(infoRef);

        fcs.setFacilityRef(facilityRef);
        fcs.setFacility(facilityStructure);

        return fcs;
    }
}
