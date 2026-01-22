package no.rutebanken.anshar.mapping;

import no.rutebanken.anshar.routes.mapping.PoiIdsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PoiIdsServiceTest {

    PoiIdsService tested;

    @BeforeEach
    void beforeEach() {
        tested = new PoiIdsService(Path.of("src/test/resources/poi_id_mappings.csv").toFile(), 5);
    }

    @ParameterizedTest
    @CsvSource({"A,1,FR:40140:Poi:1:LOC", "A,2,FR:40140:Poi:2:LOC", "B,1,FR:40140:Poi:3:LOC", "B,2,FR:40140:Poi:4:LOC", "B,3,FR:40140:Poi:5:LOC"})
    void test_getNetexPoiIdByOperatorAndOriginalId_whenOperatorAndOriginalIdTupleInMappingsFile_shouldReturnNetexId(String operator, String originalId, String expectedNetexId) {
        tested.updatePoiIds();

        Optional<String> netexId = tested.getNetexPoiIdByOperatorAndOriginalId(operator, originalId);

        assertTrue(netexId.isPresent(), "should be present");
        assertEquals(expectedNetexId, netexId.get());
    }


    @ParameterizedTest
    @CsvSource({"A,3", "C,2", "Obi-Wan,Kenobi", "'',''"})
    void test_getNetexPoiIdByOperatorAndOriginalId_whenOperatorAndOriginalIdTupleNotInMappingsFile_shouldReturnNothing(String operator, String originalId) {
        tested.updatePoiIds();

        Optional<String> netexId = tested.getNetexPoiIdByOperatorAndOriginalId(operator, originalId);

        assertTrue(netexId.isEmpty(), "should return nothing");
    }

    @Test
    void test_getNetexPoiIdByOperatorAndOriginalId_whenInputParametersAreNull_shouldThrowNullPointerException() {
        assertThrows(NullPointerException.class, () -> tested.getNetexPoiIdByOperatorAndOriginalId(null, null));
        assertThrows(NullPointerException.class, () -> tested.getNetexPoiIdByOperatorAndOriginalId("", null));
        assertThrows(NullPointerException.class, () -> tested.getNetexPoiIdByOperatorAndOriginalId(null, ""));
    }

    @ParameterizedTest
    @CsvSource({"1,FR:40140:Poi:1:LOC", "2,FR:40140:Poi:2:LOC", "1,FR:40140:Poi:3:LOC", "2,FR:40140:Poi:4:LOC", "3,FR:40140:Poi:5:LOC"})
    void test_getOriginalPoiIdByNetexId_whenNetexIdInMappingsFile_shouldReturnOriginalId(String expectedOriginalId, String netexId) {
        tested.updatePoiIds();

        Optional<String> originalPoiId = tested.getOriginalPoiIdByNetexId(netexId);

        assertTrue(originalPoiId.isPresent(), "should be present");
        assertEquals(expectedOriginalId, originalPoiId.get());
    }

    @ParameterizedTest
    @CsvSource(value = {"FR:40140:Poi:6:LOC", "Obi-Wan Kenobi", "''"})
    void test_getOriginalPoiIdByNetexId_whenNetexIdNotInMappingsFile_shouldReturnNothing(String netexId) {
        tested.updatePoiIds();

        Optional<String> originalPoiId = tested.getOriginalPoiIdByNetexId(netexId);

        assertTrue(originalPoiId.isEmpty(), "should return nothing");
    }

    @Test
    void test_getOriginalPoiIdByNetexId__whenInputParameterIsNull_shouldThrowNullPointerException() {
        assertThrows(NullPointerException.class, () -> tested.getOriginalPoiIdByNetexId(null));
    }

    @ParameterizedTest
    @CsvSource({"A,FR:40140:Poi:1:LOC", "A,FR:40140:Poi:2:LOC", "B,FR:40140:Poi:3:LOC", "B,FR:40140:Poi:4:LOC", "B,FR:40140:Poi:5:LOC"})
    void test_getOperatorByNetexId_whenNetexIdInMappingsFile_shouldReturnOperator(String expectedOperator, String netexId) {
        tested.updatePoiIds();

        Optional<String> operator = tested.getOperatorByNetexId(netexId);

        assertTrue(operator.isPresent(), "should be present");
        assertEquals(expectedOperator, operator.get());
    }

    @ParameterizedTest
    @CsvSource(value = {"FR:40140:Poi:6:LOC", "Obi-Wan Kenobi", "''"})
    void test_getOperatorByNetexId_whenNetexIdNotInMappingsFile_shouldReturnNothing(String netexId) {
        tested.updatePoiIds();

        Optional<String> operator = tested.getOperatorByNetexId(netexId);

        assertTrue(operator.isEmpty(), "should return nothing");
    }

    @Test
    void test_getOperatorByNetexId_whenInputParameterIsNull_shouldThrowNullPointerException() {
        assertThrows(NullPointerException.class, () -> tested.getOperatorByNetexId(null));
    }

}
