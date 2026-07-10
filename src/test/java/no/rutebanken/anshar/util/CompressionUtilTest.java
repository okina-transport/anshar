package no.rutebanken.anshar.util;

import no.rutebanken.anshar.routes.outbound.model.CompressionFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CompressionUtilTest {

    @ParameterizedTest
    @CsvSource(value = {
            ";NONE",
            "toto;NONE",
            "gzip;GZIP",
            "br;BROTLI",
            "deflate;DEFLATE",
            "gzip,deflate;GZIP",
            "br,gzip;BROTLI",
            "deflate,br,gzip;BROTLI",
            "deflate,br,gzip,toto;BROTLI"
    }, delimiter = ';')
    @DisplayName("Content-Encoding header priority is br > gzip > deflate > other values")
    void getCompressionFormatFromHeader_singleValue_test(String input, CompressionFormat expected) {
        CompressionFormat result = CompressionUtil.getCompressionFormatFromHeader(input);

        assertThat(result).isEqualTo(expected);
    }


}