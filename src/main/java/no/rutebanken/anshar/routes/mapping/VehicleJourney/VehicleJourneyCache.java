package no.rutebanken.anshar.routes.mapping.VehicleJourney;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class VehicleJourneyCache {

    private static final Map<String, VehicleJourney> VJ_CACHE = new HashMap<>();
    private final VJMappingFileParser parser;

    public VehicleJourneyCache(VJMappingFileParser parser) {
        this.parser = parser;
    }

    public Map<String, VehicleJourney> getVjCache() {
        synchronized (this) {
            return VJ_CACHE;
        }
    }


    public Optional<VehicleJourney> findVehicleJourney(String vjCacheKey) {
        synchronized (this) {
            return Optional.ofNullable(VJ_CACHE.get(vjCacheKey));
        }
    }

    public void refill() {
        synchronized (this) {
            log.info("Refilling VJ cache");
            VJ_CACHE.clear();
            try {
                VJ_CACHE.putAll(parser.parseVjMappingCsv());

            } catch (IOException e) {
                log.error("Error parsing VJ mapping file", e);
            }
        }
        if (VJ_CACHE.isEmpty()) {
            log.warn("VJ cache is empty");
        } else {
            log.info("{} entries in VJ cache", VJ_CACHE.size());
        }


    }
}
