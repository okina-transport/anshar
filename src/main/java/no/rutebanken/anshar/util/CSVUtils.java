package no.rutebanken.anshar.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class CSVUtils {
    /**
     * Read a csv file and builds a collection of records
     *
     * @param file the file to read
     * @return a collection of records
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

        InputStream is1 = new ByteArrayInputStream(baos.toByteArray());
        InputStream is2 = new ByteArrayInputStream(baos.toByteArray());
        String result = IOUtils.toString(is1, StandardCharsets.UTF_8);

        String delimiter = guessDelimiter(result);

        Reader reader = new InputStreamReader(is2);

        return CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(false)
                .setDelimiter(delimiter)
                .build()
                .parse(reader);
    }

    public static Iterable<CSVRecord> getRecordsWithBomHandling(File file) throws IOException {
        try (InputStream fileStream = new FileInputStream(file);
             BOMInputStream bomInputStream = new BOMInputStream(fileStream)) {

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = bomInputStream.read(buffer)) > -1) {
                baos.write(buffer, 0, len);
            }
            baos.flush();

            InputStream is1 = new ByteArrayInputStream(baos.toByteArray());
            InputStream is2 = new ByteArrayInputStream(baos.toByteArray());

            String content = IOUtils.toString(is1, StandardCharsets.UTF_8);
            String delimiter = guessDelimiter(content);

            Reader reader = new InputStreamReader(is2, StandardCharsets.UTF_8);

            return CSVFormat.DEFAULT
                    .builder()
                    .setHeader()
                    .setSkipHeaderRecord(false)
                    .setDelimiter(delimiter)
                    .build()
                    .parse(reader);
        }
    }

    private static String guessDelimiter(String fileContent) {

        String[] lines = fileContent.split("\n");
        String firstLine = lines[0];
        long nbOfSemiColon = firstLine.chars()
                .filter(ch -> ch == ';')
                .count();

        long nbOfComma = firstLine.chars()
                .filter(ch -> ch == ',')
                .count();

        return nbOfSemiColon > nbOfComma ? ";" : ",";


    }
}
