package no.rutebanken.anshar.logging;

import lombok.Getter;

@Getter
public enum ActionType {

    OUTBOUND_SUBSCRIPTION_DELETE_ALL("OUTBOUND-SUBSCRIPTION-DELETE-ALL"),
    OUTBOUND_SUBSCRIPTION_DELETE_BY_REQUESTOR("OUTBOUND-SUBSCRIPTION-DELETE-BY-REQUESTOR"),
    OUTBOUND_SUBSCRIPTION_TERMINATE("OUTBOUND-SUBSCRIPTION-TERMINATE"),
    CACHE_CLEAR("CACHE-CLEAR");

    private final String value;

    ActionType(String value) {
        this.value = value;
    }

}
