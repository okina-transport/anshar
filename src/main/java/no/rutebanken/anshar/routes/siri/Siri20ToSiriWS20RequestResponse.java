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
import no.rutebanken.anshar.routes.siri.helpers.SiriRequestFactory;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.MessageHistory;
import org.apache.camel.http.base.HttpOperationFailedException;

import java.util.List;

import static no.rutebanken.anshar.routes.HttpParameter.INTERNAL_SIRI_DATA_TYPE;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_SUBSCRIPTION_ID;
import static no.rutebanken.anshar.routes.health.IncomingFlowType.SIRI;
import static no.rutebanken.anshar.routes.siri.Siri20RequestHandlerRoute.TRANSFORM_SOAP;

public class Siri20ToSiriWS20RequestResponse extends SiriSubscriptionRouteBuilder {


    private final IncomingDataHealthService incomingDataHealthService;
    private final PrometheusMetricsService metrics;

    public Siri20ToSiriWS20RequestResponse(AnsharConfiguration config, SubscriptionSetup subscriptionSetup, SubscriptionManager subscriptionManager,
                                           IncomingDataHealthService incomingDataHealthService, PrometheusMetricsService metrics) {
        super(config, subscriptionManager);
        this.incomingDataHealthService = incomingDataHealthService;
        this.metrics = metrics;
        this.subscriptionSetup = subscriptionSetup;
    }

    @Override
    public void configure() throws Exception {

        long heartbeatIntervalMillis = subscriptionSetup.getHeartbeatInterval().toMillis();


        SiriRequestFactory helper = new SiriRequestFactory(subscriptionSetup);

        String httpOptions = getTimeout();

        boolean releaseLeadershipOnError;
        if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE ||
                subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.POLLING_FETCHED_DELIVERY) {

            releaseLeadershipOnError = true;
            singletonFrom("quartz://anshar/monitor_" + subscriptionSetup.getRequestResponseRouteName() + "?trigger.repeatInterval=" + heartbeatIntervalMillis,
                    getMonitorRouteId(subscriptionSetup))
                    .choice()
                    .when(p -> requestData(subscriptionSetup.getSubscriptionId(), p.getFromRouteId()))
                    .to("direct:" + subscriptionSetup.getServiceRequestRouteName())
                    .endChoice()
            ;
        } else {
            releaseLeadershipOnError = false;
        }

        from("direct:" + subscriptionSetup.getServiceRequestRouteName())
                .messageHistory()
                .process(p -> requestStarted())
                .log("Retrieving data " + subscriptionSetup.toString())
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setBody(e -> subscriptionSetup)
                .to("direct:siri.20.to.siri.ws.20.request-response.preprocess")
                //   .to("log:request:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .doTry()
                .to(getCamelRequestUrl(subscriptionSetup, httpOptions))
                //  .to("log:response:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .setHeader("CamelHttpPath", constant("/appContext" + subscriptionSetup.buildUrl(false)))
                //    .log("Got response " + subscriptionSetup.toString())
                .setHeader(TRANSFORM_SOAP, constant(TRANSFORM_SOAP))
                .setHeader(PARAM_SUBSCRIPTION_ID, simple(subscriptionSetup.getSubscriptionId()))
                .setHeader(INTERNAL_SIRI_DATA_TYPE, simple(subscriptionSetup.getSubscriptionType().name()))
                .process(p -> {
                    String url = getCamelRequestUrl(subscriptionSetup, httpOptions);
                    metrics.registerIncomingDataMonitoring(SIRI.getCode(), subscriptionSetup.getDatasetId(), "200", url);
                    incomingDataHealthService.sendSubscriptionMonitoringData(SIRI.getCode(), subscriptionSetup.getDatasetId(), "200", url, subscriptionSetup.getSubscriptionType());
                    incomingDataHealthService.recordStatus(subscriptionSetup.getSubscriptionId(), subscriptionSetup.getDatasetId(), getRequestUrl(subscriptionSetup), IncomingFlowType.SIRI, FlowStatus.OK);
                })
                .to("direct:process.message.synchronous")
                .doCatch(Exception.class)
                .log("Caught exception - releasing leadership: " + subscriptionSetup.toString())
                .to("log:response:" + getClass().getSimpleName() + "?showCaughtException=true&showAll=true&multiline=true")
                .process(p -> {
                    int statusCode = 500;
                    final Throwable ex = p.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                    String url = getCamelRequestUrl(subscriptionSetup, httpOptions);
                    if (ex instanceof HttpOperationFailedException httpEx) {
                        statusCode = httpEx.getStatusCode();
                    }
                    metrics.registerIncomingDataMonitoring(SIRI.getCode(), subscriptionSetup.getDatasetId(), String.valueOf(statusCode), url);
                    incomingDataHealthService.sendSubscriptionMonitoringData(SIRI.getCode(), subscriptionSetup.getDatasetId(), String.valueOf(statusCode), url, subscriptionSetup.getSubscriptionType());
                    incomingDataHealthService.recordStatus(subscriptionSetup.getSubscriptionId(), subscriptionSetup.getDatasetId(), getRequestUrl(subscriptionSetup), IncomingFlowType.SIRI, FlowStatus.ERROR);
                    if (releaseLeadershipOnError) {
                        releaseLeadership(getMonitorRouteId(subscriptionSetup));
                    }
                })
                .doFinally()
                .process(p -> {
                    requestFinished();
                    List<MessageHistory> list = p.getProperty(Exchange.MESSAGE_HISTORY, List.class);
                    long elapsed = 0;
                    for (MessageHistory history : list) {
                        if (history.getRouteId().equals(getServiceRequestRouteId(subscriptionSetup))) {
                            elapsed += history.getElapsed();
                        }
                    }
                    if (elapsed > heartbeatIntervalMillis) {
                        log.info("Processing took longer than {} ms - releasing leadership", heartbeatIntervalMillis);
                        if (releaseLeadershipOnError) {
                            releaseLeadership(getMonitorRouteId(subscriptionSetup));
                        }
                    }
                })
                .endDoTry()
                .routeId(getServiceRequestRouteId(subscriptionSetup))
        ;

    }
}
