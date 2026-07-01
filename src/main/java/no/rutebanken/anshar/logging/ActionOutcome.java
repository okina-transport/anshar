package no.rutebanken.anshar.logging;

import lombok.Getter;

@Getter
public final class ActionOutcome {

    private static final ActionOutcome SUCCESS = new ActionOutcome(null);

    private final String errorMessage;

    private ActionOutcome(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public static ActionOutcome success() {
        return SUCCESS;
    }

    public static ActionOutcome failure(Exception e) {
        return new ActionOutcome(e.getMessage());
    }

    public boolean isSuccess() {
        return errorMessage == null;
    }

}
