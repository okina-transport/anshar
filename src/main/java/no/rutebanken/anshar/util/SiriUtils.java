package no.rutebanken.anshar.util;


import org.entur.siri.validator.SiriValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SiriUtils {

    private static final Logger logger = LoggerFactory.getLogger(SiriUtils.class);

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
        } else if ("2.0[FR-IDF-2.4]".equals(version)) {
            return SiriValidator.Version.VERSION_2_0_IDFM_2_4;
        }
        logger.error("Unsupported version: {}", version);
        throw new IllegalArgumentException("Unsupported version: " + version);
    }
}
