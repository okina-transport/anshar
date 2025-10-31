package no.rutebanken.anshar.idTests;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.util.CustomStringUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReplacementTest {

    @Test
    public void testReplaceColon() {
        String originalId = "FR_NAOLIB_ORG:Quay:123";
        String processedId = CustomStringUtils.applyChouetteIdTransformation(originalId);
        assertEquals("FR_NAOLIB_ORG##3A##Quay##3A##123", processedId);

    }

    @Test
    public void testRevertStopPlaceId() {
        IdProcessingParameters idParams = new IdProcessingParameters();
        idParams.setOutputPrefixToAdd("NAOLIBORG:Quay:");
        idParams.setObjectType(ObjectType.STOP);
        idParams.setDatasetId("NAOLIBORG");


        String result = idParams.removeOutputPrefixAndSuffix("NAOLIBORG:StopPlace:HODI");
        assertEquals("HODI", result);
        System.out.println(result);


    }
}
