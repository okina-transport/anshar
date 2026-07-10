/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.util;

import no.rutebanken.anshar.routes.outbound.model.CompressionFormat;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CompressionUtil {
    private static final Logger logger = LoggerFactory.getLogger(CompressionUtil.class);

    private static final int BUFFER_SIZE = 1024;

    private CompressionUtil() {
        //should not be instantiated
    }

    public static byte[] compress(Object o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gzipOut = new GZIPOutputStream(baos);
        ObjectOutputStream objectOut = new ObjectOutputStream(gzipOut);
        objectOut.writeObject(o);
        objectOut.close();

        return baos.toByteArray();
    }

    public static Object decompress(byte[] b) throws IOException {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(b);
        GZIPInputStream gzipIn = new GZIPInputStream(byteArrayInputStream);
        ObjectInputStream in = new ObjectInputStream(gzipIn);
        try {
            return in.readObject();
        } catch (ClassNotFoundException e) {
            logger.warn("Execption caught when decompressing Hazelcast object", e);
        }
        return null;
    }

    public static CompressionFormat getCompressionFormatFromHeader(String headerAcceptEncoding) {
        CompressionFormat result = CompressionFormat.NONE;
        if (StringUtils.isNotBlank(headerAcceptEncoding)) {
            String[] headers = headerAcceptEncoding.split(",");
            for (String header : headers) {
                CompressionFormat parsedCompressionFormat = CompressionFormat.valueOfByCode(header.trim());
                if (parsedCompressionFormat.ordinal() < result.ordinal()) {
                    result = parsedCompressionFormat;
                }
            }
        }
        return result;
    }

    public static byte[] deflateCompress(byte[] input) {
        Deflater deflater = new Deflater();
        deflater.setInput(input);
        deflater.finish();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];

        while (!deflater.finished()) {
            int compressedSize = deflater.deflate(buffer);
            outputStream.write(buffer, 0, compressedSize);
        }

        return outputStream.toByteArray();
    }

}
