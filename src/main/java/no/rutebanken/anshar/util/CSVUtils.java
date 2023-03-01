package no.rutebanken.anshar.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public class CSVUtils {
    /**
     * Read a csv file and builds a collection of records
     * @param file
     *  the file to read
     * @return
     *  a collection of records
     * @throws IOException
     */
    public static Iterable<CSVRecord> getRecords(File file) throws IOException {
        InputStream targetStream = new FileInputStream(file);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = targetStream.read(buffer)) > -1) {
            baos.write(buffer, 0, len);
        }
        baos.flush();

        InputStream is2 = new ByteArrayInputStream(baos.toByteArray());

        Reader reader = new InputStreamReader(is2);

        return CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(false)
                .setDelimiter(";")
                .build()
                .parse(reader);
    }
}
