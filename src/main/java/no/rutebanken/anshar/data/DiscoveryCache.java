package no.rutebanken.anshar.data;

import com.hazelcast.map.IMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DiscoveryCache {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryCache.class);

    @Autowired
    @Qualifier("getDiscoveryStops")
    private IMap<String, Set<String>> discoveryStops;

    @Autowired
    @Qualifier("getDiscoveryLines")
    private IMap<String, Set<String>> discoveryLines;


    public void addStop(String datasetId, String stop) {
        Set<String> stopsByDataset = discoveryStops.get(datasetId);
        if (stopsByDataset == null) {
            stopsByDataset = new HashSet<>();
        }
        stopsByDataset.add(stop);
        discoveryStops.set(datasetId, stopsByDataset);
    }

    public void addStops(String datasetId, List<String> stops) {
        Set<String> stopsByDataset = discoveryStops.get(datasetId);
        if (stopsByDataset == null) {
            stopsByDataset = new HashSet<>();
        }
        stopsByDataset.addAll(stops);
        discoveryStops.set(datasetId, stopsByDataset);
    }

    public void addLine(String datasetId, String line) {
        Set<String> linesByDataset = discoveryLines.get(datasetId);
        if (linesByDataset == null) {
            linesByDataset = new HashSet<>();
        }
        linesByDataset.add(line);
        discoveryLines.set(datasetId, linesByDataset);
    }

    public void addLines(String datasetId, List<String> lines) {
        Set<String> linesByDataset = discoveryLines.get(datasetId);
        if (linesByDataset == null) {
            linesByDataset = new HashSet<>();
        }
        linesByDataset.addAll(lines);
        discoveryLines.set(datasetId, linesByDataset);
    }

    public void clearDiscoveryStops() {
        discoveryStops.clear();
    }

    public void clearDiscoveryLines() {
        discoveryLines.clear();
    }

    public Map<String, Set<String>> getDiscoveryStops() {
        return discoveryStops;
    }

    public Map<String, Set<String>> getDiscoveryLines() {
        return discoveryLines;
    }

    public Set<String> getDiscoveryStopsForDataset(String datasetId) {
        return discoveryStops.get(datasetId);
    }

    public Set<String> getDiscoveryLinesForDataset(String datasetId) {
        return discoveryLines.get(datasetId);
    }
}
