package no.rutebanken.anshar.okinaDisruptions.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkinaAccessibilityAssessment {

    private Long id;
    protected LimitationStatusEnumeration mobilityImpairedAccess = LimitationStatusEnumeration.UNKNOWN;


    protected OkinaAccessibilityLimitation limitations;

}
