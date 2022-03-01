package no.rutebanken.anshar.okinaDisruptions.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import no.rutebanken.anshar.okinaDisruptions.model.enums.BikeAccessEnum;
import no.rutebanken.anshar.okinaDisruptions.model.enums.TadEnum;
import no.rutebanken.anshar.okinaDisruptions.model.enums.TransportModeNameEnum;
import no.rutebanken.anshar.okinaDisruptions.model.enums.TransportSubModeNameEnum;
import no.rutebanken.anshar.okinaDisruptions.model.enums.WheelchairAccessEnum;

import javax.ws.rs.DefaultValue;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;


/**
 * res
 * The persistent class for the line database table.
**/
@XmlRootElement
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OkinaLine extends NeptuneIdentifiedObject {


    private String name;
    private String comment;
    private String number;
    private String publishedName;
    private String stableId;
    private String registrationNumber;
    private TransportModeNameEnum transportModeName;
    private TransportSubModeNameEnum transportSubModeName;
    private Boolean mobilityRestrictedSuitable;
    private Integer intUserNeeds;
    private Boolean flexibleService;
    private String url;
    private String color;
    private String textColor;
    private OkinaNetwork network;

    @JsonIgnore
    private OkinaCompany okinaCompany;

    @DefaultValue("false")
    private Boolean supprime = false;
    private String externalRef;
    private TadEnum tad;
    private WheelchairAccessEnum wheelchairAccess;
    private BikeAccessEnum bike;
    private Integer position;
    private OkinaAccessibilityAssessment accessibilityAssessment;

}