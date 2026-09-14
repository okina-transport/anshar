package no.rutebanken.anshar.util;

import lombok.extern.slf4j.Slf4j;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
public class DateUtils {

    public static ZonedDateTime convertStringToZonedDateTime(String dateTimeString) {
        String pattern = dateTimeString.contains(".") ? "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSXXX" : "yyyy-MM-dd'T'HH:mm:ssXXX";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return ZonedDateTime.parse(dateTimeString, formatter);
    }
    
}
