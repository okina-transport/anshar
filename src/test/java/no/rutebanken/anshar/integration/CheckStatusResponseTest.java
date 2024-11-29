package no.rutebanken.anshar.integration;

import io.restassured.http.ContentType;
import no.rutebanken.anshar.helpers.TestObjectFactory;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.Siri;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

public class CheckStatusResponseTest extends BaseHttpTest {

    @BeforeEach
    public void init() {
        super.init();
    }

    @Test
    void checkStatusRequest_verifyXmlBodyNotEmpty_test() throws Exception {
        Siri checkStatusRequest = SiriObjectFactory.createCheckStatusRequest(TestObjectFactory.getSubscriptionSetup(SiriDataType.SITUATION_EXCHANGE));
        given()
            .when()
                .contentType(ContentType.XML)
                .body(SiriXml.toXml(checkStatusRequest))
                .post("anshar/anshar/ws/services")
            .then()
                .statusCode(200)
                .contentType("text/xml")
                .body("<soapenv:Envelope>",  notNullValue())
                .body("<soapenv:Body>",  notNullValue())
                .body("<CheckStatusResponse>",  notNullValue());
    }
}
