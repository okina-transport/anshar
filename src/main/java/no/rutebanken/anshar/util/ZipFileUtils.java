package no.rutebanken.anshar.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipFileUtils {
    public static ByteArrayOutputStream extractFileFromZipFile(InputStream inputStream) {
        try {
            byte[] buffer = new byte[1024];
            ZipInputStream zis = new ZipInputStream(inputStream, StandardCharsets.UTF_8);
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                ByteArrayOutputStream fos = new ByteArrayOutputStream();
                int length;
                while ((length = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
                fos.close();
                return fos;
            }
        } catch (IOException ioE) {
            throw new RuntimeException("Unzipping archive failed: " + ioE.getMessage(), ioE);
        }
        return null;
    }
}
