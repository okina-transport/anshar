package no.rutebanken.anshar.routes.mapping;

import no.rutebanken.anshar.routes.export.file.BlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.*;

@Component
@Configuration
public class LineUpdaterService {
    private static final Logger logger = LoggerFactory.getLogger(LineUpdaterService.class);

    @Value("${anshar.lineIds.file}")
    private String lineIdsPath;

    @Value("${anshar.line.ids.update.frequency.hours:10}")
    private int updateFrequency = 10;

    @Autowired
    BlobStoreService blobStoreService;

    private transient final ConcurrentMap<String, Boolean> areLineFlexible = new ConcurrentHashMap<>();

    private static final Object LOCK = new Object();

    @PostConstruct
    private void initialize() {

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        executor.scheduleAtFixedRate(this::updateLineIds, 0, updateFrequency, TimeUnit.HOURS);

        logger.info("Initialized line-ids-updater with urls:{}, updateFrequency:{} hours", new String[]{lineIdsPath}, updateFrequency);
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
                String isFlexible = tokenizer.nextToken();
                areLineFlexible.put(lineId, Boolean.valueOf(isFlexible));

            });

            long t2 = System.currentTimeMillis();

            logger.info("Fetched mapping data - {} mappings, found {} duplicates. [fetched:{}ms]", areLineFlexible.size(), (t2 - t1));
        } else {
            logger.error("Blob is null. Can't update line mapping");
        }
    }

    /**
     * Read the line cache and tells if a line is flexible or not
     *
     * @param lineId line to check
     * @return true : line is flexisble
     * false : line is NOT flexible
     */
    public boolean isLineFlexible(String lineId) {
        return areLineFlexible.containsKey(lineId) && areLineFlexible.get(lineId);
    }

    public void addFlexibleLines(Map<String, Boolean> flexibleLines) {
        areLineFlexible.putAll(flexibleLines);
    }
}
