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
import no.rutebanken.anshar.api.GtfsRTApi;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;

import javax.annotation.PostConstruct;
import javax.ws.rs.core.MediaType;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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

@Service
@Configuration
@EnableScheduling
public class LivenessReadinessRoute extends RestRouteBuilder {
    private static final Logger logger = LoggerFactory.getLogger(LivenessReadinessRoute.class);
    public static boolean triggerRestart;
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
    @Value("${anshar.jmx.metrics.configuration.filepath}")
    private String pathToJmxMetricsConfiguration;
    @Value("${anshar.jmx.metrics.scraping.enabled}")
    private boolean jmxMetricsScrapingEnabled;
    @Autowired
    @Qualifier("getUnhealthySubscriptionsSet")
    private ISet<String> unhealthySubscriptionsAlreadyNotified;
    @Autowired
    private HealthManager healthManager;
    @Autowired
    private SubscriptionManager subscriptionManager;
    @Autowired
    private PrometheusMetricsService prometheusRegistry;
    @Autowired
    private SubscriptionConfig subscriptionConfig;
    private JmxCollector jmxCollector;

    @Autowired
    private IncomingDataHealthService incomingDataHealthService;

    @PostConstruct
    private void init() {
        startMonitorTime = LocalTime.parse(startMonitorTimeStr);
        endMonitorTime = LocalTime.parse(endMonitorTimeStr);
//        if (StringUtils.isNotBlank(pathToJmxMetricsConfiguration)) {
//            try {
//                jmxCollector = new JmxCollector(new File(pathToJmxMetricsConfiguration));
//                jmxCollector.register();
//            } catch (Exception e) {
//                logger.error("Error creating jmx collector", e);
//            }
//        }
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
                .process(p -> {
                    p.getIn().setBody(getDailyStatuses());
                })
                .marshal().json()
                .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.APPLICATION_JSON))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .routeId("incoming.data.daily.statuses")
        ;

        from("direct:incoming.data.health")
                .process(p -> {
                    p.getIn().setBody(getIncomingDataHealth());
                })
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
                    p.getOut().setBody(metrics);
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

        from("direct:anshardata")
                .choice()
                .when(p -> getAllUnhealthySubscriptions().isEmpty() && !unhealthySubscriptionsAlreadyNotified.isEmpty())
                .process(p -> {
                    unhealthySubscriptionsAlreadyNotified.clear();
                    String message = hubotMessageSuccess;

                    if (LocalTime.now().isAfter(startMonitorTime) &&
                            LocalTime.now().isBefore(endMonitorTime)) {
                        String jsonPayload = "{" + MessageFormat.format(hubotTemplate, hubotSource, hubotIconSuccess, message) + "}";
                        p.getOut().setBody("{" + jsonPayload + "}");
                        p.getOut().setHeader("notify-target", "hubot");
                    } else {
                        p.getOut().setBody(message);
                        p.getOut().setHeader("notify-target", "log");
                    }
                })
                .log("Server is back to normal")
                .to("direct:notify.hubot")
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
                            p.getOut().setBody(jsonPayload);
                            p.getOut().setHeader("notify-target", "hubot");
                        } else {
                            p.getOut().setBody("Subscriptions not receiving data - NOT notifying hubot:" + message);
                            p.getOut().setHeader("notify-target", "log");
                        }
                    }
                })
                .log("Server is NOT receiving data")
                .to("direct:notify.hubot")
                .endChoice()
                .otherwise()
                .setBody(simple("OK"))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .endChoice()
                .routeId("health.data.received")
        ;
        from("direct:notify.hubot")
                .choice()
                .when(header("notify-target").isEqualTo("log"))
                .to("log:health:" + getClass().getSimpleName() + "?showAll=false&multiline=false")
                .endChoice()
                .when(header("notify-target").isEqualTo("hubot"))
                .to("log:health:" + getClass().getSimpleName() + "?showAll=false&multiline=false")
//                    .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.JSON_UTF_8))
//                    .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
//                    .to(hubotUrl)
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
        if (CollectionUtils.isNotEmpty(subscriptionConfig.getGtfsRTApis())) {
            flowStatuses.addAll(getGtfsRTStatus(subscriptionConfig.getGtfsRTApis()));
        }
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
        siriStatus.setDataset(subscriptionsByUrl.get(0).getDatasetId());
        siriStatus.setType(IncomingFlowType.SIRI);

        try {
            FlowStatus status = launchCheckStatus(subscriptionsByUrl.get(0));
            siriStatus.setStatus(status.name());
        } catch (Exception e) {
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

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(subscription.getUrlMap().get(RequestType.SUBSCRIBE)))
                .header("Content-Type", subscription.getContentType())
                .POST(HttpRequest.BodyPublishers.ofString(transformedBody, StandardCharsets.UTF_8));

        if (MapUtils.isNotEmpty(subscription.getCustomHeaders()))
            subscription.getCustomHeaders().forEach((key, value) -> requestBuilder.headers(key, value.toString()));

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200 && isStatusOk(response.body())) {
            return FlowStatus.OK;
        } else {
            log.debug("original body : " + body);
            log.debug("transformed body : " + transformedBody);
            log.debug("checkStatus error:" + response.statusCode() + "-" + response.body());
        }
        return FlowStatus.ERROR;
    }

    public ZonedDateTime getServerStartDate(SubscriptionSetup subscription) throws IOException, InterruptedException, XMLStreamException, JAXBException, TransformerException {
        Siri checkStatusRequest = SiriObjectFactory.createCheckStatusRequest(subscription);
        String body = CustomSiriXml.toXml(checkStatusRequest);
        if (subscription.getServiceType().equals(SubscriptionSetup.ServiceType.SOAP)) {
            body = CustomSiriXml.rawToSoap(body);
        }

        HttpClient client = HttpClient.newHttpClient();

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
            logger.error("Error while trying to process chekStatus Response. body:" + body);
            throw e;
        }
    }


    private List<IncomingFlowStatus> getGtfsRTStatus(List<GtfsRTApi> gtfsRtApis) {
        List<IncomingFlowStatus> result = new ArrayList<>();
        for (GtfsRTApi gtfsRtApi : gtfsRtApis) {
            IncomingFlowStatus incomingFlowStatus = new IncomingFlowStatus();
            // id might be null if config comes from YML
            String id = gtfsRtApi.getId() != null ? gtfsRtApi.getId().toString() : String.format("%s-%s-%s", gtfsRtApi.getDatasetId(), gtfsRtApi.getType(), gtfsRtApi.getRouteIdList());
            incomingFlowStatus.setId(id);
            incomingFlowStatus.setStatus(gtfsRtApi.getStatus() != null ? gtfsRtApi.getStatus().name() : "UNKNOWN");
            incomingFlowStatus.setLastUpdate(gtfsRtApi.getLastUpdate());
            incomingFlowStatus.setDataset(gtfsRtApi.getDatasetId());
            incomingFlowStatus.setUrl(gtfsRtApi.getUrl());
            incomingFlowStatus.setType(IncomingFlowType.GTFS);
            result.add(incomingFlowStatus);
        }

        return result;
    }

    private Set<String> getAllUnhealthySubscriptions() {
        return subscriptionManager.getAllUnhealthySubscriptions(allowedInactivityMinutes * 60);
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
