package no.rutebanken.anshar.data.util;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomStringUtils {

    public static final String COLON_REPLACEMENT_CODE = "##3A##";

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

    /**
     * Apply the same transformation as chouette on stop ids (replace ":" by "##3A##")
     *
     * @param input string to process
     * @return the transformed string
     */
    public static String applyChouetteIdTransformation(String input) {
        if (StringUtils.isEmpty(input)) {
            return input;
        }
        return input.replace(":", COLON_REPLACEMENT_CODE);
    }

    /**
     * Revert chouette transformation on stop ids (replace "##3A##" by ":")
     *
     * @param input string to process
     * @return the transformed string
     */
    public static String revertChouetteIdTransformation(String input) {
        if (StringUtils.isEmpty(input)) {
            return input;
        }
        return input.replace(COLON_REPLACEMENT_CODE, ":");
    }

    /**
     * Apply the same transformation as chouette on line ids (replace "##3A##" by "-")
     *
     * @param input string to process
     * @return the transformed string
     */
    public static String applyChouetteLineIdTransformation(String input) {
        if (StringUtils.isEmpty(input)) {
            return input;
        }
        return input.replace(COLON_REPLACEMENT_CODE, "-");
    }

}
