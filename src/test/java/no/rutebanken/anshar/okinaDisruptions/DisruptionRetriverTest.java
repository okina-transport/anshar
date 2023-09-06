package no.rutebanken.anshar.okinaDisruptions;

import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;

public class DisruptionRetriverTest  extends SpringBootBaseTest {

    @Autowired
    DisruptionRetriever disruptionRetriever;

    @Autowired
    private DisruptionService disruptionService;

    @Autowired
    private Situations situations;

    @Autowired
    private SiriHandler handler;

    @Autowired
    private SubscriptionManager subscriptionManager;

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
