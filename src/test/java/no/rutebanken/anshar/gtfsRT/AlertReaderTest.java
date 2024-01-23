package no.rutebanken.anshar.gtfsRT;

import com.google.protobuf.util.JsonFormat;
import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.gtfsrt.readers.AlertReader;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.PtSituationElement;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class AlertReaderTest extends SpringBootBaseTest {

    @Autowired
    private AlertReader alertReader;

    @Autowired
    private Situations situations;

    @Autowired
    private StopPlaceUpdaterService stopPlaceService;

    @BeforeEach
    public void init() {
        situations.clearAll();
        feedStopPlaceMappingsCache();
    }

    /**
     * Situation exchange
     * stopPoints ou stopPlaces
     */
    @Test
    public void GtfsRT_StopPoints_StopPlaces() throws IOException {

        String content = Files.readString(Path.of("src/test/resources/gtfs_rt_example.json"), StandardCharsets.US_ASCII);
        GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(content, feedMessageBuilder);

        assertNotNull(stopPlaceService);
        List<PtSituationElement> situations = alertReader.buildSituationList(feedMessageBuilder.build(),"ALEOP");

        assertFalse(situations.isEmpty());

        //entity : ALEOP:16738927
        assertEquals("53CHAMegliR", situations.get(0).getAffects().getStopPoints().getAffectedStopPoints().get(0).getStopPointRef().getValue());
        assertEquals(2, situations.get(0).getAffects().getStopPlaces().getAffectedStopPlaces().size(), "wrong size");
        assertEquals("49ROCHegli", situations.get(0).getAffects().getStopPlaces().getAffectedStopPlaces().get(0).getStopPlaceRef().getValue());
        assertEquals("49ROCHroll", situations.get(0).getAffects().getStopPlaces().getAffectedStopPlaces().get(1).getStopPlaceRef().getValue());

        //ALEOP:16738928
        assertEquals("49AVRIardeA", situations.get(1).getAffects().getStopPoints().getAffectedStopPoints().get(0).getStopPointRef().getValue());
        assertEquals("86POITsncfU", situations.get(1).getAffects().getStopPoints().getAffectedStopPoints().get(1).getStopPointRef().getValue());
        assertNull(situations.get(1).getAffects().getStopPlaces());

        //ALEOP:16738929
        assertEquals("49SEGRhaltA", situations.get(2).getAffects().getStopPlaces().getAffectedStopPlaces().get(0).getStopPlaceRef().getValue());
        assertNull(situations.get(2).getAffects().getStopPoints());
    }

    private void feedStopPlaceMappingsCache(){
        Map<String, Pair<String, String>> stopPlaceMap = new HashMap<>();
        try(FileReader fileReader = new FileReader("src/test/resources/stop_place_mapping.csv");
            CSVParser csvParser = CSVFormat.DEFAULT.parse(fileReader)) {
            for (CSVRecord record : csvParser) {
                if (record.size() >= 3) {
                    stopPlaceMap.put(record.get(0), Pair.of(record.get(1), record.get(2)));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        stopPlaceService.addStopPlaceMappings(stopPlaceMap);
        Set<String> stopQuays = new HashSet<>(stopPlaceMap.keySet());
        stopPlaceService.addStopQuays(stopQuays);
    }
}