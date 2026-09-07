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
import io.prometheus.jmx.JmxCollector;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.api.FlowStatus;
import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.metrics.JmxMetricsConverter;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.Exchange;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;

import javax.ws.rs.core.MediaType;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.health.IncomingFlowType.SIRI;

@Service
@Configuration
@EnableScheduling
public class LivenessReadinessRoute extends RestRouteBuilder {
    public static final String NOTIFY_HUBOT_ROUTE = "direct:notify.hubot";
    public static final String NOTIFY_TARGET_HEADER = "notify-target";
    private static final Logger logger = LoggerFactory.getLogger(LivenessReadinessRoute.class);
    public static final String HUBOT_NOTIFY_TARGET = "hubot";
    private final JmxCollector jmxCollector;
    private final ISet<String> unhealthySubscriptionsAlreadyNotified;
    private final HealthManager healthManager;
    private final SubscriptionManager subscriptionManager;
    private final PrometheusMetricsService prometheusRegistry;
    private final SubscriptionConfig subscriptionConfig;
    private final IncomingDataHealthService incomingDataHealthService;
    private final String hubotSource;
    private final String hubotIconFail;
    private final String hubotMessageFail;
    private final String hubotIconSuccess;
    private final String hubotMessageSuccess;
    private final String hubotTemplate;
    private final boolean jmxMetricsScrapingEnabled;
    private final LocalTime startMonitorTime;
    private final LocalTime endMonitorTime;

