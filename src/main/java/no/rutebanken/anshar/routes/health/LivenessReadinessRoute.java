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

package no.rutebanken.anshar.routes.health;

import com.hazelcast.collection.ISet;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalTime;
import java.util.Set;

@Service
@Configuration
public class LivenessReadinessRoute extends RestRouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${anshar.healthcheck.hubot.url}")
    private String hubotUrl;

    @Value("${anshar.healthcheck.hubot.payload.source}")
    private String hubotSource;

    @Value("${anshar.healthcheck.hubot.payload.icon.fail}")
    private String hubotIconFail;

    @Value("${anshar.healthcheck.hubot.payload.message.fail}")
    private String hubotMessageFail;

    @Value("${anshar.healthcheck.hubot.payload.icon.success}")
    private String hubotIconSuccess;

    @Value("${anshar.healthcheck.hubot.payload.message.success}")
    private String hubotMessageSuccess;

    @Value("${anshar.healthcheck.hubot.payload.template}")
    private String hubotTemplate;

    @Value("${anshar.healthcheck.hubot.allowed.inactivity.minutes:10}")
    private int allowedInactivityMinutes;

    @Value("${anshar.healthcheck.hubot.start.time}")
    private String startMonitorTimeStr;
    private LocalTime startMonitorTime;

    @Value("${anshar.healthcheck.hubot.end.time}")
    private String endMonitorTimeStr;
    private LocalTime endMonitorTime;

    @Autowired
    @Qualifier("getUnhealthySubscriptionsSet")
    private ISet<String> unhealthySubscriptionsAlreadyNotified;

    @Autowired
    private HealthManager healthManager;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private PrometheusMetricsService prometheusRegistry;

    public static boolean triggerRestart;

    @PostConstruct
    private void init() {
        startMonitorTime = LocalTime.parse(startMonitorTimeStr);
        endMonitorTime = LocalTime.parse(endMonitorTimeStr);
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        rest("").tag("health")
                .get("/scrape").to("direct:scrape")
                .get("/ready").to("direct:ready")
                .get("/up").to("direct:up")
                .get("/healthy").to("direct:healthy")
                .get("/anshardata").to("direct:anshardata")
                .get("/favicon.ico").to("direct:notfound")
        ;

        //To avoid large stacktraces in the log when fetching data using browser
        from("direct:notfound")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("404"))
                .routeId("health.notfound")
        ;

        // Application is ready to accept traffic
        from("direct:scrape")
                .process(p -> {
                    if (prometheusRegistry != null) {
                        p.getOut().setBody(prometheusRegistry.scrape());
                    }
                })
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .routeId("health.scrape")
        ;

        // Application is ready to accept traffic
        from("direct:ready")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .setBody(constant("OK"))
                .routeId("health.ready")
        ;

        // Application is (still) alive and well
        from("direct:up")
                .choice()
                .when(p -> !healthManager.isHazelcastAlive())
                    .log("Hazelcast is shut down")
                    .setBody(simple("Hazelcast is shut down"))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("500"))
                .endChoice()
                .otherwise()
                    .setBody(simple("OK"))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .end()
                .routeId("health.up")
        ;

        from("direct:healthy")
                .choice()
                .when(p -> !healthManager.isReceivingData())
                    .process(p -> {
                        p.getOut().setBody("Server has not received data for " + healthManager.getSecondsSinceDataReceived() + " seconds.");
                    })
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("500"))
                    .log("Server reports not receiving data")
                .endChoice()
                .otherwise()
                    .setBody(simple("OK"))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .end()
                .routeId("health.healthy")
        ;

    }

    private Set<String> getAllUnhealthySubscriptions() {
        return subscriptionManager.getAllUnhealthySubscriptions(allowedInactivityMinutes*60);
    }
}
