package no.rutebanken.anshar.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Slf4j
public class DateUtils {

    public static ZonedDateTime convertStringToZonedDateTime(String dateTimeString) {
        String pattern = dateTimeString.contains(".") ? "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSXXX" : "yyyy-MM-dd'T'HH:mm:ssXXX";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return ZonedDateTime.parse(dateTimeString, formatter);
    }

    public static Optional<ZonedDateTime> convertGtfsTimeToZonedDateTime(String gtfsTime) {
        if (StringUtils.isBlank(gtfsTime)) {
            return Optional.empty();
        }
        String[] splitTime = gtfsTime.split(":");
        if (splitTime.length != 3) {
            log.error("Invalid GTFS time format: {}", gtfsTime);
            return Optional.empty();
        }
        try {
            int secondsFromStartOfDay = 3600 * Integer.parseInt(splitTime[0]) + 60 * Integer.parseInt(splitTime[1]) + Integer.parseInt(splitTime[2]);
            ZonedDateTime startOfDay = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS);
            return Optional.of(startOfDay.plusSeconds(secondsFromStartOfDay));
        } catch (NumberFormatException e) {
            log.error("Invalid GTFS time format: {}", gtfsTime);
            return Optional.empty();
        }
    }

}
