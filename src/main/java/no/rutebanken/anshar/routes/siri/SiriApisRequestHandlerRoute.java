package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.api.FlowStatus;
import no.rutebanken.anshar.api.SiriApi;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.DiscoveryCache;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.routes.health.IncomingDataHealthService;
import no.rutebanken.anshar.routes.health.IncomingFlowType;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import no.rutebanken.anshar.util.ZipFileUtils;
import org.apache.commons.io.FileUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import static no.rutebanken.anshar.subscription.SubscriptionSetup.ServiceType.SOAP;

@Component
public class SiriApisRequestHandlerRoute extends BaseRouteBuilder {

    public static final String SIRI_SM = "siri-sm";
    public static final String SIRI_SX = "siri-sx";
    public static final String SIRI_ET = "siri-et";
    public static final String SIRI_VM = "siri-vm";
    public static final String SIRI_GM = "siri-gm";
    public static final String SIRI_FM = "siri-fm";

    @Value("${cron.siri:0+0+0+1+1+?+2099}")
    private String cronSchedule;

    private final SiriHandler handler;

    private final DiscoveryCache discoveryCache;

    private final SubscriptionConfig subscriptionConfig;

    private final AnsharConfiguration configuration;

    private final PrometheusMetricsService metrics;

    private final IncomingDataHealthService incomingDataHealthService;

    public SiriApisRequestHandlerRoute(SubscriptionManager subscriptionManager, SiriHandler handler, DiscoveryCache discoveryCache, SubscriptionConfig subscriptionConfig, AnsharConfiguration configuration, PrometheusMetricsService metrics, IncomingDataHealthService incomingDataHealthService) {
        super(configuration, subscriptionManager);
        this.handler = handler;
        this.discoveryCache = discoveryCache;
        this.subscriptionConfig = subscriptionConfig;
        this.configuration = configuration;
        this.metrics = metrics;
        this.incomingDataHealthService = incomingDataHealthService;
    }



    @Override
    public void configure() throws Exception {

        if (configuration.processAdmin()) {
            log.info("Instance proxy. Pas de récupération SIRI par API");
            return;
        }

        singletonFrom("quartz://anshar/SiriApiQuartz?cron=" + cronSchedule + "&trigger.timeZone=Europe/Paris", "monitor.siri.api")
                .log("Starting Siri from API")
                .process(p -> createSubscriptionsFromApis());
    }


    private void createSubscriptionsFromApis() throws IOException, SAXException, ParserConfigurationException, XMLStreamException {
        List<SiriApi> siriApis = subscriptionConfig.getSiriApis();

        long startTime = DateTime.now().toInstant().getMillis();

        for (SiriApi siriApi : siriApis) {

            try {
                if (!shouldSiriApiBeRecovered(siriApi.getType())) {
                    continue;
                }

                File file = new File("/tmp/" + siriApi.getDatasetId() + "_" + siriApi.getType() + ".zip");
                log.info("Get Siri file for siriApis : " + siriApi.getDatasetId() + " in data format : " + siriApi.getType());
                String url = siriApi.getUrl();
                log.info("URL : " + url);
                FileUtils.copyURLToFile(new URL(url), file);
                if (file.length() > 0) {
                    createSubscriptionsFromFile(siriApi.getType(), file, url, siriApi.getDatasetId());
                } else {
                    log.error("No file returned for the provider " + siriApi.getDatasetId());
                }

                metrics.registerIncomingDataMonitoring("SIRI", siriApi.getDatasetId(), "200", siriApi.getUrl());
                incomingDataHealthService.sendSubscriptionMonitoringData("SIRI", siriApi.getDatasetId(), "200", siriApi.getUrl());
                incomingDataHealthService.recordStatus(String.valueOf(siriApi.getId()), siriApi.getDatasetId(), siriApi.getUrl(), IncomingFlowType.SIRI, FlowStatus.OK);
            } catch (Exception e) {
                metrics.registerIncomingDataMonitoring("SIRI", siriApi.getDatasetId(), "500", siriApi.getUrl());
                incomingDataHealthService.sendSubscriptionMonitoringData("SIRI", siriApi.getDatasetId(), "500", siriApi.getUrl());
                incomingDataHealthService.recordStatus(String.valueOf(siriApi.getId()), siriApi.getDatasetId(), siriApi.getUrl(), IncomingFlowType.SIRI, FlowStatus.ERROR);

            }
        }

        long endTime = DateTime.now().toInstant().getMillis();
        long processTime = (endTime - startTime) / 1000;
        log.info("Siri API completed in {} seconds", processTime);

    }


