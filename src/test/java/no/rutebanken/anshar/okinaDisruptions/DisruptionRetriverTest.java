package no.rutebanken.anshar.okinaDisruptions;

import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class DisruptionRetriverTest extends SpringBootBaseTest {

    @Autowired
    DisruptionRetriever disruptionRetriever;

    @Autowired
    private Situations situations;

    @BeforeEach
    public void init() throws IOException {
        situations.clearAll();
    }

    @Test
    public void testDiffusedDisruption() throws IOException {
        String diffused = Files.readString(Path.of("src/test/resources/disruption_diffused.json"), StandardCharsets.UTF_8);

        disruptionRetriever.ingestDisruption(diffused);

        assertThat(situations.getAll().size()).isEqualTo(1);
    }

    @Test
    public void testDiffusingDisruption() throws IOException {
        String diffusing = Files.readString(Path.of("src/test/resources/disruption_diffusing.json"), StandardCharsets.UTF_8);

        disruptionRetriever.ingestDisruption(diffusing);

        assertThat(situations.getAll().size()).isEqualTo(0);
    }

    @Test
    public void testEmptyDisruption() throws IOException {
        String emptyJson = "";

        disruptionRetriever.ingestDisruption(emptyJson);

        assertThat(situations.getAll().size()).isEqualTo(0);
    }
}
