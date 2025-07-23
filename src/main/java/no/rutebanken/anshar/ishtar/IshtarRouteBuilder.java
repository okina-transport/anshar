package no.rutebanken.anshar.ishtar;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.DiscoverySubscription;
import no.rutebanken.anshar.ishtar.clearcache.ClearCacheProcessor;
import no.rutebanken.anshar.ishtar.model.GtfsRTApiDto;
import no.rutebanken.anshar.ishtar.model.SiriApiDto;
import no.rutebanken.anshar.ishtar.model.SubscriptionDto;
import no.rutebanken.anshar.ishtar.requestlogging.model.HttpRequestDto;
import no.rutebanken.anshar.ishtar.synchronize.IshtarSynchronizeProcessor;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.subscription.SubscriptionInitializer;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.builder.Builder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.anshar.routes.DiscoverySubscriptionsRouteBuilder.SEND_DISCOVERY_REQUEST_PREPROCESS_ROUTE;
import static no.rutebanken.anshar.routes.admin.AdministrationRoute.*;
import static no.rutebanken.anshar.subscription.DiscoverySubscriptionCreator.SUBSCRIPTION_URL_HEADER;
import static no.rutebanken.anshar.subscription.SubscriptionSetup.SubscriptionMode.*;
import static org.apache.camel.support.builder.PredicateBuilder.*;

@Component
public class IshtarRouteBuilder extends BaseRouteBuilder {

    private final IshtarSynchronizeProcessor ishtarSynchronizeProcessor;
    private final ClearCacheProcessor clearCacheProcessor;
    private final int ishtarSynchronizeIntervalMs;

    private static final Predicate isDiscoverySubscription = Builder.body().method("discoverySubscription").isEqualTo(true);
    private static final Predicate isSubscription = Builder.body().method("subscriptionMode").isEqualTo(SUBSCRIBE);
    private static final Predicate isLite = Builder.body().method("subscriptionMode").in(LITE, LITE_XML);
    private static final Predicate isFetchedDelivery = Builder.body().method("subscriptionMode").in(FETCHED_DELIVERY,
            POLLING_FETCHED_DELIVERY);
    private static final Predicate isSubscriptionV1_4 = Builder.body().method("version").isEqualTo("1.4");
    private static final Predicate isSOAP = Builder.body().method("serviceType").isEqualTo("SOAP");
    private static final Predicate isREST = Builder.body().method("serviceType").isEqualTo("REST");
    private static final Predicate isSiri20ToSiriWS14Subscription = and(isSubscriptionV1_4, isSOAP, or(isSubscription, isFetchedDelivery));
    private static final Predicate isSiri20ToSiriWS14RequestResponse = and(isSubscriptionV1_4, isSOAP, not(isSubscription), not(isFetchedDelivery));
    private static final Predicate isSiri20ToSiriRS14Subscription = and(isSubscriptionV1_4, isREST);
    private static final Predicate isSiri20ToSiriWS20Subscription = and(not(isSubscriptionV1_4), isSOAP, or(isSubscription, isFetchedDelivery));
    private static final Predicate isSiri20ToSiriWS20RequestResponse = and(not(isSubscriptionV1_4), isSOAP, not(isSubscription), not(isFetchedDelivery));
    private static final Predicate isSiri20ToSiriRS20Subscription = and(not(isSubscriptionV1_4), isREST, or(isSubscription, isFetchedDelivery));
    private static final Predicate isSiri20ToSiriRS20RequestResponse = and(not(isSubscriptionV1_4), isREST, not(isSubscription), not(isFetchedDelivery));
    private static final Predicate isSiriLiteToSiriRS20RequestResponse = and(not(isSubscriptionV1_4), not(isSubscription), not(isFetchedDelivery), isLite);


    protected IshtarRouteBuilder(AnsharConfiguration config,
                                 SubscriptionManager subscriptionManager,
                                 IshtarSynchronizeProcessor ishtarSynchronizeProcessor,
                                 ClearCacheProcessor clearCacheProcessor,
                                 @Value("${ishtar.interval.millis:180000}") int ishtarSynchronizeIntervalMs) {
        super(config, subscriptionManager);
        this.ishtarSynchronizeProcessor = ishtarSynchronizeProcessor;
        this.clearCacheProcessor = clearCacheProcessor;
        this.ishtarSynchronizeIntervalMs = ishtarSynchronizeIntervalMs;
    }

    private static void process(Exchange e) {
        HttpRequestDto out = new HttpRequestDto();
        out.setUrl(e.getIn().getHeader(SUBSCRIPTION_URL_HEADER, String.class));
        out.setHeaders(e.getIn().getHeaders());
        out.setBody(e.getIn().getBody(String.class));
        out.setMethod(e.getIn().getHeader(Exchange.HTTP_METHOD).toString());
        e.getIn().setBody(out);
    }

