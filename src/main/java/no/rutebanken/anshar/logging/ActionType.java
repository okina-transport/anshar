package no.rutebanken.anshar.logging;

import lombok.Getter;

@Getter
public enum ActionType {

    OUTBOUND_SUBSCRIPTION_DELETE_ALL("OUTBOUND_SUBSCRIPTION_DELETE_ALL"),
    OUTBOUND_SUBSCRIPTION_DELETE_BY_REQUESTOR("OUTBOUND_SUBSCRIPTION_DELETE_BY_REQUESTOR"),
    OUTBOUND_SUBSCRIPTION_TERMINATE("OUTBOUND_SUBSCRIPTION_TERMINATE"),
    CACHE_CLEAR("CACHE_CLEAR");

    private final String value;

    ActionType(String value) {
        this.value = value;
    }

}
