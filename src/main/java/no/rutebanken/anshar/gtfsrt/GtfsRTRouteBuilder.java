package no.rutebanken.anshar.gtfsrt;

import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.GTFSRTType;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.camel.Exchange;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Component
public class GtfsRTRouteBuilder extends BaseRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(GtfsRTRouteBuilder.class);

    private static final int INTERVAL_IN_MILLIS = 60000;

    private static final int INTERVAL_IN_MILLIS_ISHTAR = 1080000; // toutes les demi-heures

    @Value("${ishtar.server.port}")
    private String ISHTAR_PORT;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private AnsharConfiguration configuration;

    protected GtfsRTRouteBuilder(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {

        if ((!configuration.processSX() && !configuration.processVM() && !configuration.processSM() && !configuration.processET()) || !configuration.isCurrentInstanceLeader()){
            logger.info("Application non paramétrée en SM/SX/ET/VM ou instance non leader. Pas de récupération GTFS-RT");
            return;
        }

        if (subscriptionConfig.getGtfsRTApis().size() > 0) {
            singletonFrom("quartz://anshar/import_GTFSRT_DATA?trigger.repeatInterval=" + INTERVAL_IN_MILLIS,
                    "import_GTFSRT_DATA")
                    .bean(GtfsRTDataRetriever.class, "getGTFSRTData")
                    .end();
        } else {
            logger.info("Pas d'url GTFS-RT définie");
        }

        singletonFrom("quartz://anshar/getAllGTFSRTFromIshtar?trigger.repeatInterval=" + INTERVAL_IN_MILLIS_ISHTAR,"getAllGTFSRTFromIshtar")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD(ISHTAR_PORT + "/gtfs-rt-apis/all")
                .unmarshal().json(JsonLibrary.Jackson, List.class)
                .log("Get GTFS RT Api data from Ishtar project")
                .process(exchange -> {
                    List<Object> gtfsResult = body().getExpression().evaluate(exchange, List.class);
                    ArrayList<GtfsRTApi> results = new ArrayList<>();
                    if (gtfsResult != null) {
                        for (Object obj : gtfsResult) {
                            if ((Boolean) ((LinkedHashMap<?, ?>) obj).get("active")) {
                                GtfsRTApi newGtfs = new GtfsRTApi();
                                newGtfs.setDatasetId(((LinkedHashMap<?, ?>) obj).get("datasetId").toString());
                                newGtfs.setType(GTFSRTType.valueOf(((LinkedHashMap<?, ?>) obj).get("type").toString()));
                                newGtfs.setUrl(((LinkedHashMap<?, ?>) obj).get("url").toString());
                                newGtfs.setActive((Boolean) ((LinkedHashMap<?, ?>) obj).get("active"));
                                results.add(newGtfs);
                            }
                        }
                        subscriptionConfig.getGtfsRTApis().addAll(results);
                    }
                })
                .bean(GtfsRTDataRetriever.class, "getGTFSRTData()")
                .end();

    }
}
