package no.rutebanken.anshar.util;

import org.apache.commons.lang3.Strings;

public class StringUtils {

    public static String removeLeadingZeros(String input) {
        if (org.apache.commons.lang3.StringUtils.isEmpty(input)) {
            return input;
        }
        return input.replaceFirst("^0+(?!$)", "");
    }


    public static String extractIdFromObjectId(String objectId) {
        objectId = Strings.CS.removeEnd(objectId, ":LOC");
        String[] split = objectId.split(":");
        if (split.length == 1) {
            // id is raw
            return objectId;
        } else if (split.length == 3) {
            // id format is {ref}:{type}:{id}
            return split[2];
        }
        throw new IllegalArgumentException("Invalid originalId format: " + objectId);
    }
}
