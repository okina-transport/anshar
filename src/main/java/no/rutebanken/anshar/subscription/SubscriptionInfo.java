package no.rutebanken.anshar.subscription;

import lombok.Getter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Getter
public class SubscriptionInfo {
    private final String id;
    private final String type;
    private final String mode;
    private final String status;
    private final ZonedDateTime lastActivity;
    private final ZonedDateTime startedAt;
    private final Long receivedBytes;
    private final boolean active;

    public SubscriptionInfo(SubscriptionSetup subscription, Instant lastActivityInstant, Long receivedBytes) {
        this.id = subscription.getSubscriptionId();
        this.type = subscription.getSubscriptionType() != null ? subscription.getSubscriptionType().name() : "";
        this.mode = subscription.getSubscriptionMode() != null ? subscription.getSubscriptionMode().name() : "";
        this.status = subscription.getStatus() != null ? subscription.getStatus().name() : "";
        this.lastActivity = lastActivityInstant != null ? lastActivityInstant.atZone(ZoneId.systemDefault()) : null;
        this.startedAt = subscription.getStartedAt();
        this.receivedBytes = receivedBytes;
        this.active = subscription.isActive();
    }
}
