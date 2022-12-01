package no.rutebanken.anshar.util;


import no.rutebanken.anshar.data.util.TimingTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PerformanceTimingService {

    private final Logger logger = LoggerFactory.getLogger(PerformanceTimingService.class);

    private static Map<String, TimingTracer> tracerCache = new HashMap<>();


    public static void createNewTracer (String name){
        TimingTracer tracer = new TimingTracer(name);
        tracerCache.put(name, tracer);
    }

    public static TimingTracer getTracer (String name){
        return tracerCache.get(name);
    }

}
