package no.rutebanken.anshar.gtfsRT;

import com.google.protobuf.util.JsonFormat;
import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.gtfsrt.swallowers.AlertSwallower;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri20.PtSituationElement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AlertSwallowerTest extends SpringBootBaseTest {

    @Autowired
    private AlertSwallower alertSwallower;

    @Autowired
    private Situations situations;

    @BeforeEach
    public void init() {
        situations.clearAll();
    }

    @Test
    void ingestAlertData() throws IOException {
        String content = Files.readString(Path.of("src/test/resources/gtfs_rt_example.json"), StandardCharsets.US_ASCII);
        GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(content, feedMessageBuilder);
        alertSwallower.setUrl("http://www.test.fr");

        alertSwallower.ingestAlertData("TEST", feedMessageBuilder.build());

        Collection<PtSituationElement> savedSituations = situations.getAll();

        assertTrue(4 == savedSituations.size());

        for(PtSituationElement savedSituation :  savedSituations) {
            assertNotNull(savedSituation.getValidityPeriods());
            assertNotEquals(savedSituation.getValidityPeriods().size(), 0);
            for(HalfOpenTimestampOutputRangeStructure validityPeriod : savedSituation.getValidityPeriods()){
                assertNotNull(validityPeriod.getStartTime());
            }
        }

    }
}