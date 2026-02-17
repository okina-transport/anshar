package no.rutebanken.anshar.outbound;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.idTests.TestUtils;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.outbound.OutboundErrorHandler;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;


import java.util.Map;

import static no.rutebanken.anshar.routes.validation.validators.Constants.ENDPOINT_HEADER_NAME;

@Slf4j
public class OutboundErrorHandlerTest extends SpringBootBaseTest implements CamelContextAware {


    @Autowired
    private OutboundErrorHandler outboundErrorHandler;

    @Autowired
    private ServerSubscriptionManager subscriptionManager;

    private CamelContext camelContext;

    private static final String WRONG_URL = "https://www.test.com/test";

    @Test
    void test_that_errors_are_correctly_counted() throws Exception {
        outboundErrorHandler.clearOutboundErrors();
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getIn().setHeader(ENDPOINT_HEADER_NAME, WRONG_URL);
        outboundErrorHandler.recordError(exchange);

        Map<String, Integer> errorMap = outboundErrorHandler.getOutboundErrorCount();
        Assertions.assertEquals(1, errorMap.size());
        Assertions.assertTrue(errorMap.containsKey(WRONG_URL));
        Assertions.assertEquals(1, errorMap.get(WRONG_URL));

        for (int i = 1; i <= 20; i++) {
            outboundErrorHandler.recordError(exchange);
        }

        Assertions.assertEquals(21, errorMap.get(WRONG_URL));
    }

    @Test
    void test_that_subscriptions_removed_after_banishment() throws Exception {

        String goodURL = "http://goodURL.com";

        // creating a subcription with a wrong url
        OutboundSubscriptionSetup wrongUrlSub = TestUtils.createSxOutboundSubscription(true, WRONG_URL, "id1");
        subscriptionManager.addSubscription(wrongUrlSub);

        // and one with a good url
        OutboundSubscriptionSetup goodUrlSub = TestUtils.createSxOutboundSubscription(true, goodURL, "id2");
        subscriptionManager.addSubscription(goodUrlSub);

        Assertions.assertEquals(2, subscriptionManager.getAllSubscriptions(SiriDataType.SITUATION_EXCHANGE).size());


        // recording errors for the wrong url, to be banished
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(ENDPOINT_HEADER_NAME, WRONG_URL);
        outboundErrorHandler.recordError(exchange);

        Map<String, Integer> errorMap = outboundErrorHandler.getOutboundErrorCount();
        Assertions.assertEquals(1, errorMap.size());
        Assertions.assertTrue(errorMap.containsKey(WRONG_URL));
        Assertions.assertEquals(1, errorMap.get(WRONG_URL));

        for (int i = 1; i <= 20; i++) {
            outboundErrorHandler.recordError(exchange);
        }

        Assertions.assertEquals(21, errorMap.get(WRONG_URL));

        // subscription with wrong url has been banished
        Assertions.assertEquals(1, subscriptionManager.getAllSubscriptions(SiriDataType.SITUATION_EXCHANGE).size());
        Assertions.assertEquals(goodURL, subscriptionManager.getAllSubscriptions(SiriDataType.SITUATION_EXCHANGE).getFirst().getAddress());
    }

    @Test
    void test_reset_by_exchange() throws Exception {
        outboundErrorHandler.clearOutboundErrors();
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getIn().setHeader(ENDPOINT_HEADER_NAME, WRONG_URL);
        outboundErrorHandler.recordError(exchange);

        Map<String, Integer> errorMap = outboundErrorHandler.getOutboundErrorCount();
        Assertions.assertEquals(1, errorMap.size());
        Assertions.assertTrue(errorMap.containsKey(WRONG_URL));
        Assertions.assertEquals(1, errorMap.get(WRONG_URL));

        for (int i = 1; i <= 20; i++) {
            outboundErrorHandler.recordError(exchange);
        }

        Assertions.assertEquals(21, errorMap.get(WRONG_URL));

        outboundErrorHandler.resetCount(exchange);
        Assertions.assertFalse(errorMap.containsKey(WRONG_URL));
    }

    @Test
    void test_reset_by_url() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getIn().setHeader(ENDPOINT_HEADER_NAME, WRONG_URL);
        outboundErrorHandler.recordError(exchange);

        Map<String, Integer> errorMap = outboundErrorHandler.getOutboundErrorCount();
        Assertions.assertEquals(1, errorMap.size());
        Assertions.assertTrue(errorMap.containsKey(WRONG_URL));
        Assertions.assertEquals(1, errorMap.get(WRONG_URL));

        for (int i = 1; i <= 20; i++) {
            outboundErrorHandler.recordError(exchange);
        }

        Assertions.assertEquals(21, errorMap.get(WRONG_URL));

        outboundErrorHandler.resetCount(WRONG_URL);
        Assertions.assertFalse(errorMap.containsKey(WRONG_URL));
    }

    @Test
    void test_is_allowed() throws Exception {
        outboundErrorHandler.clearOutboundErrors();
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getIn().setHeader(ENDPOINT_HEADER_NAME, WRONG_URL);
        outboundErrorHandler.recordError(exchange);

        Map<String, Integer> errorMap = outboundErrorHandler.getOutboundErrorCount();
        Assertions.assertEquals(1, errorMap.size());
        Assertions.assertTrue(errorMap.containsKey(WRONG_URL));
        Assertions.assertEquals(1, errorMap.get(WRONG_URL));

        // only one error, url is allowed
        Assertions.assertTrue(outboundErrorHandler.isUrlAllowed(WRONG_URL));

        outboundErrorHandler.recordError(exchange);
        // 2 errors, url is still allowed
        Assertions.assertTrue(outboundErrorHandler.isUrlAllowed(WRONG_URL));

        outboundErrorHandler.recordError(exchange);
        // 3 errors, now url is banned
        Assertions.assertFalse(outboundErrorHandler.isUrlAllowed(WRONG_URL));
    }

    @Test
    void test_clear_all() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getIn().setHeader(ENDPOINT_HEADER_NAME, WRONG_URL);
        outboundErrorHandler.recordError(exchange);

        String otherURL = "http://www.other-url.com";
        Exchange exchange2 = new DefaultExchange(camelContext);
        exchange2.getIn().setHeader(ENDPOINT_HEADER_NAME, otherURL);
        outboundErrorHandler.recordError(exchange2);

        Map<String, Integer> errorMap = outboundErrorHandler.getOutboundErrorCount();
        Assertions.assertEquals(2, errorMap.size());
        Assertions.assertTrue(errorMap.containsKey(WRONG_URL));
        Assertions.assertEquals(1, errorMap.get(WRONG_URL));
        Assertions.assertEquals(1, errorMap.get(otherURL));

        // clearing all errors
        outboundErrorHandler.clearOutboundErrors();
        Assertions.assertTrue(errorMap.isEmpty());
    }


    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
}
