package no.rutebanken.anshar.ishtar.model;

import lombok.Data;

import java.util.Date;

@Data
public class SiriApiDto {

    private Long id;
    private String datasetId;
    private String type;
    private String url;
    private Boolean active;
    private Date updateDatetime;

}