    public LivenessReadinessRoute(@Value("${anshar.healthcheck.hubot.payload.source}") String hubotSource,
                                  @Value("${anshar.healthcheck.hubot.payload.icon.fail}") String hubotIconFail,
                                  @Value("${anshar.healthcheck.hubot.payload.message.fail}") String hubotMessageFail,
                                  @Value("${anshar.healthcheck.hubot.payload.icon.success}") String hubotIconSuccess,
                                  @Value("${anshar.healthcheck.hubot.payload.message.success}") String hubotMessageSuccess,
                                  @Value("${anshar.healthcheck.hubot.payload.template}") String hubotTemplate,
                                  @Value("${anshar.healthcheck.hubot.start.time}") LocalTime startMonitorTime,
                                  @Value("${anshar.healthcheck.hubot.end.time}") LocalTime endMonitorTime,
                                  @Value("${anshar.jmx.metrics.configuration.filepath:}") String pathToJmxMetricsConfiguration,
                                  @Value("${anshar.jmx.metrics.scraping.enabled:false}") boolean jmxMetricsScrapingEnabled,
                                  @Qualifier("getUnhealthySubscriptionsSet") ISet<String> unhealthySubscriptionsAlreadyNotified,
                                  HealthManager healthManager,
                                  SubscriptionManager subscriptionManager,
                                  PrometheusMetricsService prometheusRegistry,
                                  SubscriptionConfig subscriptionConfig,
                                  IncomingDataHealthService incomingDataHealthService) {
        this.hubotSource = hubotSource;
        this.hubotIconFail = hubotIconFail;
        this.hubotMessageFail = hubotMessageFail;
        this.hubotIconSuccess = hubotIconSuccess;
        this.hubotMessageSuccess = hubotMessageSuccess;
        this.hubotTemplate = hubotTemplate;
        this.startMonitorTime = startMonitorTime;
        this.endMonitorTime = endMonitorTime;
        this.jmxMetricsScrapingEnabled = jmxMetricsScrapingEnabled;
        this.unhealthySubscriptionsAlreadyNotified = unhealthySubscriptionsAlreadyNotified;
        this.healthManager = healthManager;
        this.subscriptionManager = subscriptionManager;
        this.prometheusRegistry = prometheusRegistry;
        this.subscriptionConfig = subscriptionConfig;
        JmxCollector tmpJmxCollector = null;
        if (jmxMetricsScrapingEnabled && StringUtils.isNotBlank(pathToJmxMetricsConfiguration)) {
            try {
                tmpJmxCollector = new JmxCollector(new File(pathToJmxMetricsConfiguration)).register();
            } catch (Exception e) {
                logger.error("Error creating jmx collector", e);
            }
        } else {
            logger.info("Jmx metrics scraping is disabled");
        }
        this.jmxCollector = tmpJmxCollector;
        this.incomingDataHealthService = incomingDataHealthService;
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        rest("")
                .apiDocs(false)
                .get("/scrape").to("direct:scrape")
                .get("/ready").to("direct:ready")
                .get("/up").to("direct:up")
                .get("/incomingdatahealth").to("direct:incoming.data.health")
                .get("/healthy").to("direct:healthy")
                .get("/anshardata").to("direct:anshardata")
                .get("/favicon.ico").to("direct:notfound")
        ;

        from("direct:incoming.data.daily.statuses")
                .process(p -> p.getIn().setBody(getDailyStatuses()))
                .marshal().json()
                .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.APPLICATION_JSON))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .routeId("incoming.data.daily.statuses")
        ;

        from("direct:incoming.data.health")
                .process(p -> p.getIn().setBody(getIncomingDataHealth()))
                .marshal().json()
                .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.APPLICATION_JSON))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .routeId("incoming.data.health")
        ;


        //To avoid large stacktraces in the log when fetching data using browser
        from("direct:notfound")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("404"))
                .routeId("health.notfound")
        ;

        // Application is ready to accept traffic
        from("direct:scrape")
                .process(p -> {
                    String metrics = prometheusRegistry.scrape();
                    if (isJmxMetricsScrapingActive()) {
                        MetricSnapshots jmxMetrics = this.jmxCollector.collect();
                        String parsedJmxMetrics = jmxMetrics.stream().map(JmxMetricsConverter::convertMetricSnapshotToPrometheusString).collect(Collectors.joining(""));
                        metrics = metrics + parsedJmxMetrics;
                    }
                    p.getIn().setBody(metrics);
                })
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .routeId("health.scrape")
        ;

        // readiness
        from("direct:ready")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .setBody(constant("OK"))
                .routeId("health.ready")
        ;

        // liveness
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
                .process(p -> p.getIn().setBody("Server has not received data for " + healthManager.getSecondsSinceDataReceived() + " seconds."))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("500"))
                .log("Server reports not receiving data")
                .endChoice()
                .otherwise()
                .setBody(simple("OK"))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .end()
                .routeId("health.healthy")
        ;

        from("direct:anshardata")
                .choice()
                .when(p -> getAllUnhealthySubscriptions().isEmpty() && !unhealthySubscriptionsAlreadyNotified.isEmpty())
                .process(p -> {
                    unhealthySubscriptionsAlreadyNotified.clear();
                    String message = hubotMessageSuccess;

                    if (LocalTime.now().isAfter(startMonitorTime) &&
                            LocalTime.now().isBefore(endMonitorTime)) {
                        String jsonPayload = "{" + MessageFormat.format(hubotTemplate, hubotSource, hubotIconSuccess, message) + "}";
                        p.getIn().setBody("{" + jsonPayload + "}");
                        p.getIn().setHeader(NOTIFY_TARGET_HEADER, HUBOT_NOTIFY_TARGET);
                    } else {
                        p.getIn().setBody(message);
                        p.getIn().setHeader(NOTIFY_TARGET_HEADER, "log");
                    }
                })
                .log("Server is back to normal")
                .to(NOTIFY_HUBOT_ROUTE)
                .endChoice()
                .when(p -> getAllUnhealthySubscriptions() != null && !getAllUnhealthySubscriptions().isEmpty())
                .process(p -> {
                    Set<String> unhealthySubscriptions = getAllUnhealthySubscriptions();

                    //Avoid notifying multiple times for same subscriptions
                    unhealthySubscriptions.removeAll(unhealthySubscriptionsAlreadyNotified);

                    //Keep
                    unhealthySubscriptionsAlreadyNotified.addAll(unhealthySubscriptions);

                    if (!unhealthySubscriptions.isEmpty()) {
                        String message = MessageFormat.format(hubotMessageFail, getAllUnhealthySubscriptions());

                        if (LocalTime.now().isAfter(startMonitorTime) &&
                                LocalTime.now().isBefore(endMonitorTime)) {

                            String jsonPayload = "{" + MessageFormat.format(hubotTemplate, hubotSource, hubotIconFail, message) + "}";
                            p.getIn().setBody(jsonPayload);
                            p.getIn().setHeader(NOTIFY_TARGET_HEADER, HUBOT_NOTIFY_TARGET);
                        } else {
                            p.getIn().setBody("Subscriptions not receiving data - NOT notifying hubot:" + message);
                            p.getIn().setHeader(NOTIFY_TARGET_HEADER, "log");
                        }
                    }
                })
                .log("Server is NOT receiving data")
                .to(NOTIFY_HUBOT_ROUTE)
                .endChoice()
                .otherwise()
                .setBody(simple("OK"))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .endChoice()
                .routeId("health.data.received")
        ;
        from(NOTIFY_HUBOT_ROUTE)
                .choice()
                .when(header(NOTIFY_TARGET_HEADER).isEqualTo("log"))
                .to("log:health:" + getClass().getSimpleName() + "?showAll=false&multiline=false")
                .endChoice()
                .when(header(NOTIFY_TARGET_HEADER).isEqualTo(HUBOT_NOTIFY_TARGET))
                .to("log:health:" + getClass().getSimpleName() + "?showAll=false&multiline=false")
                .endChoice()
                .routeId("health.notify.hubot")
        ;
    }

    private List<IncomingFlowDailyStatus> getDailyStatuses() {
        List<IncomingFlowDailyStatus> dailyStatuses = new ArrayList<>();
        for (Map.Entry<IncomingFlowParameters, DailyStatus> incomingFlowParametersDailyStatusEntry : incomingDataHealthService.getDailyStatuses().entrySet()) {
            IncomingFlowDailyStatus newDailyStatus = new IncomingFlowDailyStatus();
            newDailyStatus.setUrl(incomingFlowParametersDailyStatusEntry.getKey().getUrl());
            newDailyStatus.setId(incomingFlowParametersDailyStatusEntry.getKey().getId());
            newDailyStatus.setType(incomingFlowParametersDailyStatusEntry.getKey().getType());
            newDailyStatus.setDataset(incomingFlowParametersDailyStatusEntry.getKey().getDataset());
            newDailyStatus.setDailyStatus(incomingFlowParametersDailyStatusEntry.getValue());
            dailyStatuses.add(newDailyStatus);
        }

        return dailyStatuses;
    }

    private List<IncomingFlowStatus> getIncomingDataHealth() {
        List<IncomingFlowStatus> flowStatuses = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(subscriptionConfig.getSubscriptions())) {
            flowStatuses.addAll(getSiriStatus(subscriptionConfig.getSubscriptions()));
        }
        return flowStatuses;
    }

    private List<IncomingFlowStatus> getSiriStatus(List<SubscriptionSetup> subscriptions) {
        List<IncomingFlowStatus> results = new ArrayList<>();
        Map<String, Map<String, List<SubscriptionSetup>>> datasetIdToUrlToSubscriptions = new HashMap<>();
        for (SubscriptionSetup subscription : subscriptions) {
            if (!subscription.isActive() || !subscription.getSubscriptionMode().equals(SubscriptionSetup.SubscriptionMode.SUBSCRIBE)) {
                continue;
            }
            datasetIdToUrlToSubscriptions
                    .computeIfAbsent(subscription.getDatasetId(), datasetId -> new HashMap<>())
                    .computeIfAbsent(subscription.getUrlMap().get(RequestType.SUBSCRIBE), url -> new ArrayList<>())
                    .add(subscription);
        }
        datasetIdToUrlToSubscriptions
                .forEach(
                        (datasetId, urlToSubscriptions) ->
                                urlToSubscriptions
                                        .forEach((url, subscriptionsByUrl) -> results.add(getFlowStatusFromSubscription(url, subscriptionsByUrl))));
        return results;
    }

    public IncomingFlowStatus getFlowStatusFromSubscription(String url, List<SubscriptionSetup> subscriptionsByUrl) {
        IncomingFlowStatus siriStatus = new IncomingFlowStatus();

        siriStatus.setId(subscriptionsByUrl.stream().map(SubscriptionSetup::getSubscriptionId).collect(Collectors.joining(",")));
        siriStatus.setLastUpdate(System.currentTimeMillis());
        siriStatus.setUrl(url);
        siriStatus.setDataset(subscriptionsByUrl.getFirst().getDatasetId());
        siriStatus.setType(SIRI);

        try {
            FlowStatus status = launchCheckStatus(subscriptionsByUrl.getFirst());
            siriStatus.setStatus(status.name());
        } catch (Exception e) {
            incomingDataHealthService.sendSubscriptionMonitoringData(SIRI.getCode(), subscriptionsByUrl.getFirst().getDatasetId(), "500", subscriptionsByUrl.getFirst().getUrlMap().get(RequestType.SUBSCRIBE));
            siriStatus.setStatus(FlowStatus.ERROR.name());
            log.error("error checking flow status", e);
        }

        return siriStatus;
    }

    private FlowStatus launchCheckStatus(SubscriptionSetup subscription) throws IOException, InterruptedException, XMLStreamException, JAXBException, TransformerException {
        Siri checkStatusRequest = SiriObjectFactory.createCheckStatusRequest(subscription);
        String body = CustomSiriXml.toXml(checkStatusRequest);
        String transformedBody = body;
        if (subscription.getServiceType().equals(SubscriptionSetup.ServiceType.SOAP)) {
            transformedBody = CustomSiriXml.rawToSoap(body);
        }

        try (HttpClient client = HttpClient.newHttpClient()) {
            String producerUrl = subscription.getUrlMap().get(RequestType.CHECK_STATUS);
            if (StringUtils.isBlank(producerUrl)) {
                producerUrl = subscription.getUrlMap().get(RequestType.SUBSCRIBE);
            }
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(producerUrl))
                    .header("Content-Type", subscription.getContentType())
                    .POST(HttpRequest.BodyPublishers.ofString(transformedBody, StandardCharsets.UTF_8));

            if (MapUtils.isNotEmpty(subscription.getCustomHeaders())) {
                subscription.getCustomHeaders().forEach((key, value) -> requestBuilder.headers(key, value.toString()));
            }

            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && isStatusOk(response.body())) {
                incomingDataHealthService.sendSubscriptionMonitoringData(SIRI.getCode(), subscription.getDatasetId(), "200", producerUrl, subscription.getSubscriptionType());
                return FlowStatus.OK;
            } else {
                incomingDataHealthService.sendSubscriptionMonitoringData(SIRI.getCode(), subscription.getDatasetId(), String.valueOf(response.statusCode()), producerUrl, subscription.getSubscriptionType());
                log.debug("original body : {}", body);
                log.debug("transformed body : {}", transformedBody);
                if (log.isDebugEnabled()) {
                    log.debug("checkStatus error: {} - {}", response.statusCode(), response.body());
                }
            }
            return FlowStatus.ERROR;
        }
    }

    public ZonedDateTime getServerStartDate(SubscriptionSetup subscription) throws IOException, InterruptedException, XMLStreamException, JAXBException, TransformerException {
        Siri checkStatusRequest = SiriObjectFactory.createCheckStatusRequest(subscription);
        String body = CustomSiriXml.toXml(checkStatusRequest);
        if (subscription.getServiceType().equals(SubscriptionSetup.ServiceType.SOAP)) {
            body = CustomSiriXml.rawToSoap(body);
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(subscription.getUrlMap().get(RequestType.SUBSCRIBE)))
                    .header("Content-Type", subscription.getContentType())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

            if (MapUtils.isNotEmpty(subscription.getCustomHeaders()))
                subscription.getCustomHeaders().forEach((key, value) -> requestBuilder.headers(key, value.toString()));

            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return extractStartDate(response.body());
            }
            return null;
        }
    }

    private ZonedDateTime extractStartDate(String body) throws FileNotFoundException, TransformerException, XMLStreamException, JAXBException {
        if (body.contains("<soapenv:Body>")) {
            body = CustomSiriXml.soapToRaw(body);
        }
        InputStream inputStream = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        Siri siriResponse = SiriValueTransformer.parseXml(inputStream);
        return siriResponse.getCheckStatusResponse().getServiceStartedTime();


    }

    public boolean isStatusOk(String body) throws XMLStreamException, JAXBException, FileNotFoundException, TransformerException {
        try {
            if (body.contains("<soapenv:Body>") || body.contains("<soap:") || body.contains("http://schemas.xmlsoap.org/soap/envelope/")) {
                body = CustomSiriXml.soapToRaw(body);
            }
            InputStream inputStream = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));

            Siri siriResponse = SiriValueTransformer.parseXml(inputStream);
            return siriResponse.getCheckStatusResponse() != null && siriResponse.getCheckStatusResponse().isStatus();
        } catch (Exception e) {
            logger.error("Error while trying to process chekStatus Response. body: {}", body);
            throw e;
        }
    }


    private Set<String> getAllUnhealthySubscriptions() {
        return subscriptionManager.getUnresponsiveSubscriptions().stream()
                .map(SubscriptionSetup::getSubscriptionId)
                .collect(Collectors.toSet());
    }

    private boolean isJmxMetricsScrapingActive() {
        return jmxCollector != null && jmxMetricsScrapingEnabled;
    }

    @Scheduled(fixedRate = 60000)
    public void checkIncomingData() {
        List<IncomingFlowStatus> currentStatuses = getSiriStatus(subscriptionConfig.getSubscriptions());
        for (IncomingFlowStatus currentStatus : currentStatuses) {
            incomingDataHealthService.recordStatus(currentStatus);
        }
    }


}
