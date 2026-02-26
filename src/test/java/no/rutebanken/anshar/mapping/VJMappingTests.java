package no.rutebanken.anshar.mapping;

import no.rutebanken.anshar.routes.mapping.VehicleJourney.VJMappingFileParser;
import no.rutebanken.anshar.routes.mapping.VehicleJourney.VehicleJourney;
import no.rutebanken.anshar.routes.mapping.VehicleJourney.VehicleJourneyCache;
import org.apache.commons.collections.MapUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class VJMappingTests {

    @Mock
    VJMappingFileParser csvFileParser;
    private VehicleJourneyCache tested;

    @BeforeEach
    void setUp() {
        tested = new VehicleJourneyCache(csvFileParser);
        // clear all caches
        tested.getVjCache().clear();
    }

    @Test
    void test_findVehicleJourney_whenCacheIsEmpty_returnsNothing() {
        assertTrue(tested.getVjCache().isEmpty(), "cache should be empty");

        var output = tested.findVehicleJourney("KEY");

        assertTrue(output.isEmpty(), "should return nothing");
    }

    @Test
    void test_findVehicleJourney_whenKeyNotInCache_returnsNothing() {
        VehicleJourney expectedVj = new VehicleJourney(null, null, null, 1);
        tested.getVjCache().put("KEY", expectedVj);

        var output = tested.findVehicleJourney("KEY2");

        assertTrue(output.isEmpty(), "should return nothing");
    }

    @Test
    void test_findVehicleJourney_whenKeyInCache_returnsValue() {
        VehicleJourney expectedVj = new VehicleJourney(null, null, null, 1);
        tested.getVjCache().put("KEY", expectedVj);

        var output = tested.findVehicleJourney("KEY");

        assertTrue(output.isPresent(), "should return vj");
        assertEquals(expectedVj, output.get(), "should return vj");
    }

    @Test
    void test_refill_whenParsingVjMappingCsvFails_thenVjMappingCacheIsEmpty() throws IOException {
        when(csvFileParser.parseVjMappingCsv()).thenThrow(new IOException("Error"));

        tested.refill();

        assertTrue(tested.getVjCache().isEmpty(), "vj cache should be empty");
    }

    @Test
    void test_refill_whenParsingVjMappingCsvIsSuccessful_thenVjMappingCacheIsFilled() throws IOException {
        var expected = Map.of("KEY", new VehicleJourney(null, null, null, 1));
        when(csvFileParser.parseVjMappingCsv()).thenReturn(expected);

        tested.refill();

        assertEquals(expected, tested.getVjCache(), "vj cache should be filled");
    }

    @Test
    void test_refill_whenCacheIsEmpty_thenRefillIt() throws IOException {
        VehicleJourney expectedVj = new VehicleJourney(null, null, null, 1);
        Map<String, VehicleJourney> expectedVjCache = Map.of("KEY", expectedVj);
        when(csvFileParser.parseVjMappingCsv()).thenReturn(expectedVjCache);

        assertTrue(MapUtils.isEmpty(tested.getVjCache()), "ineo VJ cache should not be filled");

        // Act
        tested.refill();

        // Assert
        assertEquals(expectedVjCache, tested.getVjCache(), "vj cache should be filled");
    }

    @Test
    void test_refill_whenCacheIsNotEmpty_thenClearAndRefillIt() throws IOException {
        // Arrange
        Map<String, VehicleJourney> expectedVjCache = Map.of("KEY", new VehicleJourney(null, null, null, 1));
        when(csvFileParser.parseVjMappingCsv()).thenReturn(expectedVjCache);
        // Make sure caches are not empty
        tested.getVjCache().put("@@@", new VehicleJourney(null, null, null, 1));

        assertTrue(MapUtils.isNotEmpty(tested.getVjCache()), "ineo VJ cache should be filled");

        // Act
        tested.refill();

        // Assert
        assertNull(tested.getVjCache().get("@@@"), "should clear ineo VJ cache before refilling");

        assertEquals(expectedVjCache, tested.getVjCache(), "vj cache should be filled");
    }


}