    @Override
    public void configure() throws Exception {

        singletonFrom("quartz://anshar/autoGetAllDataFromIshtar?trigger.repeatInterval=" + ishtarSynchronizeIntervalMs,
                "autoGetAllDataFromIshtar")
                .to(ISHTAR_SYNCHRONIZE_DATA_ROUTE)
                .end();

        from("direct://isthar.synchronize.data")
                .routeId(ISHTAR_SYNCHRONIZE_DATA_ROUTE)
                .onException(Exception.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                .setBody(simple("Error during data synchronization : ${exception.message}"))
                .end()
                .process(ishtarSynchronizeProcessor)
                .bean(SubscriptionInitializer.class, "createSubscriptions")
                .end();

        from(ISHTAR_GET_GTFS_RT_API_REQUEST_ROUTE)
                .routeId(ISHTAR_GET_GTFS_RT_API_REQUEST_ROUTE)
                .removeHeaders("*")
                .unmarshal().json(GtfsRTApiDto.class)
                .convertBodyTo(HttpRequestDto.class)
                .marshal().json()
                .end();

        from(ISHTAR_GET_SIRI_API_REQUEST_ROUTE)
                .routeId(ISHTAR_GET_SIRI_API_REQUEST_ROUTE)
                .removeHeaders("*")
                .unmarshal().json(SiriApiDto.class)
                .convertBodyTo(HttpRequestDto.class)
                .marshal().json()
                .end();

        from(ISHTAR_GET_SUBSCRIPTION_REQUEST_ROUTE)
                .routeId(ISHTAR_GET_SUBSCRIPTION_REQUEST_ROUTE)
                .removeHeaders("*")
                .unmarshal().json(SubscriptionDto.class)
                .choice()
                .when(not(isDiscoverySubscription))
                .to("direct:is.not.discovery.subscription")
                .otherwise()
                .to("direct:discovery.subscription")
                .end();

        from("direct:is.not.discovery.subscription")
                .routeId("is.not.discovery.subscription")
                .convertBodyTo(SubscriptionSetup.class)
                .choice()
                .when(isSiri20ToSiriWS14Subscription)
                .log(LoggingLevel.INFO, "20 to WS 14 Subscription")
                .to("direct:siri.20.to.siri.ws.14.subscription.preprocess")
                .when(isSiri20ToSiriWS14RequestResponse)
                .log(LoggingLevel.INFO, "20 to WS 14 Request Response")
                .to("direct:siri.20.to.siri.ws.14.request-response.preprocess")
                .when(isSiri20ToSiriRS14Subscription)
                .log(LoggingLevel.INFO, "20 to RS 14 Subscription")
                .to("direct:siri.20.to.siri.rs.14.subscription.preprocess")
                .when(isSiri20ToSiriWS20Subscription)
                .log(LoggingLevel.INFO, "20 to WS 20 Subscription")
                .to("direct:siri.20.to.siri.ws.20.subscription.preprocess")
                .when(isSiri20ToSiriWS20RequestResponse)
                .log(LoggingLevel.INFO, "20 to WS 20 Request Response")
                .to("direct:siri.20.to.siri.ws.20.request-response.preprocess")
                .when(isSiri20ToSiriRS20Subscription)
                .log(LoggingLevel.INFO, "20 to RS 20 Subscription")
                .to("direct:siri.20.to.siri.rs.20.subscription.preprocess")
                .when(isSiriLiteToSiriRS20RequestResponse)
                .log(LoggingLevel.INFO, "Lite to RS 20 Request Response")
                .to("direct:siri.lite.to.siri.rs.20.request-response.preprocess")
                .when(isSiri20ToSiriRS20RequestResponse)
                .log(LoggingLevel.INFO, "20 to RS 20 Request Response")
                .to("direct:siri.20.to.siri.rs.20.request-response.preprocess")
                .end()
                .process(IshtarRouteBuilder::process)
                .marshal().json();

        from("direct:discovery.subscription")
                .routeId("discovery.subscription")
                .log(LoggingLevel.INFO, "Discovery Subscription")
                .convertBodyTo(DiscoverySubscription.class)
                .to(SEND_DISCOVERY_REQUEST_PREPROCESS_ROUTE)
                .process(IshtarRouteBuilder::process)
                .marshal().json();

        from(ISHTAR_CLEAR_CACHE_BY_DATASET_ID)
                .routeId(ISHTAR_CLEAR_CACHE_BY_DATASET_ID)
                .process(clearCacheProcessor)
                .end();

    }

}
