package no.rutebanken.anshar.subscription;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.idTests.TestUtils;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.health.IncomingDataHealthService;
import no.rutebanken.anshar.routes.siri.Siri20ToSiriRS20RequestResponse;
import no.rutebanken.anshar.routes.siri.Siri20ToSiriWS20RequestResponse;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.ServiceRequest;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.StopMonitoringRequestStructure;

import java.util.ArrayList;
import java.util.List;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@Slf4j
class RequestResponseSubscriptionTest extends SpringBootBaseTest implements CamelContextAware {

    @Produce("direct:execute_request_responsereq_id1")
    protected ProducerTemplate executeRequestProducer;
    @Produce("direct:execute_request_responsereq_id2")
    protected ProducerTemplate executeRequestProducer2;
    @Autowired
    AnsharConfiguration ansharConfiguration;
    @Autowired
    SubscriptionManager subscriptionManager;
    @Autowired
    IncomingDataHealthService incomingDataHealthService;
    @Autowired
    PrometheusMetricsService prometheusMetricsService;
    private ClientAndServer mockServer;
    private CamelContext camelContext;

    @AfterEach
    void stopServer() throws InterruptedException {
        if (mockServer != null) {
            mockServer.stop();
            Thread.sleep(2000);
            log.info("MockServer arrêté");
        }
    }

    @Test
    void test_RequestResponse_sending_request_siri_20_to_20_REST() throws Exception {

        mockServer = startClientAndServer(1080);
        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/providerEndpoint")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withBody("{\"message\":\"success\"}")
        );


        SubscriptionSetup subscriptionToCheck = new SubscriptionSetup();
        subscriptionToCheck.setSubscriptionMode(SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE);
        subscriptionToCheck.getUrlMap().put(RequestType.GET_STOP_MONITORING, "http://localhost:1080/providerEndpoint");
        subscriptionToCheck.setSubscriptionType(SiriDataType.STOP_MONITORING);
        subscriptionToCheck.setSubscriptionId("req_id1");
        subscriptionToCheck.setHeartbeatIntervalSeconds(60);
        subscriptionToCheck.setPreviewIntervalSeconds(600);
        subscriptionToCheck.setVersion("2.0");
        subscriptionToCheck.setServiceType(SubscriptionSetup.ServiceType.REST);
        List<String> stopMonitoringRefs = new ArrayList<>();
        stopMonitoringRefs.add("stop1");
        subscriptionToCheck.setStopMonitoringRefValue(stopMonitoringRefs);
        log.info("Sending request to route:" + "direct:" + subscriptionToCheck.getServiceRequestRouteName());

        Siri20ToSiriRS20RequestResponse siri20ToSiri20 = new Siri20ToSiriRS20RequestResponse(ansharConfiguration, subscriptionToCheck, subscriptionManager, incomingDataHealthService, prometheusMetricsService);
        camelContext.addRoutes(siri20ToSiri20);

        executeRequestProducer.asyncRequestBody(executeRequestProducer.getDefaultEndpoint(), "fakeBody");
        Thread.sleep(2000);

        // Récupérer et tracer les requêtes reçues
        int nbOfReceivedRequests = TestUtils.printReceivedRequestsOnEndpoint(mockServer, "/providerEndpoint");
        String result = TestUtils.getFirstRequestOnEndpoint(mockServer, "/providerEndpoint");
        Siri siri = CustomSiriXml.parseXml(result);

        // Message received by the mock MUST NOT contain a subscription request. Used mode is "REQUEST_RESPONSE". So, mock must only receive a StopMonitoringRequest
        Assertions.assertNull(siri.getSubscriptionRequest());

        // REQUEST_REPONSE => so, mock must receive a stopMonitoring Request
        Assertions.assertNotNull(siri.getServiceRequest());
        ServiceRequest subscriptionRequest = siri.getServiceRequest();
        Assertions.assertTrue(CollectionUtils.isNotEmpty(subscriptionRequest.getStopMonitoringRequests()));
        StopMonitoringRequestStructure firstSmRequest = subscriptionRequest.getStopMonitoringRequests().getFirst();
        Assertions.assertEquals("stop1", firstSmRequest.getMonitoringRef().getValue());
        log.info("received {} requests", nbOfReceivedRequests);
    }

    @Test
    void test_RequestResponse_sending_request_siri_20_to_20_SOAP() throws Exception {

        mockServer = startClientAndServer(1080);
        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/providerEndpoint")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withBody("{\"message\":\"success\"}")
        );


        SubscriptionSetup subscriptionToCheck = new SubscriptionSetup();
        subscriptionToCheck.setSubscriptionMode(SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE);
        subscriptionToCheck.getUrlMap().put(RequestType.GET_STOP_MONITORING, "http://localhost:1080/providerEndpoint");
        subscriptionToCheck.setSubscriptionType(SiriDataType.STOP_MONITORING);
        subscriptionToCheck.setSubscriptionId("req_id2");
        subscriptionToCheck.setHeartbeatIntervalSeconds(60);
        subscriptionToCheck.setPreviewIntervalSeconds(600);
        subscriptionToCheck.setVersion("2.0");
        subscriptionToCheck.setServiceType(SubscriptionSetup.ServiceType.SOAP);
        List<String> stopMonitoringRefs = new ArrayList<>();
        stopMonitoringRefs.add("stop1");
        subscriptionToCheck.setStopMonitoringRefValue(stopMonitoringRefs);
        log.info("Sending request to route:" + "direct:" + subscriptionToCheck.getServiceRequestRouteName());

        Siri20ToSiriWS20RequestResponse siri20ToSiri20 = new Siri20ToSiriWS20RequestResponse(ansharConfiguration, subscriptionToCheck, subscriptionManager, incomingDataHealthService, prometheusMetricsService);
        camelContext.addRoutes(siri20ToSiri20);

        executeRequestProducer2.asyncRequestBody(executeRequestProducer.getDefaultEndpoint(), "fakeBody");
        Thread.sleep(2000);

        // Récupérer et tracer les requêtes reçues
        int nbOfReceivedRequests = TestUtils.printReceivedRequestsOnEndpoint(mockServer, "/providerEndpoint");
        String result = TestUtils.getFirstRequestOnEndpoint(mockServer, "/providerEndpoint");
        result = CustomSiriXml.soapToRaw(result);

        Siri siri = CustomSiriXml.parseXml(result);

        // Message received by the mock MUST NOT contain a subscription request. Used mode is "REQUEST_RESPONSE". So, mock must only receive a StopMonitoringRequest
        Assertions.assertNull(siri.getSubscriptionRequest());

        // REQUEST_REPONSE => so, mock must receive a stopMonitoring Request
        Assertions.assertNotNull(siri.getServiceRequest());
        ServiceRequest subscriptionRequest = siri.getServiceRequest();
        Assertions.assertTrue(CollectionUtils.isNotEmpty(subscriptionRequest.getStopMonitoringRequests()));
        StopMonitoringRequestStructure firstSmRequest = subscriptionRequest.getStopMonitoringRequests().getFirst();
        Assertions.assertEquals("stop1", firstSmRequest.getMonitoringRef().getValue());
        log.info("received {} requests", nbOfReceivedRequests);
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }
}
