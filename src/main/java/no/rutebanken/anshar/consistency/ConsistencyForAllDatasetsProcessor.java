package no.rutebanken.anshar.consistency;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.routes.kafka.KafkaRouteBuilder;
import no.rutebanken.anshar.subscription.DatasetService;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class ConsistencyForAllDatasetsProcessor implements Processor {

    private final ConsistencyService consistencyService;
    private final ProducerTemplate producerTemplate;
    private final DatasetService datasetService;

    public ConsistencyForAllDatasetsProcessor(ConsistencyService consistencyService, ProducerTemplate producerTemplate, DatasetService datasetService) {
        this.consistencyService = consistencyService;
        this.producerTemplate = producerTemplate;
        this.datasetService = datasetService;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        log.info("Start generation of consistency reports for all datasets");
        for (String dataset : datasetService.getAllDatasetIds()) {
            log.info("Generate consistency report for dataset '{}'", dataset);
            var report = consistencyService.buildReportForDataset(dataset);
            producerTemplate.asyncRequestBody(KafkaRouteBuilder.SEND_TH_TR_CONSISTENCY_REPORT_TO_KAFKA, report);
        }
    }

}
