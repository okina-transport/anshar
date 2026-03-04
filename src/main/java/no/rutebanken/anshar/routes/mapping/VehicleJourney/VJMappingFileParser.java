package no.rutebanken.anshar.routes.mapping.VehicleJourney;


import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.util.MappingUtils;
import no.rutebanken.anshar.util.StringUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.csv.CSVRecord;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static no.rutebanken.anshar.util.CSVUtils.parseCsv;

/**
 * Parse CSV files to map SIRI LineRef, RouteRef and StopRef with SIC Line & Stop recipients.
 */
@Component
@Slf4j
public class VJMappingFileParser {

    private final File vjMappingCsv;


    public VJMappingFileParser(
            @Value("${vj.mapping.csv}") File vjMappingCsv

    ) {
        this.vjMappingCsv = vjMappingCsv;
    }

    /**
     * @return Map -> VehicleJourney mobi-iti id
     * @throws IOException when CSV parsing fails
     */
    public Map<String, VehicleJourney> parseVjMappingCsv() throws IOException {
        List<CSVRecord> records = parseCsv(vjMappingCsv, VJMappingHeaders.class, true);
        if (CollectionUtils.isEmpty(records)) {
            log.error("{} is empty, abort", vjMappingCsv.getName());
            return Map.of();
        }
        Map<String, VehicleJourney> result = new HashMap<>();
        for (CSVRecord r : records) {
            String vehicleJourneyId = r.get(VJMappingHeaders.VEHICLE_JOURNEY_ID);
            String directionName = r.get(VJMappingHeaders.ROUTE_DIRECTION);
            String dateyyyyMMdd = r.get(VJMappingHeaders.DATE_YYYYMMDD);
            String timeHHmmss = r.get(VJMappingHeaders.TIME_HHMMSS);
            String datasetId = r.get(VJMappingHeaders.DATASET_ID);
            VehicleJourney vj = new VehicleJourney(vehicleJourneyId, dateyyyyMMdd, timeHHmmss, Integer.parseInt(r.get(VJMappingHeaders.POSITION)));
            String key = MappingUtils.buildIneoVJKey(
                    dateyyyyMMdd,
                    timeHHmmss,
                    r.get(VJMappingHeaders.LINE_NUMBER),
                    directionName,
                    r.get(VJMappingHeaders.ORIGINAL_STOP_ID),
                    datasetId);
            result.put(key, vj);
            key = MappingUtils.buildIneoVJKey(
                    dateyyyyMMdd,
                    timeHHmmss,
                    r.get(VJMappingHeaders.LINE_NUMBER),
                    directionName,
                    r.get(VJMappingHeaders.ORIGINAL_PARENT_STOP_ID),
                    datasetId);
            result.put(key, vj);
        }
        return result;
    }


    public enum VJMappingHeaders {
        DATE_YYYYMMDD,
        TIME_HHMMSS,
        LINE_NUMBER,
        ROUTE_DIRECTION,
        ORIGINAL_STOP_ID,
        ORIGINAL_PARENT_STOP_ID,
        VEHICLE_JOURNEY_ID,
        POSITION,
        DATASET_ID

    }



    /**
     * @param dateyyyyMMdd  date in format yyyyMMdd
     * @param timeHHmmss    time in format HHmmss
     * @param lineNumber    line number
     * @param directionName A (= allée) or R (= retour)
     * @param ineoStopId    INEO message stop id
     * @return {dateyyyyMMdd},{timeHHmmss},{lineNumber},{directionName},{ineoStopId}
     */
    public static String buildIneoVJKey(String dateyyyyMMdd, String timeHHmmss, String lineNumber,
                                        String directionName, String ineoStopId) {
        return String.format("%s,%s,%s,%s,%s",
                dateyyyyMMdd,
                timeHHmmss,
                StringUtils.removeLeadingZeros(lineNumber),
                directionName,
                ineoStopId);
    }

}
