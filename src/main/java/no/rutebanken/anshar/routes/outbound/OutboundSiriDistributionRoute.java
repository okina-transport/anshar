package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.siri.Siri20RequestHandlerRoute;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
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
import static no.rutebanken.anshar.routes.validation.validators.Constants.IS_IDFM_GM;
import static no.rutebanken.anshar.routes.validation.validators.Constants.ORIGINAL_BODY_HEADER;

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

        from("direct:send.to.external.subscription")
                .routeId("send.to.external.subscription")
                .log(LoggingLevel.DEBUG, "POST data to ${header.SubscriptionId}")
                .setHeader("CamelHttpMethod", constant("POST"))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml; charset=utf-8"))
                .process(e->{
                    metrics.countOutgoingData(e.getIn().getBody(Siri.class),(String) e.getIn().getHeader("requestorRef"),SubscriptionSetup.SubscriptionMode.SUBSCRIBE);
                })
                .to("direct:siri.transform.data")
                .process(p -> {
                    Siri response = p.getIn().getBody(Siri.class);
                    utils.handleFlexibleLines(response);
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    if (p.getIn().getHeader(SIRI_VERSION_HEADER_NAME).equals(SiriValidator.Version.VERSION_2_1)){
                        CustomSiriXml.toXml(response, null, byteArrayOutputStream);
                    }else if (p.getIn().getHeader(SIRI_VERSION_HEADER_NAME).equals(SiriValidator.Version.VERSION_2_0_IDFM_2_4)){
                        CustomSiriXml.toXml(downgradeSiriVersion(response,"2.0[FR-IDF-2.4]"), null, byteArrayOutputStream);
                    }else{
                        CustomSiriXml.toXml(downgradeSiriVersion(response), null, byteArrayOutputStream);
                    }
                    p.getIn().setBody(byteArrayOutputStream.toString());
                })
                .choice()
                .when(header(IS_IDFM_GM).isEqualTo(Boolean.TRUE))
                .process(p -> {
                    String siriGmXml = p.getIn().getBody(String.class);
                    siriGmXml = siriGmXml.replaceAll("FrGeneralMessageStructure", "IDFGeneralMessageStructure");
                    p.getIn().setBody(siriGmXml);
                })
                .endChoice()
                .end()
                .choice()
                .when(header(Siri20RequestHandlerRoute.TRANSFORM_SOAP).isEqualTo(simple(Siri20RequestHandlerRoute.TRANSFORM_SOAP)))
                    .log(LoggingLevel.DEBUG, "Transforming SOAP")
                    .process(e->{
                        // saving original body in header
                        String originalBody = e.getIn().getBody(String.class);
                        e.getIn().setHeader(ORIGINAL_BODY_HEADER,originalBody);
                    })
                    .to("xslt-saxon:xsl/siri_subscription_raw_soap.xsl")// Convert SIRI raw request to SOAP version
                    .process(e->{
                        String transformedBody = e.getIn().getBody(String.class);
                        if (transformedBody.length() < 40){
                            // transform msg too short. means there was an issue
                            log.error("Error while transforming soap response. Original body was: " + e.getIn().getHeader(ORIGINAL_BODY_HEADER));
                            log.error("transformed body: " + transformedBody);
                        }
                        e.getIn().removeHeader(ORIGINAL_BODY_HEADER);
                     })
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
                .toD("${header.endpoint}?maxTotalConnections=" + maxTotalConnections + "&connectionsPerRoute=" + connectionsByRoute)
                .process(e->{
                    String subsId = (String) e.getIn().getHeader("SubscriptionId");
                    subscriptionManager.clearFailTracker(subsId);
                })
                .end();

    }
}
