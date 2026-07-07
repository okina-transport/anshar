package no.rutebanken.anshar.routes.mapping;

import no.rutebanken.anshar.routes.export.file.BlobStoreService;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Configuration
public class LineUpdaterService {
    private static final Logger logger = LoggerFactory.getLogger(LineUpdaterService.class);
    private static final Object LOCK = new Object();
    private final Map<String, String> lineNameMap = new HashMap<>();
    private final Map<String, String> lineNumberMap = new HashMap<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    @Autowired
    BlobStoreService blobStoreService;
    @Value("${anshar.lineIds.file}")
    private String lineIdsPath;
    @Value("${anshar.line.ids.update.frequency.minutes:10}")
    private int updateFrequency = 10;

    @PostConstruct
    private void initialize() {
        executor.scheduleAtFixedRate(this::updateLineIds, 0, updateFrequency, TimeUnit.MINUTES);
        logger.info("Initialized line-ids-updater with urls:{}, updateFrequency:{} minutes", new String[]{lineIdsPath}, updateFrequency);
    }

    @PreDestroy
    private void destroy() {
        logger.info("Destroy LineUpdaterService");
        executor.shutdown();
    }

    private void updateLineIds() {
        // re-entrant
        synchronized (LOCK) {
            updateLineIdMapping(lineIdsPath);
        }
    }

    private void updateLineIdMapping(String lineIdsPath) {
        logger.info("Fetching line id data - start. Fetching line id from {}", lineIdsPath);
        long t1 = System.currentTimeMillis();

        final InputStream blob = blobStoreService.getBlob(lineIdsPath);

        if (blob != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(blob));

            reader.lines().forEach(line -> {
                StringTokenizer tokenizer = new StringTokenizer(line, ",");
                String lineId = tokenizer.nextToken();
                if (tokenizer.hasMoreTokens()) {
                    String lineName = tokenizer.nextToken();
                    lineNameMap.put(lineId, lineName);
                }
                if (tokenizer.hasMoreTokens()) {
                    String lineNumber = tokenizer.nextToken();
                    lineNumberMap.put(lineId, lineNumber);
                }
            });

            long t2 = System.currentTimeMillis();

            logger.info("Fetched mapping data - {} mappings. [fetched: {}ms]", lineNameMap.size(), (t2 - t1));
        } else {
            logger.error("Blob is null. Can't update line mapping");
        }
    }

    public Optional<String> getLineName(String lineId) {

        String lineName = lineNameMap.get(lineId);
        if (lineName == null) {
            // trying 2nd time with/without LOC suffix
            if (lineId.endsWith(":LOC")) {
                lineId = Strings.CS.removeEnd(lineId, ":LOC");
            } else {
                lineId = lineId + ":LOC";
            }
            lineName = lineNameMap.get(lineId);
        }
        return Optional.ofNullable(lineName);
    }

    public Optional<String> getLineNumber(String lineId) {
        String lineNumber = lineNumberMap.get(lineId);
        if (lineNumber == null) {
            if (lineId.endsWith(":LOC")) {
                lineId = Strings.CS.removeEnd(lineId, ":LOC");
            } else {
                lineId = lineId + ":LOC";
            }
            lineNumber = lineNumberMap.get(lineId);
        }
        return Optional.ofNullable(lineNumber);
    }

    public void addLineName(String lineId, String lineName) {
        lineNameMap.put(lineId, lineName);
    }

    public boolean exists(String lineOriginalId) {
        if (lineOriginalId.endsWith(":LOC")) {
            lineOriginalId = lineOriginalId.substring(0, lineOriginalId.length() - 4);
        }

        return lineNameMap.containsKey(lineOriginalId);
    }
}