    /**
     * Checks if the current anshar instance is allowed to get siri API data
     * e.g : if the current instance is running with DATA_SM app_mode, it is only allowed to recover siri-sm data
     *
     * @return true : the current instance of anshar is allowed to get data from this siri api
     * false : the current instace of anshar must not recover data from this siri api
     */
    private boolean shouldSiriApiBeRecovered(String subscriptionType) {

        if (SIRI_SM.equals(subscriptionType) && configuration.processSM()) {
            return true;
        }

        if (SIRI_SX.equals(subscriptionType) && configuration.processSX()) {
            return true;
        }

        if (SIRI_ET.equals(subscriptionType) && configuration.processET()) {
            return true;
        }

        return SIRI_VM.equals(subscriptionType) && configuration.processVM();
    }


    public void createSubscriptionsFromFile(String dataFormat, File file, String url, String provider) throws IOException, SAXException, ParserConfigurationException, XMLStreamException {
        log.info("Subscriptions creating for provider : {} in data format : {}", provider, dataFormat);
        List<String> monitoringIds = getMonitoringIds(dataFormat, file);

        String globalSubscriptionId = "SIRI-API_" + dataFormat + "_" + provider;
        SubscriptionSetup globalSub = subscriptionManager.getSubscriptionBySubscriptionId(globalSubscriptionId);

        if (globalSub == null && !monitoringIds.isEmpty()) {
            SubscriptionSetup subscriptionSetup = createSubscriptionSetup(dataFormat, monitoringIds, url, provider);
            subscriptionSetup.setSubscriptionId(globalSubscriptionId);
            subscriptionSetup.setVendor(globalSubscriptionId);
            subscriptionSetup.setName(globalSubscriptionId);
            subscriptionManager.addSubscription(globalSubscriptionId, subscriptionSetup);
            globalSub = subscriptionManager.getSubscriptionBySubscriptionId(globalSubscriptionId);
        }

        for (String monitoringId : monitoringIds) {
            log.debug("SIRI by API - checking monitoring id : {}", monitoringId);
            if (!globalSub.getStopMonitoringRefValues().contains(monitoringId)) {
                log.debug("SIRI by API - adding to discovery cache for provider {} - id : {}", provider, monitoringId);
                globalSub.getStopMonitoringRefValues().add(monitoringId);
                discoveryCache.addStop(provider, monitoringId);
            }
            subscriptionManager.addSiriAPISubscription(provider + monitoringId, globalSubscriptionId);

        }

        ByteArrayInputStream byteArrayInputStream = extractXMLFromZip(file);


        SiriDataType siriDataType = null;
        switch (dataFormat) {
            case SIRI_SM:
                siriDataType = SiriDataType.STOP_MONITORING;
                break;
            case SIRI_ET:
                siriDataType = SiriDataType.ESTIMATED_TIMETABLE;
                break;
            case SIRI_SX:
                siriDataType = SiriDataType.SITUATION_EXCHANGE;
                break;
            case SIRI_VM:
                siriDataType = SiriDataType.VEHICLE_MONITORING;
                break;
            case SIRI_GM:
                siriDataType = SiriDataType.GENERAL_MESSAGE;
                break;
            case SIRI_FM:
                siriDataType = SiriDataType.FACILITY_MONITORING;
                break;
            default:
                break;
        }

        handler.processSiriClientRequestFromApis(Collections.singletonList(globalSubscriptionId), byteArrayInputStream, siriDataType, provider);
        log.info("Subscriptions created for provider : {} in data format : {}", provider, dataFormat);
    }

    private ByteArrayInputStream extractXMLFromZip(File file) throws IOException {
        return new ByteArrayInputStream(ZipFileUtils.extractFileFromZipFile(FileUtils.openInputStream(file)).toByteArray());
    }

