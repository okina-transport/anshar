package no.rutebanken.anshar.gtfsrt;

import com.google.protobuf.util.JsonFormat;
import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.config.GTFSRTType;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static no.rutebanken.anshar.gtfsrt.GtfsRtConstants.*;

@Component
public class GtfsRtHelper {

    private static final Logger logger = LoggerFactory.getLogger(GtfsRtHelper.class);

    private final ExtendedHazelcastService hazelcastService;

    public GtfsRtHelper(ExtendedHazelcastService hazelCastService) {
        this.hazelcastService = hazelCastService;
    }

    /**
     * Creates a GTFSRT feed message object from an URL
     *
     * @param gtfsRTApi parameters of the API  : url, type (json or protobuf), dataset
     * @return a GTFSRT FeedMessage object
     */
    public Optional<GtfsRealtime.FeedMessage> buildMessageFromApi(GtfsRTApi gtfsRTApi) {

        try {
            URL url1 = new URL(gtfsRTApi.getUrl());
            if (gtfsRTApi.getType() == null || GTFSRTType.PROTOBUF.equals(gtfsRTApi.getType())) {
                BufferedInputStream in = new BufferedInputStream(url1.openStream());
                return Optional.of(GtfsRealtime.FeedMessage.newBuilder().mergeFrom(in).build());
            }

            GtfsRealtime.FeedMessage.Builder structBuilder = GtfsRealtime.FeedMessage.newBuilder();
            String json = IOUtils.toString(url1, StandardCharsets.UTF_8);
            JsonFormat.parser().ignoringUnknownFields().merge(json, structBuilder);
            return Optional.of(structBuilder.build());
        } catch (IOException ex) {
            logger.error("Error while creating feedMessage", ex);
            return Optional.empty();
        }

    }

    public boolean isGtfsRtRunning() {
        Object isGtfsRtRunning = hazelcastService.getHazelcastInstance().getMap(LOCK_MAP).get(GTFS_RT_LOCK);
        return isGtfsRtRunning != null && (boolean) isGtfsRtRunning;
    }


    public long getLastExecutionTime() {
        Object lastExecutionTime = hazelcastService.getHazelcastInstance().getMap(LOCK_MAP).get(GTFS_RT_LAST_EXECUTION_TIME);
        return lastExecutionTime != null ? (long) lastExecutionTime : 0;
    }
}
