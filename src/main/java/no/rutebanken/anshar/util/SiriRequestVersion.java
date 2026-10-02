package no.rutebanken.anshar.util;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SiriRequestVersion(String requested, String siriVersion, String requestName) {

    private static final Logger logger = LoggerFactory.getLogger(SiriRequestVersion.class);

    public static final String DEFAULT_VERSION = "2.1";

    private static final Set<String> SUPPORTED_SIRI_VERSIONS = Set.of("2.0", "2.1");

    // x.y[letter][:FR-[IDF-]a.b[-c[-d]]]
    private static final Pattern PROFILE_VERSION_PATTERN = Pattern.compile("^(\\d+\\.\\d+)[a-z]?(:FR-(IDF-)?\\d+\\.\\d+(-\\d{1,2}(-[\\d.]+)?)?)?$");

    // Legacy IDFM notation, already used by Anshar for outbound subscriptions (e.g. "2.0[FR-IDF-2.4]")
    private static final Pattern LEGACY_IDFM_VERSION_PATTERN = Pattern.compile("^(\\d+\\.\\d+)\\[FR-IDF-\\d+\\.\\d+]$");

    private static final XMLInputFactory XML_INPUT_FACTORY = createXmlInputFactory();

    public static SiriRequestVersion parse(String requested) {
        return parse(requested, null);
    }

    public static SiriRequestVersion parse(String requested, String requestName) {
        if (StringUtils.isBlank(requested)) {
            return new SiriRequestVersion(DEFAULT_VERSION, DEFAULT_VERSION, requestName);
        }
        String trimmed = requested.trim();
        for (Pattern pattern : new Pattern[]{PROFILE_VERSION_PATTERN, LEGACY_IDFM_VERSION_PATTERN}) {
            Matcher matcher = pattern.matcher(trimmed);
            if (matcher.matches()) {
                return new SiriRequestVersion(trimmed, matcher.group(1), requestName);
            }
        }
        return new SiriRequestVersion(trimmed, null, requestName);
    }

    public static Optional<SiriRequestVersion> fromServiceRequest(byte[] xml) {
        if (xml == null || xml.length == 0) {
            return Optional.empty();
        }
        XMLStreamReader reader = null;
        try {
            reader = XML_INPUT_FACTORY.createXMLStreamReader(new ByteArrayInputStream(xml));
            String rootVersion = null;
            int depth = 0;
            boolean inServiceRequest = false;

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                    continue;
                }
                if (event != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                depth++;
                String name = reader.getLocalName();

                if (depth == 1) {
                    if (!"Siri".equals(name)) {
                        return Optional.empty();
                    }
                    rootVersion = getVersionAttribute(reader);
                } else if (depth == 2) {
                    if (!"ServiceRequest".equals(name)) {
                        return Optional.empty();
                    }
                    inServiceRequest = true;
                } else if (depth == 3 && inServiceRequest && name.endsWith("Request")) {
                    String serviceVersion = getVersionAttribute(reader);
                    return Optional.of(parse(StringUtils.isNotBlank(serviceVersion) ? serviceVersion : rootVersion, name));
                }
            }
            return inServiceRequest ? Optional.of(parse(rootVersion)) : Optional.empty();
        } catch (XMLStreamException e) {
            logger.debug("Unable to read requested SIRI version", e);
            return Optional.empty();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException e) {
                    logger.debug("Unable to close XML reader", e);
                }
            }
        }
    }

    public boolean isSupported() {
        return siriVersion != null && SUPPORTED_SIRI_VERSIONS.contains(siriVersion);
    }

    public boolean isSiri21() {
        return "2.1".equals(siriVersion);
    }

    private static String getVersionAttribute(XMLStreamReader reader) {
        return reader.getAttributeValue(null, "version");
    }

    private static XMLInputFactory createXmlInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory;
    }
}
