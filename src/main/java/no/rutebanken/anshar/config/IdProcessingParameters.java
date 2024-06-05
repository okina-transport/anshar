package no.rutebanken.anshar.config;

import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.Objects;


public class IdProcessingParameters implements Serializable {

    private String datasetId;
    private ObjectType objectType;
    private String inputPrefixToRemove;
    private String inputSuffixToRemove;
    private String outputPrefixToAdd;
    private String outputSuffixToAdd;


    public String getDatasetId() {
        return datasetId;
    }

    public String getInputPrefixToRemove() {
        return inputPrefixToRemove;
    }

    public String getInputSuffixToRemove() {
        return inputSuffixToRemove;
    }

    public String getOutputPrefixToAdd() {
        return outputPrefixToAdd;
    }

    public String getOutputSuffixToAdd() {
        return outputSuffixToAdd;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public void setInputPrefixToRemove(String inputPrefixToRemove) {
        this.inputPrefixToRemove = inputPrefixToRemove;
    }

    public void setInputSuffixToRemove(String inputSuffixToRemove) {
        this.inputSuffixToRemove = inputSuffixToRemove;
    }

    public void setOutputPrefixToAdd(String outputPrefixToAdd) {
        this.outputPrefixToAdd = outputPrefixToAdd;
    }

    public void setOutputSuffixToAdd(String outputSuffixToAdd) {
        this.outputSuffixToAdd = outputSuffixToAdd;
    }


    public ObjectType getObjectType() {
        return objectType;
    }

    public void setObjectType(ObjectType objectType) {
        this.objectType = objectType;
    }


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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdProcessingParameters that = (IdProcessingParameters) o;
        return Objects.equals(datasetId, that.datasetId) && objectType == that.objectType && Objects.equals(inputPrefixToRemove, that.inputPrefixToRemove) && Objects.equals(inputSuffixToRemove, that.inputSuffixToRemove) && Objects.equals(outputPrefixToAdd, that.outputPrefixToAdd) && Objects.equals(outputSuffixToAdd, that.outputSuffixToAdd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(datasetId, objectType, inputPrefixToRemove, inputSuffixToRemove, outputPrefixToAdd, outputSuffixToAdd);
    }
}
