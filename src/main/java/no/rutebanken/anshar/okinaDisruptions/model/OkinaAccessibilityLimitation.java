package no.rutebanken.anshar.okinaDisruptions.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkinaAccessibilityLimitation {

    private Long id;
    protected LimitationStatusEnumeration wheelchairAccess;
    protected LimitationStatusEnumeration stepFreeAccess;
    protected LimitationStatusEnumeration escalatorFreeAccess;
    protected LimitationStatusEnumeration liftFreeAccess;
    protected LimitationStatusEnumeration audibleSignalsAvailable;
    protected LimitationStatusEnumeration visualSignsAvailable;
}
