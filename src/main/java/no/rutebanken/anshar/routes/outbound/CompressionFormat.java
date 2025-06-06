package no.rutebanken.anshar.routes.outbound;

import lombok.Getter;

@Getter
public enum CompressionFormat {
    BROTLI("br"),
    GZIP("gzip"),
    DEFLATE("deflate"),
    NONE("none");

    private final String code;

    CompressionFormat(String code) {
        this.code = code;
    }

    public static CompressionFormat valueOfByCode(String headerAcceptEncoding) {
        for (CompressionFormat compressionFormat : CompressionFormat.values()) {
            if (compressionFormat.getCode().equals(headerAcceptEncoding)) {
                return compressionFormat;
            }
        }
        return NONE;
    }

}
