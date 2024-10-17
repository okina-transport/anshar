package no.rutebanken.anshar.config;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;


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

        if (outputPrefixToAdd != null && !text.startsWith(outputPrefixToAdd)) {
            text = outputPrefixToAdd + text;
        }

        if (outputSuffixToAdd != null && !text.endsWith(outputSuffixToAdd)) {
            text = text + outputSuffixToAdd;
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
        if (outputPrefixToAdd != null && text.startsWith(outputPrefixToAdd)) {
            text = text.substring(outputPrefixToAdd.length());
        }

        if (outputSuffixToAdd != null && text.endsWith(outputSuffixToAdd)) {
            text = text.substring(0, text.length() - outputSuffixToAdd.length());
        }
        return text;
    }

}
