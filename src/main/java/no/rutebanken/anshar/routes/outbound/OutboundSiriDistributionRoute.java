package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.data.util.TimingTracer;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.util.PerformanceTimingService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.MonitoringRefStructure;

import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

@Service
public class OutboundSiriDistributionRoute extends RouteBuilder {

    @Autowired
    private ServerSubscriptionManager subscriptionManager;

    @Autowired
    private PrometheusMetricsService metrics;

    @Override
    public void configure() {

        int timeout = 15000;

        onException(Exception.class)
            .maximumRedeliveries(2)
            .redeliveryDelay(3000) //milliseconds
            .logRetryAttempted(true)
            .log("Retry triggered")
        ;

        from("direct:send.to.external.subscription")
                .routeId("send.to.external.subscription")
                .log(LoggingLevel.INFO, "POST data to ${header.SubscriptionId}")
                .setHeader("CamelHttpMethod", constant("POST"))
                .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.APPLICATION_XML))
                .bean(metrics, "countOutgoingData(${body}, SUBSCRIBE)")
                .to("direct:siri.transform.data")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .setHeader("httpClient.socketTimeout", constant(timeout))
                .setHeader("httpClient.connectTimeout", constant(timeout))
                .choice()
                .when(header("showBody").isEqualTo(true))
                        .to("log:push:" + getClass().getSimpleName() + "?showAll=true&multiline=true&level=DEBUG")
                .endChoice()
                    .otherwise()
                        .to("log:push:" + getClass().getSimpleName() + "?showAll=false&showExchangeId=true&showHeaders=true&showException=true&multiline=true&showBody=false")
                .end()
                .removeHeader("showBody")
                .toD("${header.endpoint}")
                .process(e->{


                    if (!e.getIn().getBody(String.class).contains("HeartbeatNotification")){
                        String subsId = e.getIn().getHeader("SubscriptionId", String.class);

                        Optional<OutboundSubscriptionSetup> outboundSubOpt = subscriptionManager.getSubscriptions().stream()
                                .filter(sub -> ((OutboundSubscriptionSetup) sub).getSubscriptionId().equals(subsId))
                                .findFirst();


                        if (outboundSubOpt.isPresent()){
                            OutboundSubscriptionSetup outboundSub = outboundSubOpt.get();
                            Set<String> filter = outboundSub.getFilterMap().get(MonitoringRefStructure.class);
                            String monitoringRef = filter.iterator().next();
                            TimingTracer tracer = PerformanceTimingService.getTracer(monitoringRef);
                            tracer.mark("Data sent to output");
                            log.info(tracer.toString());
                        }
                    }



                })
                .bean(subscriptionManager, "clearFailTracker(${header.SubscriptionId})")
                .log(LoggingLevel.INFO, "POST complete ${header.SubscriptionId} - Response: [${header.CamelHttpResponseCode} ${header.CamelHttpResponseText}]");

    }
}
