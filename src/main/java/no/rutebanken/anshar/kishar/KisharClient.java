package no.rutebanken.anshar.kishar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;

@Component
public class KisharClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private final WebClient client;

    public KisharClient(@Value("${kishar.server.url}") URI kisharUri) {
        client = WebClient.builder().baseUrl(kisharUri.toString()).build();
    }

    public void clearCacheByDatasetId(String datasetId) {
        client.delete().uri("/api/dataset/" + datasetId).retrieve().bodyToMono(Void.class).block(TIMEOUT);
    }

}
