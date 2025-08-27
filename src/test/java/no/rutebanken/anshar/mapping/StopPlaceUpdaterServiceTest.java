package no.rutebanken.anshar.mapping;


import no.rutebanken.anshar.routes.mapping.StopPlaceRegisterMappingFetcher;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class StopPlaceUpdaterServiceTest {

    @Mock
    StopPlaceRegisterMappingFetcher  stopPlaceRegisterMappingFetcher;

    @InjectMocks
    private StopPlaceUpdaterService tested;

    @Test
    public void test_whenUpdatingIdMappingMultipleTimes_thenDoNotDuplicateIds() {

        when(stopPlaceRegisterMappingFetcher.fetchStopPlaceMapping(any())).thenReturn(
                Map.of(
                        "TEST:azerty", Pair.of("MOBIITI:1", "Azerty"),
                        "TEST:azerty2", Pair.of("MOBIITI:1", "Azerty2"),
                        "TEST:azerty3", Pair.of("MOBIITI:1", "Azerty3"),
                        "TEST:uiop", Pair.of("MOBIITI:2", "Uiop"),
                        "TEST:qsdf", Pair.of("MOBIITI:3", "Qsdf")
                )
        );
        when(stopPlaceRegisterMappingFetcher.fetchStopPlaceQuayJson(any())).thenReturn(
                Map.of()
        );

        // update id mapping twice to check for memory leak
        tested.updateIdMapping();

        tested.updateIdMapping();

        var output = tested.getReverseStopPlaceMappings();

        assertEquals(3, output.size());
        assertEquals(Set.of("TEST:azerty", "TEST:azerty2", "TEST:azerty3"), output.get("MOBIITI:1"));
        assertEquals(Set.of("TEST:uiop"), output.get("MOBIITI:2"));
        assertEquals(Set.of("TEST:qsdf"), output.get("MOBIITI:3"));
    }

}
