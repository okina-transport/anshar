package no.rutebanken.anshar.config;

import lombok.Data;
import no.rutebanken.anshar.data.util.CustomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.Objects;


@Data
public class IdProcessingParameters implements Serializable {

    private String datasetId;
    private ObjectType objectType;
    private String inputPrefixToRemove;
    private String inputSuffixToRemove;
    private String outputPrefixToAdd;
    private String outputSuffixToAdd;

    /**
     * Apply transformations defined in this class (prefix/suffix removal and after prefix/suffix add) to the input String
     *
     * @param text input string on which transformation must be applied
     * @return the transformed string
     */
    public String applyTransformationToString(String text) {
        if (StringUtils.isEmpty(text)) {
            return text;
        }

        if (inputPrefixToRemove != null && text.startsWith(inputPrefixToRemove)) {
            text = text.substring(inputPrefixToRemove.length());
        }

        if (inputSuffixToRemove != null && text.endsWith(inputSuffixToRemove)) {
            text = text.substring(0, text.length() - inputSuffixToRemove.length());
        }

        text = CustomStringUtils.applyChouetteIdTransformation(text);


        if (outputPrefixToAdd != null && !text.startsWith(outputPrefixToAdd)) {
            text = outputPrefixToAdd + text;
        }

        if (outputSuffixToAdd != null && !text.endsWith(outputSuffixToAdd)) {
            text = text + outputSuffixToAdd;
        }
        return text;

    }

    /**
     * Removes input prefix and input suffix from a string.
     * e.g : input : PROV1:Quay:abcd:LOC  output : abcd
     *
     * @param text input text to process
     * @return text without prefix and suffix
     */
    public String removeInputPrefixAndSuffix(String text) {
        if (StringUtils.isEmpty(text)) {
            return text;
        }

        if (inputPrefixToRemove != null && text.startsWith(inputPrefixToRemove)) {
            text = text.substring(inputPrefixToRemove.length());
        }

        if (inputSuffixToRemove != null && text.endsWith(inputSuffixToRemove)) {
            text = text.substring(0, text.length() - inputSuffixToRemove.length());
        }
        return text;
    }


    /**
     * Removes output prefix and output suffix from a string.
     * e.g : input : PROV1:Quay:abcd:LOC  output : abcd
     *
     * @param text input text to process
     * @return text without prefix and suffix
     */
    public String removeOutputPrefixAndSuffix(String text) {
        if (StringUtils.isEmpty(text)) {
            return text;
        }
        if (outputPrefixToAdd != null) {
            if (text.startsWith(outputPrefixToAdd)) {
                text = text.substring(outputPrefixToAdd.length());
            } else {
                // handling stopPlace case
                String stopPlacePrefix = outputPrefixToAdd.replace(":Quay:", ":StopPlace:");
                if (text.startsWith(stopPlacePrefix)) {
                    text = text.substring(stopPlacePrefix.length());
                }
            }
        }

        if (outputSuffixToAdd != null && text.endsWith(outputSuffixToAdd)) {
            text = text.substring(0, text.length() - outputSuffixToAdd.length());
        }
        return text;
    }

    /**
     * Revert basic transformation made on text.
     * <p>
     * input : PROV1:Quay:abcd:LOC
     * output : CUSTOMPREF::ZENPOINT::abcd::loc
     *
     * @param text
     * @return the revertedText
     */
    public String revertTransformationToString(String text) {
        if (StringUtils.isEmpty(text)) {
            return text;
        }

        text = removeOutputPrefixAndSuffix(text);
        if (inputPrefixToRemove != null && !text.startsWith(inputPrefixToRemove)) {
            text = inputPrefixToRemove + text;
        }

        if (inputSuffixToRemove != null && !text.endsWith(inputSuffixToRemove)) {
            text = text + inputSuffixToRemove;
        }
        return text;

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdProcessingParameters that = (IdProcessingParameters) o;
        return Objects.equals(datasetId, that.datasetId) && objectType == that.objectType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(datasetId, objectType);
    }
}
