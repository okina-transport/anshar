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

package no.rutebanken.anshar.subscription;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.idTests.TestUtils;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;


import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.XPathBody.xpath;

@Slf4j
class SubscriptionManagerTest extends SpringBootBaseTest {

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private SubscriptionInitializer subscriptionInitializer;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Value("${anshar.healthcheck.interval.factor:12}")
    private int healthcheckIntervalFactor;

    @BeforeEach
    public void init() {
        subscriptionManager.getSubscriptions().clear();
        subscriptionConfig.getSubscriptions().clear();
    }

    private final String subscriptionResponse = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
               <soapenv:Header/>
               <soapenv:Body>
                  <SubscribeResponse xmlns="http://wsdl.siri.org.uk">
                     <SubscriptionAnswerInfo xmlns="">
                        <ResponseTimestamp xmlns="http://www.siri.org.uk/siri">2026-04-16T15:37:15.061130044+02:00</ResponseTimestamp>
                        <ResponderRef xmlns="http://www.siri.org.uk/siri">SM-GCA-LOC6-0-0</ResponderRef>
                        <RequestMessageRef xmlns="http://www.siri.org.uk/siri">977116a4-4423-45b5-85b1-d052ee3f7aae</RequestMessageRef>
                     </SubscriptionAnswerInfo>
                     <Answer xmlns="">
                        <siri:ResponseStatus xmlns:siri="http://www.siri.org.uk/siri">
                           <ResponseTimestamp xmlns="http://www.siri.org.uk/siri">2026-04-16T15:37:15.061131465+02:00</ResponseTimestamp>
                           <RequestMessageRef xmlns="http://www.siri.org.uk/siri">977116a4-4423-45b5-85b1-d052ee3f7aae</RequestMessageRef>
                           <SubscriptionRef xmlns="http://www.siri.org.uk/siri">SM-GCA-LOC6-0-0</SubscriptionRef>
                           <Status xmlns="http://www.siri.org.uk/siri">true</Status>
                        </siri:ResponseStatus> 
                     </Answer>
                     <AnswerExtension xmlns=""/>
                  </SubscribeResponse>
               </soapenv:Body>
            </soapenv:Envelope>
            """;


    private final String checkStatusResponse = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
               <soapenv:Body>
                  <CheckStatusResponse xmlns="http://wsdl.siri.org.uk">
                     <CheckStatusAnswerInfo xmlns="">
                        <ResponseTimestamp xmlns="http://www.siri.org.uk/siri">2026-04-16T16:43:23.014823312+02:00</ResponseTimestamp>
                        <ProducerRef xmlns="http://www.siri.org.uk/siri">MOBIITI</ProducerRef>
                        <ResponseMessageIdentifier xmlns="http://www.siri.org.uk/siri">13a2934f-72e0-4d3a-8025-ced145176d34</ResponseMessageIdentifier>
                     </CheckStatusAnswerInfo>
                     <Answer xmlns="">
                        <Status xmlns="http://www.siri.org.uk/siri">true</Status>
                        <ServiceStartedTime xmlns="http://www.siri.org.uk/siri">2026-04-16T10:16:45.122508394+02:00</ServiceStartedTime>
                     </Answer>
                     <AnswerExtension xmlns=""/>
                  </CheckStatusResponse>
               </soapenv:Body>
            </soapenv:Envelope>
            """;

    @AfterEach
    void stopServer() throws InterruptedException {
        if (mockServer != null) {
            mockServer.stop();
            Thread.sleep(2000);
            log.info("MockServer arrêté");
        }
    }

    private ClientAndServer mockServer;


