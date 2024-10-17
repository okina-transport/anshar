package no.rutebanken.anshar.ishtar.model;

import lombok.Data;

import java.util.Date;

@Data
public class IdProcessingParameterDto {

    private Long id;
    private String datasetId;
    private String objectType;
    private String inputPrefixToRemove;
    private String inputSuffixToRemove;
    private String outputPrefixToAdd;
    private String outputSuffixToAdd;
    private Date updateDatetime;

}
