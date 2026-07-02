package no.rutebanken.anshar.integration;

import io.restassured.http.ContentType;
import no.rutebanken.anshar.data.util.CustomSiriXml;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import uk.org.siri.siri21.Siri;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.io.StringWriter;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MalformedXmlErrorResponseTest extends BaseHttpTest {

    private static final String MALFORMED_XML = "<Siri xmlns=\"https://www.siri.org.uk/siri\"><ServiceRequest><broken";

    @BeforeEach
    @Override
    public void init() {
        super.init();
    }

    @Test
    void malformedXmlOnServiceEndpoint_returnsSiriBadRequestError() throws Exception {
        String responseBody = given()
                .when()
                    .contentType(ContentType.XML)
                    .body(MALFORMED_XML)
                    .post("anshar/anshar/services")
                .then()
                    .statusCode(400)
                    .contentType("text/xml")
                    .extract().body().asString();

        Siri response = CustomSiriXml.parseXml(responseBody);

        assertBadRequestServiceDelivery(response);
    }

    @Test
    void malformedXmlOnSoapServiceEndpoint_returnsSoapWrappedSiriBadRequestError() throws Exception {
        String soapResponseBody = given()
                .when()
                    .contentType(ContentType.XML)
                    .body(MALFORMED_XML)
                    .post("anshar/anshar/ws/services")
                .then()
                    .statusCode(400)
                    .contentType("text/xml")
                    .extract().body().asString();

        Siri response = CustomSiriXml.parseXml(extractSiriElement(soapResponseBody));

        assertBadRequestServiceDelivery(response);
    }

    private void assertBadRequestServiceDelivery(Siri response) {
        assertNotNull(response.getServiceDelivery(), "response should contain a ServiceDelivery");
        assertFalse(response.getServiceDelivery().isStatus(), "Status should be false");

        var errorCondition = response.getServiceDelivery().getErrorCondition();
        assertNotNull(errorCondition, "ErrorCondition should be present");
        assertNotNull(errorCondition.getOtherError(), "OtherError should be present");
        assertTrue(errorCondition.getOtherError().getErrorText().startsWith("[BAD_REQUEST]"),
                "OtherError text should start with the [BAD_REQUEST] code");
        assertEquals("Invalid XML", errorCondition.getDescription().getValue());
    }

    private String extractSiriElement(String soapXml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(soapXml)));

        Node siriElement = (Node) XPathFactory.newInstance().newXPath()
                .evaluate("//*[local-name()='Siri']", document, XPathConstants.NODE);
        assertNotNull(siriElement, "SOAP response should wrap a <Siri> element");

        StringWriter writer = new StringWriter();
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.transform(new DOMSource(siriElement), new StreamResult(writer));
        return writer.toString();
    }
}
