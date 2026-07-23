package no.rutebanken.anshar.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static no.rutebanken.anshar.subscription.SubscriptionSetup.SubscriptionMode.SUBSCRIBE;
import static no.rutebanken.anshar.subscription.SubscriptionStatus.RUNNING;

public class SubscriptionPredicates {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionPredicates.class);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");


    /**
     * Checks if the subscription has a mode "SUBSCRIBE" and is active and running
     *
     * @param subscription subscription to check
     * @return true : has "SUBSCRIBE" mode, is active and running
     * false : not SUBSCRIBE or not active or not running
     */
    public static final Predicate<SubscriptionSetup> IS_RUNNING = subscription ->
            subscription.isActive()
                    && SUBSCRIBE.equals(subscription.getSubscriptionMode())
                    && RUNNING.equals(subscription.getStatus());


    /**
     * Check if a subscripton is out of date by checking restart time and last restart (startedAt)
     *
     * @param subscription subscription to check
     * @return true : subscription is out of date
     * false : subscription is up to date
     */
    public static final Predicate<SubscriptionSetup> IS_OUT_OF_DATE = subscription -> {
        try {
            LocalTime time = LocalTime.parse(subscription.getRestartTime(), TIME_FORMATTER);
            LocalDate today = LocalDate.now();
            Instant restartedAt = subscription.getStartedAt().toInstant();
            Instant trigger = LocalDateTime.of(today, time).atZone(subscription.getStartedAt().getZone()).toInstant();
            Instant now = ZonedDateTime.now().toInstant();
            return restartedAt.isBefore(trigger) && now.isAfter(trigger);
        } catch (DateTimeParseException ex) {
            logger.error("Exception while parsing restart time: {}", subscription.getSubscriptionId());
            return false;
        }
    };

    /**
     * Check if the subscription is unresponsive (last activity > limit)
     *
     * @return true : unresponsive. no activity since a long time
     * false : data received recently
     */
    public static Predicate<SubscriptionSetup> isUnresponsive(Map<String, Instant> lastActivity, long unresponsiveDelay) {
        return subscription -> {
            String subscriptionId = subscription.getSubscriptionId();
            return !lastActivity.containsKey(subscriptionId)
                    || lastActivity.get(subscriptionId).isBefore(Instant.now().minus(unresponsiveDelay, ChronoUnit.MINUTES));
        };
    }

    /**
     * Checks if the subscription is eligible for a responsive check. Subscription is eligible if :
     * - subscription is SM/VM/ET/FM OR (subscription is SX/GM and check is disabled)
     *
     * @param disableCheckUnresponsiveSX true : check is disabled for SX/GM subs
     *                                   false:  check is enabled
     * @return true : subscription is eligible. Unresponsive check must be done on this subscription
     * * false : Unresponsive check must not be done
     */
    public static Predicate<SubscriptionSetup> isApplicableToUnresponsiveTest(boolean disableCheckUnresponsiveSX) {
        List<SiriDataType> mandatoryCheckTypes = List.of(SiriDataType.ESTIMATED_TIMETABLE, SiriDataType.STOP_MONITORING, SiriDataType.VEHICLE_MONITORING, SiriDataType.FACILITY_MONITORING);
        return subscription -> {
            return mandatoryCheckTypes.contains(subscription.getSubscriptionType()) || !disableCheckUnresponsiveSX;
        };
    }
}
