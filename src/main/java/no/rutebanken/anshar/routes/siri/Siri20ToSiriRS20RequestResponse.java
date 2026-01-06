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

package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.api.FlowStatus;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.health.IncomingDataHealthService;
import no.rutebanken.anshar.routes.health.IncomingFlowType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.MessageHistory;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static no.rutebanken.anshar.routes.HttpParameter.INTERNAL_SIRI_DATA_TYPE;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_SUBSCRIPTION_ID;

public class Siri20ToSiriRS20RequestResponse extends SiriSubscriptionRouteBuilder {

    @Autowired
    private IncomingDataHealthService incomingDataHealthService;

    @Autowired
    private PrometheusMetricsService metrics;

    public Siri20ToSiriRS20RequestResponse(AnsharConfiguration config, SubscriptionSetup subscriptionSetup, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
        this.subscriptionSetup = subscriptionSetup;
    }

    @Override
    public void configure() throws Exception {

        long heartbeatIntervalMillis = subscriptionSetup.getHeartbeatInterval().toMillis();

        String httpOptions = getTimeout();

        String monitoringRouteId = "monitor.rs.20." + subscriptionSetup.getSubscriptionType() + "." + subscriptionSetup.getVendor();
        boolean releaseLeadershipOnError;
        if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE |
                subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.POLLING_FETCHED_DELIVERY) {
            releaseLeadershipOnError = true;
            singletonFrom("quartz://anshar/monitor_" + subscriptionSetup.getRequestResponseRouteName() + "?trigger.repeatInterval=" + heartbeatIntervalMillis,
                    monitoringRouteId)
                    .choice()
                    .when(p -> requestData(subscriptionSetup.getSubscriptionId(), p.getFromRouteId()))
                    .to("direct:" + subscriptionSetup.getServiceRequestRouteName())
                    .endChoice()
            ;
        } else {
            releaseLeadershipOnError = false;
        }

        String routeId = subscriptionSetup.getBaseRouteId();
        from("direct:" + subscriptionSetup.getServiceRequestRouteName())
                .messageHistory()
                .process(p -> requestStarted())
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setBody(e -> subscriptionSetup)
                .to("direct:siri.20.to.siri.rs.20.request-response.preprocess")
                .log(LoggingLevel.DEBUG, "Retrieving data " + subscriptionSetup.toString())
                .to("log:request:" + getClass().getSimpleName() + "?showAll=true&multiline=true&level=DEBUG")
                .doTry()
                .to(getCamelRequestUrl(subscriptionSetup, httpOptions))
                .setHeader("CamelHttpPath", constant("/appContext" + subscriptionSetup.buildUrl(false)))
                .log(LoggingLevel.DEBUG, "Got response " + subscriptionSetup.toString())
                .to("log:response:" + getClass().getSimpleName() + "?showAll=true&multiline=true&level=DEBUG")
                .setHeader(PARAM_SUBSCRIPTION_ID, simple(subscriptionSetup.getSubscriptionId()))
                .setHeader(INTERNAL_SIRI_DATA_TYPE, simple(subscriptionSetup.getSubscriptionType().name()))
                .process(p -> {
                    String url = getRequestUrl(subscriptionSetup);
                    metrics.registerIncomingDataMonitoring("SIRI", subscriptionSetup.getDatasetId(), "200", url);
                    incomingDataHealthService.sendSubscriptionMonitoringData("SIRI", subscriptionSetup.getDatasetId(), "200", url);
                    incomingDataHealthService.recordStatus(subscriptionSetup.getSubscriptionId(), subscriptionSetup.getDatasetId(), url, IncomingFlowType.SIRI, FlowStatus.OK);
                })
                .to("direct:enqueue.message")
                .doCatch(Exception.class)
                .log("Caught exception -" + (releaseLeadershipOnError ? "" : " NOT") + " releasing leadership: " + subscriptionSetup.toString())
                .to("log:response:" + getClass().getSimpleName() + "?showCaughtException=true&showAll=true&multiline=true")
                .process(p -> {
                    int statusCode = 500;
                    final Throwable ex = p.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                    String url = getCamelRequestUrl(subscriptionSetup, httpOptions);
                    if (ex instanceof HttpOperationFailedException httpEx) {
                        statusCode = httpEx.getStatusCode();
                    }
                    metrics.registerIncomingDataMonitoring("SIRI", subscriptionSetup.getDatasetId(), String.valueOf(statusCode), url);
                    incomingDataHealthService.sendSubscriptionMonitoringData("SIRI", subscriptionSetup.getDatasetId(), String.valueOf(statusCode), url);
                    incomingDataHealthService.recordStatus(subscriptionSetup.getSubscriptionId(), subscriptionSetup.getDatasetId(), getRequestUrl(subscriptionSetup), IncomingFlowType.SIRI, FlowStatus.ERROR);
                    if (releaseLeadershipOnError) {
                        releaseLeadership(monitoringRouteId);
                    }
                })
                .doFinally()
                .process(p -> {
                    requestFinished();
                    List<MessageHistory> list = p.getProperty(Exchange.MESSAGE_HISTORY, List.class);
                    long elapsed = 0;
                    for (MessageHistory history : list) {
                        if (history.getRouteId().equals(routeId)) {
                            elapsed += history.getElapsed();
                        }
                    }
                    log.info("Processing data took {} ms.", elapsed);
                    if (elapsed > heartbeatIntervalMillis) {
                        log.info("Processing took longer than {} ms - releasing leadership", heartbeatIntervalMillis);
                        if (releaseLeadershipOnError) {
                            releaseLeadership(monitoringRouteId);
                        }
                    }
                })
                .endDoTry()
                .routeId(routeId)
        ;
    }

}
