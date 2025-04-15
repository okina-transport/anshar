package no.rutebanken.anshar.routes.mapping;

import no.rutebanken.anshar.util.CSVUtils;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.FirstOrLastJourneyEnumeration;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service to handle first or last VJ of the day
 */
@Component
@Configuration
public class VehicleJourneyService {

    private static final Logger logger = LoggerFactory.getLogger(VehicleJourneyService.class);
    private static final Object LOCK = new Object();
    private final Set<String> vehicleJourneyMobiitiIds = new HashSet<>();
    private final Map<LocalDate, Map<String, FirstOrLastJourneyEnumeration>> servicePositionMap = new HashMap<>();
    @Value("${anshar.first.or.last.vj.file}")
    private String firstOrLastVehicleJourneyFile;
    @Value("${anshar.line.ids.update.frequency.hours:10}")
    private int updateFrequency = 10;

    @PostConstruct
    private void initialize() {

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        executor.scheduleAtFixedRate(this::updateFirstOrLastVJ, 0, updateFrequency, TimeUnit.HOURS);

        logger.info("Initialized first-or-last-VJ-updater with urls:{}, updateFrequency:{} hours", new String[]{firstOrLastVehicleJourneyFile}, updateFrequency);
    }

    private void updateFirstOrLastVJ() {
        // re-entrant
        synchronized (LOCK) {
            try {
                updateLineIdMapping(firstOrLastVehicleJourneyFile);
            } catch (IOException e) {
                logger.error("Failed to update first-or-last-VJ", e);
            }
        }
    }

    private void updateLineIdMapping(String firstOrLastVehicleJourneyFile) throws IOException {
        logger.info("Fetching VJ data - start. Fetching VJ from {}", firstOrLastVehicleJourneyFile);
        long t1 = System.currentTimeMillis();
        File fileToRead = new File(firstOrLastVehicleJourneyFile);
        Iterable<CSVRecord> records = CSVUtils.getRecords(fileToRead);
        servicePositionMap.clear();
        vehicleJourneyMobiitiIds.clear();
        for (CSVRecord record : records) {

            String lineId = record.isSet("lineId") ? record.get("lineId") : null;
            LocalDate date = record.isSet("dateyyyyMMdd") ? LocalDate.parse(record.get("dateyyyyMMdd"), DateTimeFormatter.ofPattern("yyyyMMdd")) : null;
            String vehicleJourneyId = record.isSet("vehicleJourneyId") ? record.get("vehicleJourneyId") : null;
            FirstOrLastJourneyEnumeration servicePosition = record.isSet("servicePosition") ? FirstOrLastJourneyEnumeration.fromValue(record.get("servicePosition")) : null;

            if (lineId == null || vehicleJourneyId == null || servicePosition == null || date == null) {
                logger.warn("Missing field in VJ data : " + lineId + " / " + vehicleJourneyId + " / " + servicePosition + " / " + date);
                continue;
            }

            vehicleJourneyMobiitiIds.add(vehicleJourneyId);
            Map<String, FirstOrLastJourneyEnumeration> dayMap;

            if (servicePositionMap.containsKey(date)) {
                dayMap = servicePositionMap.get(date);
            } else {
                dayMap = new HashMap<>();
                servicePositionMap.put(date, dayMap);
            }
            dayMap.put(vehicleJourneyId, servicePosition);
        }
        logger.info("VJ data completed. Size:" + servicePositionMap.size());

        for (Map.Entry<LocalDate, Map<String, FirstOrLastJourneyEnumeration>> servicePosEntry : servicePositionMap.entrySet()) {
            logger.info("VJ data date:" + servicePosEntry.getKey() + ", size:" + servicePosEntry.getValue().size());
        }

    }

    /**
     * Return the service position of the vehicle journey
     * - firstServiceOfDay : first vehicle journey of the day, for a specific line
     * - lastServiceOfDay : last service journey of the day, for a specific line
     * -
     *
     * @param date
     * @param vehicleJourneyId
     * @return
     */
    public FirstOrLastJourneyEnumeration getServicePosition(LocalDate date, String vehicleJourneyId) {
        if (!servicePositionMap.containsKey(date) || !servicePositionMap.get(date).containsKey(vehicleJourneyId)) {

            if (!servicePositionMap.containsKey(date)) {
                logger.debug("First-or-last-VJ date not found:" + date.toString());
                return FirstOrLastJourneyEnumeration.UNSPECIFIED;
            }

            if (!servicePositionMap.get(date).containsKey(vehicleJourneyId)) {
                logger.debug("First-or-last-VJ vj not found:" + vehicleJourneyId);
                return FirstOrLastJourneyEnumeration.UNSPECIFIED;
            }

        }
        return servicePositionMap.get(date).get(vehicleJourneyId);
    }

    /**
     * @param vehicleJourneyMobiitiId vehicle journey id to check existence from
     * @return true if vehicle journey id belongs to Mobi-iti referential
     */
    public boolean exists(String vehicleJourneyMobiitiId) {
        if (!vehicleJourneyMobiitiId.endsWith(":LOC")) {
            vehicleJourneyMobiitiId += ":LOC";
        }
        return vehicleJourneyMobiitiIds.contains(vehicleJourneyMobiitiId);
    }

}
