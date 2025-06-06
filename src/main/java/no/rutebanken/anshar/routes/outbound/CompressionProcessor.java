package no.rutebanken.anshar.routes.outbound;

import com.aayushatharva.brotli4j.encoder.Encoder;
import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.util.CompressionUtil;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import static org.springframework.http.HttpHeaders.CONTENT_ENCODING;

@Slf4j
@Component
public class CompressionProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        String headerContentEncoding = exchange.getIn().getHeader(CONTENT_ENCODING, String.class);
        if (!CompressionFormat.NONE.getCode().equals(headerContentEncoding)) {
            CompressionFormat compressionFormat = CompressionFormat.valueOfByCode(headerContentEncoding);
            byte[] bodyAsBytes = exchange.getIn().getBody(byte[].class);
            switch (compressionFormat) {
                case GZIP:
                    log.debug("Compressing body with GZIP format");
                    exchange.getIn().setBody(CompressionUtil.gzip(bodyAsBytes));
                    break;
                case DEFLATE:
                    log.debug("Compressing body with deflate format");
                    exchange.getIn().setBody(CompressionUtil.deflateCompress(bodyAsBytes));
                    break;
                case BROTLI:
                    log.debug("Compressing body with BROTLI format");
                    exchange.getIn().setBody(Encoder.compress(bodyAsBytes));
                    break;
                default:
                    log.error("Unable to find valid Content-Encoding");
                    exchange.getIn().removeHeader(CONTENT_ENCODING);
                    break;
            }
        } else {
            log.debug("Removing header Content-Encoding");
            exchange.getIn().removeHeader(CONTENT_ENCODING);
        }
    }
}
