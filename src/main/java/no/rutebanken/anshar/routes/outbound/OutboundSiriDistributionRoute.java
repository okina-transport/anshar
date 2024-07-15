package no.rutebanken.anshar.routes.outbound;

import jakarta.ws.rs.core.MediaType;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import org.apache.camel.Configuration;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.entur.siri.validator.SiriValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;

import static no.rutebanken.anshar.routes.HttpParameter.SIRI_VERSION_HEADER_NAME;
import static no.rutebanken.anshar.routes.RestRouteBuilder.downgradeSiriVersion;
import static no.rutebanken.anshar.routes.validation.validators.Constants.HEARTBEAT_HEADER;

@Service
public class OutboundSiriDistributionRoute extends RouteBuilder {

    @Autowired
    private ServerSubscriptionManager subscriptionManager;

    @Autowired
    private PrometheusMetricsService metrics;


    @Value("${anshar.outbound.error.redelivery.delay.millis:1000}")
    private int redeliveryDelay;

    @Value("${anshar.outbound.error.redelivery.count:2}")
    private int redeliveryCount;

    @Value("${anshar.outbound.timeout.socket:15000}")
    private int socketTimeout;

    @Value("${anshar.outbound.timeout.connect:5000}")
    private int connectTimeout;

    // @formatter:off
    @Override
    public void configure() {

        onException(Exception.class)
            .maximumRedeliveries(redeliveryCount)
            .redeliveryDelay(redeliveryDelay)
            .logRetryAttempted(true)
            .log("Retry triggered")
        ;

        onException(NullPointerException.class)
            .handled(false)
            .log("NullPointerException caught while sending data - retry NOT triggered")
        ;

        onException(HttpOperationFailedException.class)
            .handled(false)
                .process(p -> {
                    HttpOperationFailedException e = p.getProperty("CamelExceptionCaught", HttpOperationFailedException.class);
                    p.getMessage().setBody(e.getStatusCode());
                })
            .log("HttpOperationFailed - retry NOT triggered: Response code ${body}")
        ;

        from("direct:send.to.external.subscription")
                .routeId("send.to.external.subscription")
                .log(LoggingLevel.DEBUG, "POST data to ${header.SubscriptionId}")
                .setHeader("CamelHttpMethod", constant("POST"))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml; charset=utf-8"))
                .bean(metrics, "countOutgoingData(${body},${header.requestorRef} , SUBSCRIBE)")
                .to("direct:siri.transform.data")
                .choice()
                .when(header(SIRI_VERSION_HEADER_NAME).isEqualTo(SiriValidator.Version.VERSION_2_1))
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat(SiriValidator.Version.VERSION_2_1))
                .endChoice()
                .otherwise()
                .process(p -> {
                    p.getMessage().setBody(downgradeSiriVersion(p.getIn().getBody(Siri.class)));
                })
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat(SiriValidator.Version.VERSION_2_0))
                .end()
                .setHeader("httpClient.socketTimeout", constant(socketTimeout))
                .setHeader("httpClient.connectTimeout", constant(connectTimeout))
                .choice()
                .when(header("showBody").isEqualTo(true))
                        .to("log:push:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .endChoice()
                    .otherwise()
                        .to("log:push:" + getClass().getSimpleName() + "?showAll=false&showExchangeId=true&showHeaders=true&showException=true&multiline=true&showBody=false")
                .end()
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
