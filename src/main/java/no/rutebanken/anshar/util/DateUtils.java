package no.rutebanken.anshar.util;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class DateUtils {

    public static ZonedDateTime convertStringToZonedDateTime(String dateTimeString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
        return ZonedDateTime.parse(dateTimeString, formatter);
    }
}
