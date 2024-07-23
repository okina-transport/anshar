package no.rutebanken.anshar.util;

import org.entur.siri.validator.SiriValidator;

public class SiriUtils {

    public static SiriValidator.Version getVersionEnum(String version) {
        if ("1.0".equals(version)) {
            return SiriValidator.Version.VERSION_1_0;
        } else if ("1.3".equals(version)) {
            return SiriValidator.Version.VERSION_1_3;
        } else if ("1.4".equals(version)) {
            return SiriValidator.Version.VERSION_1_4;
        } else if ("2.0".equals(version)) {
            return SiriValidator.Version.VERSION_2_0;
        } else if ("2.1".equals(version)) {
            return SiriValidator.Version.VERSION_2_1;
        }
        throw new IllegalArgumentException("Unsupported version: " + version);
    }
}
