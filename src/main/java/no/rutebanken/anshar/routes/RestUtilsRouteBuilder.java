package no.rutebanken.anshar.routes;

import no.rutebanken.anshar.config.AnsharConfiguration;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RestUtilsRouteBuilder extends RouteBuilder {


    @Autowired
    private AnsharConfiguration configuration;

    @Override
    public void configure() throws Exception {

        from("direct:anshar.blocked.tracking.header.response")
                .removeHeaders("*")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("400")) //400 Bad request
                .setBody(constant(""))
                .routeId("reject.request.blocked.header")
        ;

        from("direct:anshar.invalid.tracking.header.response")
                .log("invalid")
                .removeHeaders("*")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("400")) //400 Bad request
                .setBody(constant("Missing required header (" + configuration.getTrackingHeaderName() + ")"))
                .routeId("reject.request.missing.header")
        ;

        from("direct:send.to.expired.data.queue")
                .to("activemq:queue:sm.expired.data?timeToLive=600000")
                .routeId("send.to.expired.data.queue")
        ;

        from("direct:anshar.invalid.input.request")
                .log("invalid")
                .removeHeaders("*")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("400")) //400 Bad request
                .setBody(constant("The input request is invalid"))
                .routeId("reject.invalid.input.request")
        ;

    }
}
