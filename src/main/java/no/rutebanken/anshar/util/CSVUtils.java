package no.rutebanken.anshar.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
public class CSVUtils {
    /**
     * Read a csv file and builds a collection of records
     *
     * @param file the file to read
     * @return a collection of records
     * @throws IOException when opening or parsing input file
     */
    public static Iterable<CSVRecord> getRecords(File file) throws IOException {
        try (InputStream targetStream = new FileInputStream(file)) {
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
    }

    public static Iterable<CSVRecord> getRecordsWithBomHandling(File file) throws IOException {
        try (BOMInputStream bomInputStream = BOMInputStream.builder().setFile(file).get()) {


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

    public static List<CSVRecord> parseCsv(File csvFileFullpath, final Class<? extends Enum<?>> headerEnum,
                                           boolean skipHeaderRecord) throws IOException {
        log.info("Parse CSV from {}", csvFileFullpath.getAbsolutePath());
        try (Reader csvFileReader = new FileReader(csvFileFullpath)) {
            CSVParser csvParser = CSVFormat.RFC4180.builder()
                    .setHeader(headerEnum)
                    .setSkipHeaderRecord(skipHeaderRecord)
                    .get()
                    .parse(csvFileReader);
            List<CSVRecord> records = csvParser.getRecords();
            if (CollectionUtils.isEmpty(records)) {
                log.warn("No CSV records in file {}", csvFileFullpath.getAbsolutePath());
            } else {
                log.info("Parsed {} CSV records in file {}", records.size(), csvFileFullpath.getAbsolutePath());
            }
            return records;
        }
    }
}