    private Document parseXML(File file) throws IOException, ParserConfigurationException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        ByteArrayInputStream byteArrayInputStream = extractXMLFromZip(file);
        return builder.parse(byteArrayInputStream);
    }

    private SubscriptionSetup createSubscriptionSetup(String subscriptionType, List<String> monitoringIds, String url, String provider) {
        SubscriptionSetup subscriptionSetup = new SubscriptionSetup();
        subscriptionSetup.setSubscriptionId(UUID.randomUUID().toString());
        subscriptionSetup.setDatasetId(provider);
        subscriptionSetup.setUrlMap(new HashMap<>());
        switch (subscriptionType) {
            case SIRI_SM:
                subscriptionSetup.setSubscriptionType(SiriDataType.STOP_MONITORING);
                subscriptionSetup.getStopMonitoringRefValues().addAll(monitoringIds);
                subscriptionSetup.getUrlMap().put(RequestType.GET_STOP_MONITORING, url);
                subscriptionSetup.setRequestorRef("AURA_OKINA_SM");
                subscriptionSetup.setVendor("AURA-MULTITUD-CITYWAY-SIRI-SM");
                subscriptionSetup.setName("AURA-MULTITUD-CITYWAY-SIRI-SM");
                break;
            case SIRI_ET:
                subscriptionSetup.setSubscriptionType(SiriDataType.ESTIMATED_TIMETABLE);
                subscriptionSetup.getStopMonitoringRefValues().addAll(monitoringIds);
                subscriptionSetup.getUrlMap().put(RequestType.GET_ESTIMATED_TIMETABLE, url);
                subscriptionSetup.setRequestorRef("AURA_OKINA_ET");
                subscriptionSetup.setVendor("AURA-MULTITUD-CITYWAY-SIRI-ET");
                subscriptionSetup.setName("AURA-MULTITUD-CITYWAY-SIRI-ET");
                break;
            case SIRI_SX:
                subscriptionSetup.setSubscriptionType(SiriDataType.SITUATION_EXCHANGE);
                subscriptionSetup.getUrlMap().put(RequestType.GET_SITUATION_EXCHANGE, url);
                subscriptionSetup.setRequestorRef("AURA_OKINA_SX");
                subscriptionSetup.setVendor("AURA-MULTITUD-CITYWAY-SIRI-SX");
                subscriptionSetup.setName("AURA-MULTITUD-CITYWAY-SIRI-SX");
                break;
            case SIRI_VM:
                subscriptionSetup.setSubscriptionType(SiriDataType.VEHICLE_MONITORING);
                subscriptionSetup.getLineRefValues().addAll(monitoringIds);
                subscriptionSetup.getUrlMap().put(RequestType.GET_VEHICLE_MONITORING, url);
                subscriptionSetup.setRequestorRef("AURA_OKINA_VM");
                subscriptionSetup.setVendor("AURA-MULTITUD-CITYWAY-SIRI-VM");
                subscriptionSetup.setName("AURA-MULTITUD-CITYWAY-SIRI-VM");
                break;
            case SIRI_FM:
                subscriptionSetup.setSubscriptionType(SiriDataType.FACILITY_MONITORING);
                subscriptionSetup.getUrlMap().put(RequestType.GET_FACILITY_MONITORING, url);
                subscriptionSetup.setRequestorRef("AURA_OKINA_FM");
                subscriptionSetup.setVendor("AURA-MULTITUD-CITYWAY-SIRI-FM");
                subscriptionSetup.setName("AURA-MULTITUD-CITYWAY-SIRI-FM");
                break;
            case SIRI_GM:
                subscriptionSetup.setSubscriptionType(SiriDataType.GENERAL_MESSAGE);
                subscriptionSetup.getUrlMap().put(RequestType.GET_GENERAL_MESSAGE, url);
                subscriptionSetup.setRequestorRef("AURA_OKINA_GM");
                subscriptionSetup.setVendor("AURA-MULTITUD-CITYWAY-SIRI-GM");
                subscriptionSetup.setName("AURA-MULTITUD-CITYWAY-SIRI-GM");
                break;
            default:
                break;
        }
        subscriptionSetup.setSubscriptionMode(SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE);
        subscriptionSetup.setServiceType(SOAP);
        subscriptionSetup.setVersion("2.0");
        subscriptionSetup.setActive(true);
        subscriptionSetup.setDurationOfSubscriptionMinutes(5);
        subscriptionSetup.setContentType("text/xml;charset=UTF-8");
        return subscriptionSetup;
    }

    private List<String> getMonitoringIds(String dataFormat, File file) throws ParserConfigurationException, SAXException, IOException {
        List<String> monitoringIds = new ArrayList<>();
        Document document = parseXML(file);
        String tagName = null;
        switch (dataFormat) {
            case SIRI_SM:
                tagName = "MonitoringRef";
                break;
            case SIRI_ET:
                tagName = "StopPointRef";
                break;
            case SIRI_SX:
                tagName = "SituationNumber";
                break;
            case SIRI_VM:
                tagName = "VehicleMonitoringRef";
                break;
            case SIRI_FM:
                tagName = "FacilityRef";
                break;
            case SIRI_GM:
                tagName = "InfoMessageIdentifier";
                break;
            default:
                break;
        }
        NodeList idLists = document.getElementsByTagName(tagName);
        for (int i = 0; i < idLists.getLength(); i++) {
            Node node = idLists.item(i);
            monitoringIds.add(node.getFirstChild().getNodeValue());
        }
        return monitoringIds;
    }
}

