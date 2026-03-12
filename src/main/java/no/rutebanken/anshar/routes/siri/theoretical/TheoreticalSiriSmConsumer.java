package no.rutebanken.anshar.routes.siri.theoretical;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.MonitoredStopVisit;
import uk.org.siri.siri20.ServiceDelivery;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.StopMonitoringDeliveryStructure;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class TheoreticalSiriSmConsumer {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");
    private static final String DATASET_ID_HEADER_NAME = "datasetId";
    private static final String URL_HEADER_NAME = "URL";

    private final CsvDataToSiriConverter csvDataToSiriConverter;

    private final SubscriptionConfig subscriptionConfig;

    @Value("${anshar.generate-siri-from-th.directory}")
    private String dataDirectoryPath;

    @Value("${anshar.generate-siri-from-th.file-suffix}")
    private String fileSuffix;


    @Value("${anshar.generate-siri-from-th.datasets:}")
    private List<String> siriFromThDatasets;

    @Produce("direct:send.sm.from.th.to.realtime.server")
    protected ProducerTemplate smFromTheoreticalDataProducer;

    @Autowired
    private TaskScheduler taskScheduler;

    public TheoreticalSiriSmConsumer(CsvDataToSiriConverter csvDataToSiriConverter, SubscriptionConfig subscriptionConfig) {
        this.csvDataToSiriConverter = csvDataToSiriConverter;
        this.subscriptionConfig = subscriptionConfig;
    }

    public void ingestSiriSmData() {
        log.info("starting ingest siri sm TH data");
        if (StringUtils.isBlank(dataDirectoryPath)) {
            log.error("No directory specified");
            return;
        }
        Path directoryPath = Paths.get(dataDirectoryPath);
        try (Stream<Path> paths = Files.list(directoryPath)) {
            paths.filter(path -> Strings.CS.endsWith(path.getFileName().toString(), fileSuffix))
                    .forEach(this::ingestSiriSmDataByDataset);
        } catch (IOException e) {
            log.error("Error parsing siri sm data csv files", e);
        }

    }


    private void ingestSiriSmDataByDataset(Path path) {
        String datasetId = Strings.CS.removeEnd(path.getFileName().toString(), fileSuffix).toUpperCase();
        if (!isAllowedToBuildSmFromTH(datasetId)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(path);
             CSVParser csvParser = CSVParser.parse(reader,
                     CSVFormat.DEFAULT.builder()
                             .setDelimiter(',')
                             .setHeader()
                             .setSkipHeaderRecord(true)
                             .get())) {

            if (subscriptionConfig.getIdParametersForDataset(datasetId, ObjectType.STOP).isEmpty()) {
                log.info("idProcessing not existing for datasetId :{}. Trying again in 30s", datasetId);
                taskScheduler.schedule(() -> this.ingestSiriSmDataByDataset(path), Instant.now().plusSeconds(30));
                return;
            }

            List<TheoreticalStopMonitoringInfo> ingestedData = new ArrayList<>();
            TheoreticalStopMonitoringInfo monitoringInfo;
            IdProcessingParameters emptyIdProcessing = new IdProcessingParameters();
            IdProcessingParameters lineIdProcessingParameters = subscriptionConfig.getIdParametersForDataset(datasetId, ObjectType.LINE).orElse(emptyIdProcessing);
            IdProcessingParameters stopIdProcessingParameters = subscriptionConfig.getIdParametersForDataset(datasetId, ObjectType.STOP).orElse(emptyIdProcessing);
            IdProcessingParameters vehicleJourneyIdProcessingParameters = subscriptionConfig.getIdParametersForDataset(datasetId, ObjectType.VEHICLE_JOURNEY).orElse(emptyIdProcessing);

            if (StringUtils.isNotEmpty(vehicleJourneyIdProcessingParameters.getOutputPrefixToAdd())) {
                IdProcessingParameters replacedIdProcessing = new IdProcessingParameters();
                replacedIdProcessing.setObjectType(ObjectType.VEHICLE_JOURNEY);
                replacedIdProcessing.setDatasetId(vehicleJourneyIdProcessingParameters.getDatasetId());
                replacedIdProcessing.setInputPrefixToRemove(vehicleJourneyIdProcessingParameters.getInputPrefixToRemove());
                replacedIdProcessing.setInputSuffixToRemove(vehicleJourneyIdProcessingParameters.getInputSuffixToRemove());
                replacedIdProcessing.setOutputPrefixToAdd(vehicleJourneyIdProcessingParameters.getOutputPrefixToAdd().replace(":ServiceJourney:", ":VehicleJourney:"));
                replacedIdProcessing.setOutputSuffixToAdd(vehicleJourneyIdProcessingParameters.getOutputSuffixToAdd());
                vehicleJourneyIdProcessingParameters = replacedIdProcessing;
            }


            for (CSVRecord csvRecord : csvParser) {

                if (!LocalDate.now().equals(LocalDate.parse(csvRecord.get("dateyyyyMMdd"), DATE_FORMATTER))) {
                    continue;
                }


                monitoringInfo = TheoreticalStopMonitoringInfo.builder()
                        .date(LocalDate.parse(csvRecord.get("dateyyyyMMdd"), DATE_FORMATTER))
                        .monitoringRef(stopIdProcessingParameters.revertTransformationToString(csvRecord.get("monitoringRef")))
                        .stopPointName(csvRecord.get("stopPointName"))
                        .monitoredVehicleJourneyRef(vehicleJourneyIdProcessingParameters.removeOutputPrefixAndSuffix(csvRecord.get("monitoredVehicleJourneyRef")))
                        .lineRef(lineIdProcessingParameters.revertTransformationToString(csvRecord.get("lineRef")))
                        .publishedLineName(csvRecord.get("publishedLineName"))
                        .directionName(csvRecord.get("directionName"))
                        .aimedDepartureTime(LocalTime.parse(csvRecord.get("aimedDepartureTime"), HOUR_FORMATTER))
                        .aimedArrivalTime(LocalTime.parse(csvRecord.get("aimedArrivalTime"), HOUR_FORMATTER))
                        .originRef(csvRecord.get(stopIdProcessingParameters.removeOutputPrefixAndSuffix("originRef")))
                        .originName(csvRecord.get("originName"))
                        .destinationRef(csvRecord.get(stopIdProcessingParameters.removeOutputPrefixAndSuffix("destinationRef")))
                        .destinationName(csvRecord.get("destinationName"))
                        .build();
                ingestedData.add(monitoringInfo);
            }
            if (CollectionUtils.isNotEmpty(ingestedData)) {
                List<MonitoredStopVisit> monitoredStopVisits = ingestedData
                        .stream()
                        .map(csvDataToSiriConverter::mapToStopVisit)
                        .collect(Collectors.toList());
                List<Siri> siriMessage = splitByMonitoringRef(monitoredStopVisits);
                for (Siri siri : siriMessage) {
                    sendSMToRealTimeServer(siri, datasetId.toUpperCase());
                }

            }
        } catch (IOException e) {
            log.error("Error while reading theoretical siri sm data file : {}", e.getMessage());
        }
    }

    private boolean isAllowedToBuildSmFromTH(String datasetId) {
        return CollectionUtils.isEmpty(siriFromThDatasets) || siriFromThDatasets.stream()
                .anyMatch(current -> Strings.CI.equals(current, datasetId));
    }

    private List<Siri> splitByMonitoringRef(List<MonitoredStopVisit> visitsToSort) {

        Map<String, List<MonitoredStopVisit>> stopVisitsByRef = new HashMap<>();
        List<Siri> results = new ArrayList<>();

        for (MonitoredStopVisit monitoredStopVisit : visitsToSort) {
            String monitoringRef = monitoredStopVisit.getMonitoringRef().getValue();

            if (stopVisitsByRef.containsKey(monitoringRef)) {
                stopVisitsByRef.get(monitoringRef).add(monitoredStopVisit);
            } else {
                List<MonitoredStopVisit> monitoredStopVisits = new ArrayList<>();
                monitoredStopVisits.add(monitoredStopVisit);
                stopVisitsByRef.put(monitoringRef, monitoredStopVisits);
            }
        }

        for (List<MonitoredStopVisit> visits : stopVisitsByRef.values()) {
            Siri siri = new Siri();
            ServiceDelivery delivery = new ServiceDelivery();
            StopMonitoringDeliveryStructure smDeliveryStruct = new StopMonitoringDeliveryStructure();
            smDeliveryStruct.getMonitoredStopVisits().addAll(visits);
            delivery.getStopMonitoringDeliveries().add(smDeliveryStruct);
            siri.setServiceDelivery(delivery);
            results.add(siri);
        }
        return results;
    }


    private void sendSMToRealTimeServer(Siri siriToSend, String datasetId) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(DATASET_ID_HEADER_NAME, datasetId);
        headers.put(URL_HEADER_NAME, datasetId);
        smFromTheoreticalDataProducer.asyncRequestBodyAndHeaders(smFromTheoreticalDataProducer.getDefaultEndpoint(), siriToSend, headers);
    }

}
