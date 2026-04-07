package no.rutebanken.anshar.routes.siri.theoretical;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.camel.CamelContext;
import org.awaitility.Durations;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class TheoreticalSiriSmConsumerTest extends SpringBootBaseTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private TheoreticalSiriSmConsumer consumer;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @BeforeAll
    static void setUp() throws Exception {
        updateInputCsvFile();
    }

    @AfterAll
    static void tearDown() throws Exception {
        removeTheoreticalCsvFile();
    }

    @BeforeEach
    void beforeEach() {
        subscriptionConfig.getIdProcessingParameters().clear();
        IdProcessingParameters vjIdProcessing = new IdProcessingParameters();
        vjIdProcessing.setInputPrefixToRemove("TEST:VehicleJourney:");
        vjIdProcessing.setDatasetId("TEST");
        vjIdProcessing.setObjectType(ObjectType.VEHICLE_JOURNEY);
        subscriptionConfig.getIdProcessingParameters().add(vjIdProcessing);
        IdProcessingParameters stopIdProcessing = new IdProcessingParameters();
        stopIdProcessing.setInputPrefixToRemove("TEST:Quay:");
        stopIdProcessing.setDatasetId("TEST");
        stopIdProcessing.setObjectType(ObjectType.STOP);
        subscriptionConfig.getIdProcessingParameters().add(stopIdProcessing);
        IdProcessingParameters lineIdProcessing = new IdProcessingParameters();
        lineIdProcessing.setInputPrefixToRemove("TEST:Line:");
        lineIdProcessing.setDatasetId("TEST");
        lineIdProcessing.setObjectType(ObjectType.LINE);
        subscriptionConfig.getIdProcessingParameters().add(lineIdProcessing);
        subscriptionManager.clearAllSubscriptions();
    }

    @Test
    void readDataAndProduceSiriTest() {
        camelContext.start();

        consumer.ingestSiriSmData();

        await()
                .atMost(Durations.TEN_SECONDS)
                .with()
                .pollInterval(Durations.ONE_SECOND)
                .until(() -> subscriptionManager.getAllSubscriptions(SiriDataType.STOP_MONITORING).size() == 20);

        assertThat(subscriptionManager.getAllSubscriptions(SiriDataType.STOP_MONITORING))
                .extracting("datasetId").containsOnly("TEST");
        assertThat(subscriptionManager.getAllSubscriptions(SiriDataType.STOP_MONITORING))
                .flatExtracting("stopMonitoringRefValues").contains(
                        "TEST:Quay:44708",
                        "TEST:Quay:44089",
                        "TEST:Quay:44157",
                        "TEST:Quay:44063",
                        "TEST:Quay:44034",
                        "TEST:Quay:44082",
                        "TEST:Quay:44158",
                        "TEST:Quay:44005",
                        "TEST:Quay:44505",
                        "TEST:Quay:44087",
                        "TEST:Quay:44205",
                        "TEST:Quay:44127",
                        "TEST:Quay:44068",
                        "TEST:Quay:44013",
                        "TEST:Quay:44004",
                        "TEST:Quay:44036",
                        "TEST:Quay:44032",
                        "TEST:Quay:44802",
                        "TEST:Quay:44616",
                        "TEST:Quay:44054");
    }

    static void updateInputCsvFile() throws Exception {
        Path templateFile = Paths.get("src/test/resources/theoretical-data/TEST_th_sm_template.csv");
        Path copy = Paths.get("src/test/resources/theoretical-data/TEST_th_sm.csv");
        List<String> modifiedLines = new ArrayList<>();
        List<String> templateLines = Files.readAllLines(templateFile);

        for (int i = 0; i < templateLines.size(); i++) {
            String line = templateLines.get(i);

            if (i == 0) {
                modifiedLines.add(line);
            } else {
                String[] parts = line.split(",");
                parts[0] = LocalDate.now().format(DATE_FORMATTER);
                modifiedLines.add(String.join(",", parts));
            }
        }

        Files.write(copy, modifiedLines, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }


    static void removeTheoreticalCsvFile() throws IOException {
        Path csvFilePath = Paths.get("src/test/resources/theoretical-data/TEST_th_sm.csv");
        Files.delete(csvFilePath);
    }
}