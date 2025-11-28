package no.rutebanken.anshar.gbfs;

import no.rutebanken.anshar.config.AnsharConfiguration;
import org.apache.camel.builder.RouteBuilder;
import org.mobilitydata.gbfs.v3_0.station_status.GBFSStationStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GbfsRouteBuilder extends RouteBuilder {

    private final AnsharConfiguration ansharConfiguration;
    private final String routeName;
    private final boolean gbfsActivated;
    private final GbfsIngester gbfsIngester;

    public GbfsRouteBuilder(AnsharConfiguration ansharConfiguration, @Value("${anshar.gbfs.activated:false}") boolean gbfsActivated, @Value("${anshar.gbfs.to.siri.route.name:activemq:queue:gbfs.to.siri?asyncConsumer=true&disableReplyTo=true&concurrentConsumers=3}") String routeName, GbfsIngester gbfsIngester) {
        this.ansharConfiguration = ansharConfiguration;
        this.gbfsActivated = gbfsActivated;
        this.routeName = routeName;
        this.gbfsIngester = gbfsIngester;
    }


    @Override
    public void configure() throws Exception {
        if (gbfsActivated && ansharConfiguration.processFM()) {
            from(routeName)
                    .unmarshal().json(GBFSStationStatus.class)
                    .bean(gbfsIngester, "ingest(${body}")
                    .routeId("gbfs.to.siri").end();
        }
    }

}
