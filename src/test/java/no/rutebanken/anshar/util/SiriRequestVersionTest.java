package no.rutebanken.anshar.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SiriRequestVersionTest {

    @ParameterizedTest
    @CsvSource(value = {
            "2.1;2.1",
            "2.0;2.0",
            "2.1n;2.1",
            "2.1:FR-1.0;2.1",
            "2.0:FR-1.0-1;2.0",
            "2.1:FR-1.8-2-4.5.1;2.1",
            "2.1:FR-IDF-2.4;2.1",
            "2.0:FR-2.4;2.0",
            "2.0[FR-IDF-2.4];2.0"
    }, delimiter = ';')
    void parse_supportedVersions(String requested, String expectedSiriVersion) {
        SiriRequestVersion version = SiriRequestVersion.parse(requested);

        assertThat(version.requested()).isEqualTo(requested);
        assertThat(version.siriVersion()).isEqualTo(expectedSiriVersion);
        assertThat(version.isSupported()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"n_importe_quoi", "toto", "1.0", "1.3", "1.4", "2.2", "3.0", "2", "2.1:FR", "2.1:XX-1.0", "2.1:FR-1.0-123"})
    void parse_unsupportedVersions(String requested) {
        SiriRequestVersion version = SiriRequestVersion.parse(requested);

        assertThat(version.requested()).isEqualTo(requested);
        assertThat(version.isSupported()).isFalse();
    }

    @Test
    void parse_missingVersion_defaultsTo21() {
        assertThat(SiriRequestVersion.parse(null).requested()).isEqualTo("2.1");
        assertThat(SiriRequestVersion.parse("").isSiri21()).isTrue();
    }

    @Test
    void fromServiceRequest_serviceVersionHasPriorityOverRootVersion() {
        Optional<SiriRequestVersion> version = SiriRequestVersion.fromServiceRequest(sxRequest("version=\"2.0\"", "version=\"toto\""));

        assertThat(version).isPresent();
        assertThat(version.get().requested()).isEqualTo("toto");
    }

    @Test
    void fromServiceRequest_fallsBackOnRootVersion() {
        Optional<SiriRequestVersion> version = SiriRequestVersion.fromServiceRequest(sxRequest("version=\"2.0\"", ""));

        assertThat(version).isPresent();
        assertThat(version.get().requested()).isEqualTo("2.0");
    }

    @Test
    void fromServiceRequest_emptyServiceVersionFallsBackOnRootVersion() {
        Optional<SiriRequestVersion> version = SiriRequestVersion.fromServiceRequest(sxRequest("version=\"2.0\"", "version=\"\""));

        assertThat(version).isPresent();
        assertThat(version.get().requested()).isEqualTo("2.0");
    }

    @Test
    void fromServiceRequest_noVersion_defaultsTo21() {
        Optional<SiriRequestVersion> version = SiriRequestVersion.fromServiceRequest(sxRequest("", ""));

        assertThat(version).isPresent();
        assertThat(version.get().requested()).isEqualTo("2.1");
    }

    @Test
    void fromServiceRequest_notAServiceRequest_isEmpty() {
        String checkStatus = "<Siri xmlns=\"http://www.siri.org.uk/siri\" version=\"toto\"><CheckStatusRequest/></Siri>";

        assertThat(SiriRequestVersion.fromServiceRequest(checkStatus.getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    void fromServiceRequest_malformedXml_isEmpty() {
        String malformed = "<Siri xmlns=\"http://www.siri.org.uk/siri\"><ServiceRequest><broken";

        assertThat(SiriRequestVersion.fromServiceRequest(malformed.getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(SiriRequestVersion.fromServiceRequest(null)).isEmpty();
    }

    private static byte[] sxRequest(String rootVersion, String serviceVersion) {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" " + rootVersion + ">" +
                "<ServiceRequest>" +
                "<RequestTimestamp>2026-10-02T10:00:00+02:00</RequestTimestamp>" +
                "<RequestorRef>test</RequestorRef>" +
                "<SituationExchangeRequest " + serviceVersion + "/>" +
                "</ServiceRequest>" +
                "</Siri>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }
}
