package no.rutebanken.anshar.integration;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceRequestVersionTest extends BaseHttpTest {

    @BeforeEach
    @Override
    public void init() {
        super.init();
    }

    @ParameterizedTest
    @ValueSource(strings = {"n_importe_quoi", "1.0", "1.4", "2.2"})
    void unsupportedVersion_returnsCapabilityNotSupportedError(String version) throws Exception {
        Document response = postServiceRequest(sxRequest(version, version));

        assertEquals("2.1", xpath(response, "/*[local-name()='Siri']/@version"));
        assertEquals("false", xpath(response, "//*[local-name()='ServiceDelivery']/*[local-name()='Status']"));
        assertEquals("false", xpath(response, "//*[local-name()='SituationExchangeDelivery']/*[local-name()='Status']"));
        assertEquals(version, xpath(response, "//*[local-name()='SituationExchangeDelivery']//*[local-name()='CapabilityNotSupportedError']/*[local-name()='CapabilityRef']"));
        assertEquals("Unsupported version: " + version, xpath(response, "//*[local-name()='CapabilityNotSupportedError']/*[local-name()='ErrorText']"));
        assertEquals("0", xpath(response, "count(//*[local-name()='Situations'])"));
    }

    @Test
    void unsupportedServiceVersion_withSupportedRootVersion_returnsCapabilityNotSupportedError() throws Exception {
        Document response = postServiceRequest(sxRequest("2.1", "toto"));

        assertEquals("toto", xpath(response, "//*[local-name()='CapabilityNotSupportedError']/*[local-name()='CapabilityRef']"));
    }

    @Test
    void version21() throws Exception {
        Document response = postServiceRequest(sxRequest("2.1", "2.1"));

        assertEquals("2.1", xpath(response, "/*[local-name()='Siri']/@version"));
        assertEquals("2.1", xpath(response, "//*[local-name()='SituationExchangeDelivery']/@version"));
    }

    @Test
    void version20() throws Exception {
        Document response = postServiceRequest(sxRequest("2.0", "2.0"));

        assertEquals("2.0", xpath(response, "/*[local-name()='Siri']/@version"));
        assertEquals("2.0", xpath(response, "//*[local-name()='SituationExchangeDelivery']/@version"));
    }

    @Test
    void profileVersion21_isAnsweredIn21() throws Exception {
        Document response = postServiceRequest(sxRequest(null, "2.1:FR-1.0"));

        assertEquals("2.1", xpath(response, "/*[local-name()='Siri']/@version"));
        assertEquals("2.1:FR-1.0", xpath(response, "//*[local-name()='SituationExchangeDelivery']/@version"));
    }

    @Test
    void profileVersion20_isAnsweredIn20() throws Exception {
        Document response = postServiceRequest(sxRequest(null, "2.0:FR-1.0-1"));

        assertEquals("2.0", xpath(response, "/*[local-name()='Siri']/@version"));
        assertEquals("2.0:FR-1.0-1", xpath(response, "//*[local-name()='SituationExchangeDelivery']/@version"));
    }

    @Test
    void rootVersionOnly_isUsedAsFallback() throws Exception {
        Document response = postServiceRequest(sxRequest("2.0", null));

        assertEquals("2.0", xpath(response, "/*[local-name()='Siri']/@version"));
        assertEquals("2.0", xpath(response, "//*[local-name()='SituationExchangeDelivery']/@version"));
    }

    @Test
    void noVersion_isAnsweredIn21() throws Exception {
        Document response = postServiceRequest(sxRequest(null, null));

        assertEquals("2.1", xpath(response, "/*[local-name()='Siri']/@version"));
        assertEquals("2.1", xpath(response, "//*[local-name()='SituationExchangeDelivery']/@version"));
    }

    @Test
    void soapUnsupportedVersion_returnsCapabilityNotSupportedError() throws Exception {
        Document response = postServiceRequest(soapSxRequest("toto"), "anshar/anshar/ws/services");

        assertEquals("false", xpath(response, "//*[local-name()='Answer']/*[local-name()='SituationExchangeDelivery']/*[local-name()='Status']"));
        assertEquals("toto", xpath(response, "//*[local-name()='Answer']//*[local-name()='CapabilityNotSupportedError']/*[local-name()='CapabilityRef']"));
    }

    @Test
    void soapProfileVersion21_isAnsweredIn21() throws Exception {
        Document response = postServiceRequest(soapSxRequest("2.1:FR-1.0"), "anshar/anshar/ws/services");

        assertEquals("2.1:FR-1.0", xpath(response, "//*[local-name()='SituationExchangeDelivery']/@version"));
    }

    private Document postServiceRequest(String body) throws Exception {
        return postServiceRequest(body, "anshar/anshar/services");
    }

    private Document postServiceRequest(String body, String path) throws Exception {
        String responseBody = given()
                .when()
                    .contentType(ContentType.XML)
                    .body(body)
                    .post(path)
                .then()
                    .statusCode(200)
                    .extract().body().asString();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(responseBody)));
    }

    private static String xpath(Document document, String expression) throws Exception {
        return XPathFactory.newInstance().newXPath().evaluate(expression, document);
    }

    private static String sxRequest(String rootVersion, String serviceVersion) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\"" + versionAttribute(rootVersion) + ">" +
                "<ServiceRequest>" +
                "<RequestTimestamp>2026-10-02T10:00:00+02:00</RequestTimestamp>" +
                "<RequestorRef>test</RequestorRef>" +
                "<SituationExchangeRequest" + versionAttribute(serviceVersion) + ">" +
                "<RequestTimestamp>2026-10-02T10:00:00+02:00</RequestTimestamp>" +
                "</SituationExchangeRequest>" +
                "</ServiceRequest>" +
                "</Siri>";
    }

    private static String soapSxRequest(String version) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:sw=\"http://wsdl.siri.org.uk\" xmlns:siri=\"http://www.siri.org.uk/siri\">" +
                "<soapenv:Body>" +
                "<sw:GetSituationExchange>" +
                "<ServiceRequestInfo>" +
                "<siri:RequestTimestamp>2026-10-02T10:00:00+02:00</siri:RequestTimestamp>" +
                "<siri:RequestorRef>test</siri:RequestorRef>" +
                "</ServiceRequestInfo>" +
                "<Request version=\"" + version + "\">" +
                "<siri:RequestTimestamp>2026-10-02T10:00:00+02:00</siri:RequestTimestamp>" +
                "</Request>" +
                "</sw:GetSituationExchange>" +
                "</soapenv:Body>" +
                "</soapenv:Envelope>";
    }

    private static String versionAttribute(String version) {
        return version == null ? "" : " version=\"" + version + "\"";
    }
}
