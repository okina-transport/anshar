package no.rutebanken.anshar.mapping;

import no.rutebanken.anshar.routes.mapping.ParkingIdsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ParkingIdsServiceTest {

    ParkingIdsService tested;

    @BeforeEach
    void beforeEach() {
        tested = new ParkingIdsService(Path.of("src/test/resources/parking_id_mappings.csv").toFile(), 5);
    }

    @ParameterizedTest
    @CsvSource({"A,1,FR:40140:Parking:1:LOC", "A,2,FR:40140:Parking:2:LOC", "B,1,FR:40140:Parking:3:LOC", "B,2,FR:40140:Parking:4:LOC", "B,3,FR:40140:Parking:5:LOC"})
    void test_getNetexParkingIdByOperatorAndOriginalId_whenOperatorAndOriginalIdTupleInMappingsFile_shouldReturnNetexId(String operator, String originalId, String expectedNetexId) {
        tested.updateParkingIds();

        Optional<String> netexId = tested.getNetexParkingIdByOperatorAndOriginalId(operator, originalId);

        assertTrue(netexId.isPresent(), "should be present");
        assertEquals(expectedNetexId, netexId.get());
    }


    @ParameterizedTest
    @CsvSource({"A,3", "C,2", "Obi-Wan,Kenobi", "'',''"})
    void test_getNetexParkingIdByOperatorAndOriginalId_whenOperatorAndOriginalIdTupleNotInMappingsFile_shouldReturnNothing(String operator, String originalId) {
        tested.updateParkingIds();

        Optional<String> netexId = tested.getNetexParkingIdByOperatorAndOriginalId(operator, originalId);

        assertTrue(netexId.isEmpty(), "should return nothing");
    }

    @Test
    void test_getNetexParkingIdByOperatorAndOriginalId_whenInputParametersAreNull_shouldThrowNullPointerException() {
        assertThrows(NullPointerException.class, () -> tested.getNetexParkingIdByOperatorAndOriginalId(null, null));
        assertThrows(NullPointerException.class, () -> tested.getNetexParkingIdByOperatorAndOriginalId("", null));
        assertThrows(NullPointerException.class, () -> tested.getNetexParkingIdByOperatorAndOriginalId(null, ""));
    }

    @ParameterizedTest
    @CsvSource({"1,FR:40140:Parking:1:LOC", "2,FR:40140:Parking:2:LOC", "1,FR:40140:Parking:3:LOC", "2,FR:40140:Parking:4:LOC", "3,FR:40140:Parking:5:LOC"})
    void test_getOriginalParkingIdByNetexId_whenNetexIdInMappingsFile_shouldReturnOriginalId(String expectedOriginalId, String netexId) {
        tested.updateParkingIds();

        Optional<String> originalParkingId = tested.getOriginalParkingIdByNetexId(netexId);

        assertTrue(originalParkingId.isPresent(), "should be present");
        assertEquals(expectedOriginalId, originalParkingId.get());
    }

    @ParameterizedTest
    @CsvSource(value = {"FR:40140:Parking:6:LOC", "Obi-Wan Kenobi", "''"})
    void test_getOriginalParkingIdByNetexId_whenNetexIdNotInMappingsFile_shouldReturnNothing(String netexId) {
        tested.updateParkingIds();

        Optional<String> originalParkingId = tested.getOriginalParkingIdByNetexId(netexId);

        assertTrue(originalParkingId.isEmpty(), "should return nothing");
    }

    @Test
    void test_getOriginalParkingIdByNetexId__whenInputParameterIsNull_shouldThrowNullPointerException() {
        assertThrows(NullPointerException.class, () -> tested.getOriginalParkingIdByNetexId(null));
    }

    @ParameterizedTest
    @CsvSource({"A,FR:40140:Parking:1:LOC", "A,FR:40140:Parking:2:LOC", "B,FR:40140:Parking:3:LOC", "B,FR:40140:Parking:4:LOC", "B,FR:40140:Parking:5:LOC"})
    void test_getOperatorByNetexId_whenNetexIdInMappingsFile_shouldReturnOperator(String expectedOperator, String netexId) {
        tested.updateParkingIds();

        Optional<String> operator = tested.getOperatorByNetexId(netexId);

        assertTrue(operator.isPresent(), "should be present");
        assertEquals(expectedOperator, operator.get());
    }

    @ParameterizedTest
    @CsvSource(value = {"FR:40140:Parking:6:LOC", "Obi-Wan Kenobi", "''"})
    void test_getOperatorByNetexId_whenNetexIdNotInMappingsFile_shouldReturnNothing(String netexId) {
        tested.updateParkingIds();

        Optional<String> operator = tested.getOperatorByNetexId(netexId);

        assertTrue(operator.isEmpty(), "should return nothing");
    }

    @Test
    void test_getOperatorByNetexId_whenInputParameterIsNull_shouldThrowNullPointerException() {
        assertThrows(NullPointerException.class, () -> tested.getOperatorByNetexId(null));
    }

}
