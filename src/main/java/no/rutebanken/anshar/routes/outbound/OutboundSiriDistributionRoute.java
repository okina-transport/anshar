package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.siri.Siri20RequestHandlerRoute;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.entur.siri.validator.SiriValidator;
import org.springframework.beans.factory.annotation.Autowired;
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

    // @formatter:off
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
                .log(LoggingLevel.DEBUG, "POST data to ${header.SubscriptionId}")
                .setHeader("CamelHttpMethod", constant("POST"))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml; charset=utf-8"))
                .bean(metrics, "countOutgoingData(${body},${header.requestorRef} , SUBSCRIBE)")
                .to("direct:siri.transform.data")
                .process(p -> {
                    Siri response = p.getIn().getBody(Siri.class);
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    if (p.getIn().getHeader(SIRI_VERSION_HEADER_NAME).equals(SiriValidator.Version.VERSION_2_1)){
                        CustomSiriXml.toXml(response, null, byteArrayOutputStream);
                    }else{
                        CustomSiriXml.toXml(downgradeSiriVersion(response), null, byteArrayOutputStream);
                    }
                    p.getIn().setBody(byteArrayOutputStream.toString());
                })
                .choice()
                .when(header(Siri20RequestHandlerRoute.TRANSFORM_SOAP).isEqualTo(simple(Siri20RequestHandlerRoute.TRANSFORM_SOAP)))
                .log(LoggingLevel.DEBUG, "Transforming SOAP")
                .to("xslt-saxon:xsl/siri_subscription_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .endChoice()
                .end()
                .setHeader("httpClient.socketTimeout", constant(timeout))
                .setHeader("httpClient.connectTimeout", constant(timeout))
                //               .choice()
//                .when(header("showBody").isEqualTo(true))
//                .to("log:push:" + getClass().getSimpleName() + "?showAll=true&multiline=true&level=DEBUG")
//                .endChoice()
//                .otherwise()
//                .to("log:push:" + getClass().getSimpleName() + "?showAll=false&showExchangeId=true&showHeaders=true&showException=true&multiline=true&showBody=false")
//                .end()
                .removeHeader("showBody")
                .toD("${header.endpoint}")
                .bean(subscriptionManager, "clearFailTracker(${header.SubscriptionId})")
                .choice()
                .when(header(HEARTBEAT_HEADER).isEqualTo(simple(HEARTBEAT_HEADER)))
                    .log(LoggingLevel.DEBUG, "HB-POST complete ${header.SubscriptionId} - Resp: [${header.CamelHttpResponseCode} ${header.CamelHttpResponseText}]")
                .otherwise()
                    .log(LoggingLevel.DEBUG, "DAT-POST complete ${header.SubscriptionId} - Resp: [${header.CamelHttpResponseCode} ${header.CamelHttpResponseText}]")
                .endChoice()
                .end();

    }
}
