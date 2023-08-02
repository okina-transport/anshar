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

import io.restassured.http.ContentType;
import no.rutebanken.anshar.data.GeneralMessages;
import no.rutebanken.anshar.helpers.TestObjectFactory;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rutebanken.siri20.util.SiriXml;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.GeneralMessage;
import uk.org.siri.siri20.InfoChannelRefStructure;
import uk.org.siri.siri20.InfoMessageRefStructure;
import uk.org.siri.siri20.Siri;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class GMRequestResponseTest extends BaseHttpTest {

    @Autowired
    private
    GeneralMessages repo;

    private final String situationNumber = "TTT:GeneralMsg:1234";

    @BeforeEach
    public void addData() {
        super.init();
        repo.clearAll();
        repo.add(dataSource, createGeneralMessage("Perturbation"));
    }

    @Test
    public void testGMRequest() throws Exception {

        //Test SIRI Request
        Siri siriRequest = SiriObjectFactory.createServiceRequest(TestObjectFactory.getSubscriptionSetup(SiriDataType.GENERAL_MESSAGE));
        given()
                .when()
                .contentType(ContentType.XML)
                .body(SiriXml.toXml(siriRequest))
                .post("anshar/services")
                .then()
                .statusCode(200)
                .rootPath("Siri.ServiceDelivery.GeneralMessageDelivery.GeneralMessage")
                .body("InfoMessageIdentifier", equalTo(situationNumber))

        ;
    }

    @Test
    public void testLiteGMRequest() throws Exception {

        //Test SIRI Lite Request
        given()
                .when()
                .get("anshar/rest/gm")
                .then()
                .statusCode(200)
                .contentType(ContentType.XML)
                .rootPath("Siri.ServiceDelivery.GeneralMessageDelivery.GeneralMessage")
                .body("InfoMessageIdentifier", equalTo(situationNumber))
        ;
    }

    private GeneralMessage createGeneralMessage(String infoChannel) {

        GeneralMessage msg = new GeneralMessage();
        InfoMessageRefStructure identifier = new InfoMessageRefStructure();
        identifier.setValue(situationNumber);
        msg.setInfoMessageIdentifier(identifier);
        InfoChannelRefStructure RefStruct = new InfoChannelRefStructure();
        RefStruct.setValue(infoChannel);
        msg.setInfoChannelRef(RefStruct);

        return msg;
    }
}
