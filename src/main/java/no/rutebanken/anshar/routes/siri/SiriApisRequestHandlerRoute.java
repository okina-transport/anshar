package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SiriDataType;
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
import java.util.stream.Collectors;

import static no.rutebanken.anshar.subscription.SubscriptionSetup.ServiceType.SOAP;

@Component
public class SiriApisRequestHandlerRoute extends BaseRouteBuilder {
    private final String dataSetId = "AURA";
    @Autowired
    private SiriHandler handler;
    @Value("${providers.siri:test}")
    private String providers;
    @Value("${api.siri:http://test.fr}")
    private String apiSiri;
    @Value("${cron.siri:0+0+0+1+1+?+2099}")
    private String cronSchedule;
    @Value("${data.format.siri:test}")
    private String dataFormat;



    public SiriApisRequestHandlerRoute(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {
        singletonFrom("quartz://anshar/SiriApiQuartz?cron=" + cronSchedule + "&trigger.timeZone=Europe/Paris", "monitor.siri.api")
                .log("Starting Siri from API")
                .process(p -> createSubscriptionsFromApis());
    }


    private void createSubscriptionsFromApis() throws IOException, SAXException, ParserConfigurationException, XMLStreamException {


        long startTime = DateTime.now().toInstant().getMillis();

        String[] providersList = providers.split(",");
        String[] dataFormatList = dataFormat.split(",");

        for (String provider : providersList) {
            for (String dataFormat : dataFormatList) {
                String auraDataType = "horaire-tr";
                if (dataFormat.equals("siri-sx")) {
                    auraDataType = "info-transport";
                }
                File file = new File("/tmp/" + provider + "_" + dataFormat + ".zip");
                log.info("Get Siri file for provider : " + provider + " in data format : " + dataFormat);
                String url = apiSiri + auraDataType + "/download?provider=" + provider + "&dataFormat=" + dataFormat + "&dataProfil=OPENDATA";
                log.info("URL : " + url);
                FileUtils.copyURLToFile(new URL(url), file);

                if (file.length() > 0) {
                    createSubscriptionsFromFile(dataFormat, file, url, provider);
                }
                else{
                    log.error("No file returned for the provider " + provider);
                }
            }
        }


        long endTime = DateTime.now().toInstant().getMillis();
        long processTime = (endTime - startTime) / 1000;
        log.info("Siri API completed in {} seconds",processTime);

    }

    public void createSubscriptionsFromFile(String dataFormat, File file, String url, String provider) throws IOException, SAXException, ParserConfigurationException, XMLStreamException {
        log.info("Subscriptions creating for provider : " + provider + " in data format : " + dataFormat);
        List<String> monitoringIds = getMonitoringIds(dataFormat, file);

        List<SubscriptionSetup> subscriptionSetupList = new ArrayList<>();
        for (String monitoringId : monitoringIds) {
            SubscriptionSetup subscriptionSetup = createSubscriptionSetup(dataFormat, monitoringId, url);
            subscriptionManager.addSubscription(subscriptionSetup.getSubscriptionId(), subscriptionSetup);
            subscriptionSetupList.add(subscriptionSetup);
        }

        ByteArrayInputStream byteArrayInputStream = extractXMLFromZip(file);
        List<String> ids = subscriptionSetupList.stream().map(SubscriptionSetup::getSubscriptionId).collect(Collectors.toList());

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
        }

        handler.processSiriClientRequestFromApis(ids, byteArrayInputStream, siriDataType, dataSetId);
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

    private SubscriptionSetup createSubscriptionSetup(String subscriptionType, String monitoringId, String url) {
        SubscriptionSetup subscriptionSetup = new SubscriptionSetup();
        subscriptionSetup.setSubscriptionId(UUID.randomUUID().toString());
        subscriptionSetup.setDatasetId(dataSetId);
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
        }
        NodeList idLists = document.getElementsByTagName(tagName);
        for (int i = 0; i < idLists.getLength(); i++) {
            Node node = idLists.item(i);
            monitoringIds.add(node.getFirstChild().getNodeValue());
        }
        return monitoringIds;
    }
}
