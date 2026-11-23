package no.rutebanken.anshar.routes.mapping;

import no.rutebanken.anshar.routes.export.file.BlobStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScheduledStopPointUpdaterServiceTest {

    private static final String PATH = "sspQuayAssociation.csv";

    private BlobStoreService blobStoreService;
    private ScheduledStopPointUpdaterService service;

    @BeforeEach
    void setUp() {
        blobStoreService = mock(BlobStoreService.class);
        service = new ScheduledStopPointUpdaterService(blobStoreService, PATH, 240);
    }

    @Test
    void updateCache_shouldSkipHeaderAndInvalidLines() {
        when(blobStoreService.getBlob(PATH)).thenReturn(csv("""
                scheduledStopPoint,quay
                LOCGUI1119:ScheduledStopPoint:85011031,MOBIITI:Quay:3609
                LOCGUI1119:ScheduledStopPoint:85042150,MOBIITI:Quay:3577

                INVALID_LINE
                LOCGUI1119:ScheduledStopPoint:EMPTY,
                """));

        service.updateCache();

        assertThat(service.size()).isEqualTo(2);
        assertThat(service.getQuay("LOCGUI1119:ScheduledStopPoint:85011031")).contains("MOBIITI:Quay:3609");
        assertThat(service.getQuay("LOCGUI1119:ScheduledStopPoint:85042150")).contains("MOBIITI:Quay:3577");
        assertThat(service.getQuay("scheduledStopPoint")).isEmpty();
        assertThat(service.getQuay("UNKNOWN")).isEmpty();
        assertThat(service.getQuay(null)).isEmpty();
    }

    @Test
    void updateCache_shouldReplaceWholeCacheOnReload() {
        when(blobStoreService.getBlob(PATH))
                .thenReturn(csv("scheduledStopPoint,quay\nSSP:1,QUAY:1\nSSP:2,QUAY:2\n"))
                .thenReturn(csv("scheduledStopPoint,quay\nSSP:1,QUAY:10\n"));

        service.updateCache();
        service.updateCache();

        assertThat(service.size()).isEqualTo(1);
        assertThat(service.getQuay("SSP:1")).contains("QUAY:10");
        assertThat(service.getQuay("SSP:2")).isEmpty();
    }

    @Test
    void updateCache_whenBlobIsMissing_shouldKeepPreviousCache() {
        when(blobStoreService.getBlob(PATH))
                .thenReturn(csv("scheduledStopPoint,quay\nSSP:1,QUAY:1\n"))
                .thenReturn(null);

        service.updateCache();
        service.updateCache();

        assertThat(service.getQuay("SSP:1")).contains("QUAY:1");
    }

    @Test
    void updateCache_whenBlobIsMissingAtStartup_shouldNotThrowAndLeaveCacheEmpty() {
        when(blobStoreService.getBlob(PATH)).thenReturn(null);

        assertThatCode(() -> service.updateCache()).doesNotThrowAnyException();

        assertThat(service.size()).isZero();
    }

    @Test
    void updateCache_whenFileIsEmpty_shouldNotThrowAndKeepPreviousCache() {
        when(blobStoreService.getBlob(PATH))
                .thenReturn(csv("scheduledStopPoint,quay\nSSP:1,QUAY:1\n"))
                .thenReturn(csv(""))
                .thenReturn(csv("scheduledStopPoint,quay\n"));

        service.updateCache();
        assertThatCode(() -> service.updateCache()).doesNotThrowAnyException();
        assertThatCode(() -> service.updateCache()).doesNotThrowAnyException();

        assertThat(service.size()).isEqualTo(1);
        assertThat(service.getQuay("SSP:1")).contains("QUAY:1");
    }

    @Test
    void updateCache_whenReadFails_shouldKeepPreviousCacheAndNotThrow() {
        when(blobStoreService.getBlob(PATH))
                .thenReturn(csv("scheduledStopPoint,quay\nSSP:1,QUAY:1\n"))
                .thenThrow(new RuntimeException("storage unavailable"));

        service.updateCache();
        service.updateCache();

        assertThat(service.getQuay("SSP:1")).contains("QUAY:1");
    }

    private static InputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
