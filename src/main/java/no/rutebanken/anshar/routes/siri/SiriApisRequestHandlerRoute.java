package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.api.SiriApi;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import no.rutebanken.anshar.util.ZipFileUtils;
import org.apache.commons.io.FileUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static no.rutebanken.anshar.subscription.SubscriptionSetup.ServiceType.SOAP;

@Component
public class SiriApisRequestHandlerRoute extends BaseRouteBuilder {

    @Autowired
    private SiriHandler handler;

    @Value("${cron.siri:0+0+0+1+1+?+2099}")
    private String cronSchedule;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private AnsharConfiguration configuration;

    public SiriApisRequestHandlerRoute(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {

        if (!configuration.isCurrentInstanceLeader()){
            log.info("Instance non leader. Pas de récupération SIRI par API");
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

            if (!shouldSiriApiBeRecovered(siriApi.getType())){
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
        }

        long endTime = DateTime.now().toInstant().getMillis();
        long processTime = (endTime - startTime) / 1000;
        log.info("Siri API completed in {} seconds", processTime);

    }


    /**
     * Checks if the current anshar instance is allowed to get siri API data
     *      e.g : if the current instance is running with DATA_SM app_mode, it is only allowed to recover siri-sm data
     * @return
     *      true : the current instance of anshar is allowed to get data from this siri api
     *      false : the current instace of anshar must not recover data from this siri api
     */
    private boolean shouldSiriApiBeRecovered(String subscriptionType){

        if ("siri-sm".equals(subscriptionType) && configuration.processSM()){
            return true;
        }

        if ("siri-sx".equals(subscriptionType) && configuration.processSX()){
            return true;
        }

        if ("siri-et".equals(subscriptionType) && configuration.processET()){
            return true;
        }

        if ("siri-vm".equals(subscriptionType) && configuration.processVM()){
            return true;
        }

        return false;
    }


    public void createSubscriptionsFromFile(String dataFormat, File file, String url, String provider) throws IOException, SAXException, ParserConfigurationException, XMLStreamException {
        log.info("Subscriptions creating for provider : " + provider + " in data format : " + dataFormat);
        List<String> monitoringIds = getMonitoringIds(dataFormat, file);

        List<SubscriptionSetup> subscriptionSetupList = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (String monitoringId : monitoringIds) {

            String subId;
            if (!subscriptionManager.isSiriAPISubscriptionExisting(provider + monitoringId)){
                SubscriptionSetup subscriptionSetup = createSubscriptionSetup(dataFormat, monitoringId, url, provider);
                subscriptionManager.addSubscription(subscriptionSetup.getSubscriptionId(), subscriptionSetup);
                subscriptionSetupList.add(subscriptionSetup);
                subId = subscriptionSetup.getSubscriptionId();
                subscriptionManager.addSiriAPISubscription(provider + monitoringId, subId);
            } else{
                subId = subscriptionManager.getSubscriptionForMonitoringRef(provider + monitoringId);
            }

            if (!ids.contains(subId)){
                ids.add(subId);
            }
        }

        ByteArrayInputStream byteArrayInputStream = extractXMLFromZip(file);


        SiriDataType siriDataType = null;
        switch (dataFormat) {
            case "siri-sm":
                siriDataType = SiriDataType.STOP_MONITORING;
                break;
            case "siri-et":
                siriDataType = SiriDataType.ESTIMATED_TIMETABLE;
                break;
            case "siri-sx":
                siriDataType = SiriDataType.SITUATION_EXCHANGE;
                break;
            case "siri-vm":
                siriDataType = SiriDataType.VEHICLE_MONITORING;
                break;
            case "siri-gm":
                siriDataType = SiriDataType.GENERAL_MESSAGE;
                break;
            case "siri-fm":
                siriDataType = SiriDataType.FACILITY_MONITORING;
                break;
        }

        handler.processSiriClientRequestFromApis(ids, byteArrayInputStream, siriDataType, provider);
        log.info("Subscriptions created for provider : " + provider + " in data format : " + dataFormat);
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

    private SubscriptionSetup createSubscriptionSetup(String subscriptionType, String monitoringId, String url, String provider) {
        SubscriptionSetup subscriptionSetup = new SubscriptionSetup();
        subscriptionSetup.setSubscriptionId(UUID.randomUUID().toString());
        subscriptionSetup.setDatasetId(provider);
        subscriptionSetup.setUrlMap(new HashMap<>());
        switch (subscriptionType) {
            case "siri-sm":
                subscriptionSetup.setSubscriptionType(SiriDataType.STOP_MONITORING);
                subscriptionSetup.setStopMonitoringRefValue(monitoringId);
                subscriptionSetup.getUrlMap().put(RequestType.GET_STOP_MONITORING, url);
                subscriptionSetup.setRequestorRef("AURA_OKINA_SM");
                subscriptionSetup.setVendor("AURA-MULTITUD-CITYWAY-SIRI-SM-" + monitoringId);
                subscriptionSetup.setName("AURA-MULTITUD-CITYWAY-SIRI-SM-" + monitoringId);
                break;
            case "siri-et":
                subscriptionSetup.setSubscriptionType(SiriDataType.ESTIMATED_TIMETABLE);
                subscriptionSetup.setStopMonitoringRefValue(monitoringId);
                subscriptionSetup.getUrlMap().put(RequestType.GET_ESTIMATED_TIMETABLE, url);
                subscriptionSetup.setRequestorRef("AURA_OKINA_ET");
                subscriptionSetup.setVendor("AURA-MULTITUD-CITYWAY-SIRI-ET-" + monitoringId);
                subscriptionSetup.setName("AURA-MULTITUD-CITYWAY-SIRI-ET-" + monitoringId);
                break;
            case "siri-sx":
                subscriptionSetup.setSubscriptionType(SiriDataType.SITUATION_EXCHANGE);
                subscriptionSetup.getUrlMap().put(RequestType.GET_SITUATION_EXCHANGE, url);
                subscriptionSetup.setRequestorRef("AURA_OKINA_SX");
                subscriptionSetup.setVendor("AURA-MULTITUD-CITYWAY-SIRI-SX");
                subscriptionSetup.setName("AURA-MULTITUD-CITYWAY-SIRI-SX");
                break;
            case "siri-vm":
                subscriptionSetup.setSubscriptionType(SiriDataType.VEHICLE_MONITORING);
                subscriptionSetup.getUrlMap().put(RequestType.GET_VEHICLE_MONITORING, url);
                subscriptionSetup.setRequestorRef("AURA_OKINA_VM");
                subscriptionSetup.setVendor("AURA-MULTITUD-CITYWAY-SIRI-VM");
                subscriptionSetup.setName("AURA-MULTITUD-CITYWAY-SIRI-VM");
                break;
            case "siri-fm":
                subscriptionSetup.setSubscriptionType(SiriDataType.FACILITY_MONITORING);
                subscriptionSetup.getUrlMap().put(RequestType.GET_FACILITY_MONITORING, url);
                subscriptionSetup.setRequestorRef("AURA_OKINA_FM");
                subscriptionSetup.setVendor("AURA-MULTITUD-CITYWAY-SIRI-FM");
                subscriptionSetup.setName("AURA-MULTITUD-CITYWAY-SIRI-FM");
                break;
            case "siri-gm":
                subscriptionSetup.setSubscriptionType(SiriDataType.GENERAL_MESSAGE);
                subscriptionSetup.getUrlMap().put(RequestType.GET_GENERAL_MESSAGE, url);
                subscriptionSetup.setRequestorRef("AURA_OKINA_GM");
                subscriptionSetup.setVendor("AURA-MULTITUD-CITYWAY-SIRI-GM");
                subscriptionSetup.setName("AURA-MULTITUD-CITYWAY-SIRI-GM");
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
            case "siri-sm":
                tagName = "MonitoringRef";
                break;
            case "siri-et":
                tagName = "StopPointRef";
                break;
            case "siri-sx":
                tagName = "SituationNumber";
                break;
            case "siri-vm":
                tagName = "VehicleMonitoringRef";
                break;
            case "siri-fm":
                tagName = "FacilityRef";
                break;
            case "siri-gm":
                tagName = "InfoMessageIdentifier";
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

