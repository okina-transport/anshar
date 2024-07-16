package no.rutebanken.anshar.data.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomStringUtils {

    public static String removeSpecialCharacters(String input, String objectType) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String patternString = "(.*:" + objectType + ":)(.*)(:LOC)";

        Pattern pattern = Pattern.compile(Pattern.quote(patternString));
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            String prefix = matcher.group(1);
            String extracted = matcher.group(2);
            String suffix = matcher.group(3);
            String modified = removeSpecialCharacters(extracted);
            return prefix + modified + suffix;
        } else {
            return input;
        }
    }

    public static String removeSpecialCharacters(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        return input.replace(":", "-").replace("|", "_");
    }


}
