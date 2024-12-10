package no.rutebanken.anshar.siri;

import no.rutebanken.anshar.helpers.TestObjectFactory;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import org.apache.camel.Endpoint;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.Siri;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager.CODESPACE_ID_KAFKA_HEADER_NAME;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CountOutgoingDataPerformanceTest extends SpringBootBaseTest {


    public static final int NB_OF_SIRI_TO_SEND = 100000;

    @Produce(uri = "direct:send.to.external.subscription.part1.1")
    protected ProducerTemplate countOutgoingData;

    @Autowired
    private PrometheusMetricsService prometheusMetricsService;

    @Test
    public void testCountOutgoingDataPerformance() throws InterruptedException {
        Siri siriToSend = TestObjectFactory.createRandomSMDelivery(4);
        Endpoint defaultEndPoint = countOutgoingData.getDefaultEndpoint();

        ThreadPoolExecutor executorService = (ThreadPoolExecutor) Executors.newFixedThreadPool(1000);

        for (int i = 0; i < NB_OF_SIRI_TO_SEND; i++) {

            executorService.submit(() -> {
                countOutgoingData.asyncRequestBodyAndHeader(defaultEndPoint, siriToSend, CODESPACE_ID_KAFKA_HEADER_NAME, "DAT1");
            });
        }

        while (executorService.getActiveCount() > 0) {
            Thread.sleep(1000);
        }

        System.out.println("active : " + executorService.getActiveCount());


        String res = prometheusMetricsService.scrape();

        String[] resTab = res.split("\n");
        String secondsSumStr = Arrays.stream(resTab).filter(
                line -> line.contains("camel_route_policy_seconds_sum") && line.contains("send.to.external.subscription.part1.1")
        ).findFirst().get();

        String secondsCountStr = Arrays.stream(resTab).filter(
                line -> line.contains("camel_route_policy_seconds_count") && line.contains("send.to.external.subscription.part1.1")
        ).findFirst().get();

        String outboundTotal = Arrays.stream(resTab).filter(
                line -> line.contains("app_anshar_data_outbound_total") && line.contains("emptyRequestorRef")
        ).findFirst().get();

        Double secondsSumValue = extractValue(secondsSumStr);
        Double secondsCountValue = extractValue(secondsCountStr);

        System.out.println("secondsSumValue : " + secondsSumValue);
        System.out.println("secondsCountValue : " + secondsCountValue);
        double avg = secondsSumValue / secondsCountValue * 1000;
        System.out.println("avg : " + avg);
        System.out.println("outboundTotal : " + outboundTotal);
        assertTrue(avg < 10, "Average time should be less than 10ms");

    }

    private double extractValue(String line) {
        return Double.valueOf(line.split(" ")[1]);
    }
}