    @Test
    void test_unresponsive_subscription() throws Exception {
        subscriptionManager.getSubscriptions().clear();

        mockServer = startClientAndServer(1080);
        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/providerEndpoint")
                        .withBody(xpath(
                                "count(//*[local-name()='StopMonitoringSubscriptionRequest']) > 0"
                        ))
        ).respond(
                response()
                        .withStatusCode(200)
                        .withBody(subscriptionResponse)
        );

        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/providerEndpoint"),
                Times.unlimited(), TimeToLive.unlimited(), -10
        ).respond(
                response()
                        .withStatusCode(200)
                        .withBody(checkStatusResponse)
        );

        SubscriptionSetup subscription = createSubscription(10800);
        subscriptionConfig.getSubscriptions().add(subscription);
        subscriptionInitializer.createSubscriptions();

        Thread.sleep(91000);

        int nbOfReceivedRequests = TestUtils.countNonCheckStatusRequests(mockServer, "/providerEndpoint");
        String result = TestUtils.getFirstNonCheckStatusRequestOnEndpoint(mockServer, "/providerEndpoint");
        Assertions.assertEquals(1, nbOfReceivedRequests);
        Assertions.assertTrue(result.contains("StopMonitoringSubscriptionRequest") && result.contains("STOP1"));

        SubscriptionSetup existingSubscription = subscriptionManager.get(subscription.getSubscriptionId());

        // simulating a very old activity
        Instant instant = Instant.now().minus(30, ChronoUnit.MINUTES);
        subscriptionManager.setLastActivity(subscription.getSubscriptionId(), instant);

        // check should launch a restart of the subscription because last activity is too old
        subscriptionManager.launchSubscriptionsLifeCycleCheck();

        // provider should receive 3 requests :  1-original subscribe, 2-terminateSubscription, 3-new subscribe
        nbOfReceivedRequests = TestUtils.countNonCheckStatusRequests(mockServer, "/providerEndpoint");
        List<String> receivedRequests = TestUtils.getNonCheckStatusRequests(mockServer, "/providerEndpoint");
        Assertions.assertEquals(3, nbOfReceivedRequests);
        Assertions.assertTrue(isSubscriptionRequest(receivedRequests.get(0)));
        Assertions.assertTrue(isTerminateSubscriptionRequest(receivedRequests.get(1)));
        Assertions.assertTrue(isSubscriptionRequest(receivedRequests.get(2)));


    }

    @Test
    void test_restart_time_passed() throws Exception {
        subscriptionManager.getSubscriptions().clear();

        mockServer = startClientAndServer(1080);
        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/providerEndpoint")
                        .withBody(xpath(
                                "count(//*[local-name()='StopMonitoringSubscriptionRequest']) > 0"
                        ))
        ).respond(
                response()
                        .withStatusCode(200)
                        .withBody(subscriptionResponse)
        );

        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/providerEndpoint"),
                Times.unlimited(), TimeToLive.unlimited(), -10
        ).respond(
                response()
                        .withStatusCode(200)
                        .withBody(checkStatusResponse)
        );

        SubscriptionSetup subscription = createSubscription(10800);
        subscriptionConfig.getSubscriptions().add(subscription);
        subscriptionInitializer.createSubscriptions();

        Thread.sleep(121000);

        int nbOfReceivedRequests = TestUtils.countNonCheckStatusRequests(mockServer, "/providerEndpoint");
        String result = TestUtils.getFirstNonCheckStatusRequestOnEndpoint(mockServer, "/providerEndpoint");
        Assertions.assertEquals(1, nbOfReceivedRequests);
        Assertions.assertTrue(result.contains("StopMonitoringSubscriptionRequest") && result.contains("STOP1"));

        SubscriptionSetup existingSubscription = subscriptionManager.get(subscription.getSubscriptionId());

        // simulating a subscription started 2 hours ago
        ZonedDateTime fakeStartedTime = ZonedDateTime.now().minusHours(2);
        existingSubscription.setStartedAt(fakeStartedTime);

        // setting the restart time to : now - 10 min
        LocalTime now = LocalTime.now().minusMinutes(10);
        String fakeRestartTime = String.format("%02d:%02d", now.getHour(), now.getMinute());
        existingSubscription.setRestartTime(fakeRestartTime);
        subscriptionManager.updateSubscription(existingSubscription);


        // check should launch a restart of the subscription because now > restart time and startedAt < restartTime
        subscriptionManager.launchSubscriptionsLifeCycleCheck();

        // provider should receive 3 requests :  1-original subscribe, 2-terminateSubscription, 3-new subscribe
        nbOfReceivedRequests = TestUtils.countNonCheckStatusRequests(mockServer, "/providerEndpoint");
        List<String> receivedRequests = TestUtils.getNonCheckStatusRequests(mockServer, "/providerEndpoint");
        Assertions.assertEquals(3, nbOfReceivedRequests);
        Assertions.assertTrue(isSubscriptionRequest(receivedRequests.get(0)));
        Assertions.assertTrue(isTerminateSubscriptionRequest(receivedRequests.get(1)));
        Assertions.assertTrue(isSubscriptionRequest(receivedRequests.get(2)));


    }

    @Test
    void test_unresponsive_check_disabled_for_sx_only() {
        subscriptionManager.getSubscriptions().clear();

        SubscriptionSetup smSubscription = createRunningSubscription(SiriDataType.STOP_MONITORING);
        SubscriptionSetup sxSubscription = createRunningSubscription(SiriDataType.SITUATION_EXCHANGE);

        subscriptionManager.addSubscription(smSubscription.getSubscriptionId(), smSubscription);
        subscriptionManager.addSubscription(sxSubscription.getSubscriptionId(), sxSubscription);

        Instant staleActivity = Instant.now().minus(30, ChronoUnit.MINUTES);
        subscriptionManager.setLastActivity(smSubscription.getSubscriptionId(), staleActivity);
        subscriptionManager.setLastActivity(sxSubscription.getSubscriptionId(), staleActivity);

        try {
            // check enabled (default) : both SM and SX are reported unresponsive
            ReflectionTestUtils.setField(subscriptionManager, "disableUnresponsiveCheckSx", false);
            List<String> unresponsiveIds = subscriptionManager.getUnresponsiveSubscriptions().stream()
                    .map(SubscriptionSetup::getSubscriptionId)
                    .toList();
            Assertions.assertTrue(unresponsiveIds.contains(smSubscription.getSubscriptionId()));
            Assertions.assertTrue(unresponsiveIds.contains(sxSubscription.getSubscriptionId()));

            // check disabled for SX : only SM is reported unresponsive, SX is excluded
            ReflectionTestUtils.setField(subscriptionManager, "disableUnresponsiveCheckSx", true);
            unresponsiveIds = subscriptionManager.getUnresponsiveSubscriptions().stream()
                    .map(SubscriptionSetup::getSubscriptionId)
                    .toList();
            Assertions.assertTrue(unresponsiveIds.contains(smSubscription.getSubscriptionId()));
            Assertions.assertFalse(unresponsiveIds.contains(sxSubscription.getSubscriptionId()));
        } finally {
            ReflectionTestUtils.setField(subscriptionManager, "disableUnresponsiveCheckSx", false);
        }
    }

    private SubscriptionSetup createRunningSubscription(SiriDataType type) {
        SubscriptionSetup subscription = createSubscription(type, 10800);
        subscription.setStatus(SubscriptionStatus.RUNNING);
        return subscription;
    }

    public static String getCurrentTimeMinus10() {
        LocalTime now = LocalTime.now().minusMinutes(10);
        return String.format("%02d:%02d", now.getHour(), now.getMinute());
    }

    private boolean isTerminateSubscriptionRequest(String requestToCheck) {
        log.info("Checking that request is a terminate subscription request:" + requestToCheck);
        return requestToCheck.contains("DeleteSubscription");
    }

    private boolean isSubscriptionRequest(String requestToCheck) {
        log.info("Checking that request is a subscription request:" + requestToCheck);
        return requestToCheck.contains("StopMonitoringSubscriptionRequest");
    }


    private SubscriptionSetup createSubscription(long initialDuration) {
        return createSubscription(initialDuration, Duration.ofMinutes(4));
    }

    private SubscriptionSetup createSubscription(long initialDuration, Duration heartbeatInterval) {
        return createSubscription(SiriDataType.STOP_MONITORING, initialDuration, heartbeatInterval);
    }

    private SubscriptionSetup createSubscription(SiriDataType type, long initialDuration) {
        return createSubscription(type, initialDuration, Duration.ofMinutes(4));
    }

    private SubscriptionSetup createSubscription(SiriDataType type, long initialDuration, Duration heartbeatInterval) {
        HashMap<RequestType, String> urlMap = new HashMap<>();
        urlMap.put(RequestType.SUBSCRIBE, "http://localhost:1080/providerEndpoint");
        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "http://localhost:1080/providerEndpoint");

        SubscriptionSetup sub = new SubscriptionSetup(
                type,
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                "http://localhost",
                heartbeatInterval,
                Duration.ofHours(1),
                "http://www.kolumbus.no/siri",
                urlMap,
                "2.0",
                "SwarcoMizar",
                "tst",
                SubscriptionSetup.ServiceType.SOAP,
                new ArrayList<>(),
                new HashMap<>(),
                new ArrayList<>(),
                UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofSeconds(initialDuration),
                true,
                ZonedDateTime.now()
        );

        sub.getStopMonitoringRefValues().add("STOP1");
        sub.setContentType("text/xml;charset=UTF-8");
        sub.setRestartTime("02:00");
        return sub;
    }
}
