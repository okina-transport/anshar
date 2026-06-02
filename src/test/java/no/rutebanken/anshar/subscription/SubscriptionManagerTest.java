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
public class SubscriptionManagerTest extends SpringBootBaseTest {

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



}
