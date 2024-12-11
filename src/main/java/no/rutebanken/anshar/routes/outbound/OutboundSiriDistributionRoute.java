package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.siri.Siri20RequestHandlerRoute;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.entur.siri.validator.SiriValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;

import java.io.ByteArrayOutputStream;

import static no.rutebanken.anshar.routes.HttpParameter.SIRI_VERSION_HEADER_NAME;
import static no.rutebanken.anshar.routes.RestRouteBuilder.downgradeSiriVersion;
import static no.rutebanken.anshar.routes.validation.validators.Constants.HEARTBEAT_HEADER;

@Service
public class OutboundSiriDistributionRoute extends RouteBuilder {

    @Autowired
    private ServerSubscriptionManager subscriptionManager;

    @Autowired
    private PrometheusMetricsService metrics;

    @Autowired
    private Utils utils;

    @Value("${outbound.distribution.route.maxTotalConnections}")
    private long maxTotalConnections;

    @Value("${outbound.distribution.route.connectionsbyroute}")
    private long connectionsByRoute;

    @Value("${outbound.distribution.threads}")
    private int threads;

    @Value("${outbound.distribution.max.pool.size}")
    private int maxPoolSize;


    // @formatter:off
    @Override
    public void configure() {

        int timeout = 15000;

        onException(Exception.class)
                .maximumRedeliveries(0)
                .redeliveryDelay(3000) //milliseconds
                .logRetryAttempted(true)
                .log("Retry triggered")
        ;

        from("direct:send.to.external.subscription.part1")
                .threads(threads)
                .maxPoolSize(maxPoolSize)
                .routeId("send.to.external.subscription.part1")
                .setHeader("CamelHttpMethod", constant("POST"))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml; charset=utf-8"))
                .to("direct:send.to.external.subscription.part1.1")
                .to("direct:send.to.external.subscription.part1.2")
                .end();


        from("direct:send.to.external.subscription.part1.1")
                .threads(threads)
                .maxPoolSize(maxPoolSize)
                .setExchangePattern(ExchangePattern.InOnly)
                .routeId("send.to.external.subscription.part1.1")
                .process(e->{
                    metrics.countOutgoingData(e.getIn().getBody(Siri.class),(String) e.getIn().getHeader("requestorRef"),SubscriptionSetup.SubscriptionMode.SUBSCRIBE);
                })
                .end();

        from("direct:send.to.external.subscription.part1.2")
                .threads(threads)
                .maxPoolSize(maxPoolSize)
                .routeId("send.to.external.subscription.part1.2")
                .to("direct:siri.transform.data")
                .end();



        from("direct:send.to.external.subscription.part3")
                .threads(threads)
                .maxPoolSize(maxPoolSize)
                .routeId("send.to.external.subscription.part3")
                .choice()
                .when(header(Siri20RequestHandlerRoute.TRANSFORM_SOAP).isEqualTo(simple(Siri20RequestHandlerRoute.TRANSFORM_SOAP)))
                .log(LoggingLevel.DEBUG, "Transforming SOAP")
                .to("xslt-saxon:xsl/siri_subscription_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .endChoice()
                .end()
                .setHeader("httpClient.socketTimeout", constant(timeout))
                .setHeader("httpClient.connectTimeout", constant(timeout))
                .removeHeader("showBody")
                .removeHeaders("Siri_version*")
                .removeHeaders("Transform_soap*")
                .removeHeaders("Httpclient.*")
                .toD("${header.endpoint}?maxTotalConnections=" + maxTotalConnections + "&connectionsPerRoute=" + connectionsByRoute)
                .bean(subscriptionManager, "clearFailTracker(${header.SubscriptionId})")
                .choice()
                .when(header(HEARTBEAT_HEADER).isEqualTo(simple(HEARTBEAT_HEADER)))
                .log(LoggingLevel.DEBUG, "HB-POST complete ${header.SubscriptionId} - Resp: [${header.CamelHttpResponseCode} ${header.CamelHttpResponseText}]")
                .otherwise()
                .log(LoggingLevel.DEBUG, "DAT-POST complete ${header.SubscriptionId} - Resp: [${header.CamelHttpResponseCode} ${header.CamelHttpResponseText}]")
                .endChoice()
                .end();

        from("direct:send.to.external.subscription.part2")
                .threads(threads)
                .maxPoolSize(maxPoolSize)
                .routeId("send.to.external.subscription.part2")
                .process(p -> {
                    Siri response = p.getIn().getBody(Siri.class);
                    utils.handleFlexibleLines(response);
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    if (p.getIn().getHeader(SIRI_VERSION_HEADER_NAME).equals(SiriValidator.Version.VERSION_2_1)){
                        CustomSiriXml.toXml(response, null, byteArrayOutputStream);
                    }else{
                        CustomSiriXml.toXml(downgradeSiriVersion(response), null, byteArrayOutputStream);
                    }
                    p.getIn().setBody(byteArrayOutputStream.toString());
                })
                .end();



        from("direct:send.to.external.subscription")
                .threads(threads)
                .maxPoolSize(maxPoolSize)
                .routeId("send.to.external.subscription")
                .to("direct:send.to.external.subscription.part1")
                .to("direct:send.to.external.subscription.part2")
                .to("direct:send.to.external.subscription.part3")
                .end();
    }
}
