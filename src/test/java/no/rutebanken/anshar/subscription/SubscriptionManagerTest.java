/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SubscriptionManagerTest extends SpringBootBaseTest {

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Value("${anshar.healthcheck.interval.factor:12}")
    private int healthcheckIntervalFactor;

    @BeforeEach
    public void init() {
        subscriptionManager.subscriptions.clear();
    }

    @Test
    public void activeSubscriptionIsHealthy()  {
        long subscriptionDurationSec = 1;
        SubscriptionSetup subscriptionSoonToExpire = createSubscription(subscriptionDurationSec);
        String subscriptionId = UUID.randomUUID().toString();
        subscriptionManager.addSubscription(subscriptionId, subscriptionSoonToExpire);
        subscriptionManager.activatePendingSubscription(subscriptionId);
        subscriptionManager.touchSubscription(subscriptionId);

        assertTrue(subscriptionManager.isSubscriptionHealthy(subscriptionId));
    }

    @Test
    public void activeSubscriptionNoHeartbeat() throws InterruptedException {
        long subscriptionDurationSec = 180;
        SubscriptionSetup activeSubscription = createSubscription(subscriptionDurationSec, Duration.ofMillis(150));
        String subscriptionId = UUID.randomUUID().toString();
        subscriptionManager.addSubscription(subscriptionId, activeSubscription);
        subscriptionManager.activatePendingSubscription(subscriptionId);
        subscriptionManager.touchSubscription(subscriptionId);

        assertTrue(subscriptionManager.isSubscriptionHealthy(subscriptionId));

        Thread.sleep(activeSubscription.getHeartbeatInterval().toMillis()* healthcheckIntervalFactor +150);

        assertFalse(subscriptionManager.isSubscriptionHealthy(subscriptionId));
    }

    @Test
    public void pendingSubscriptionIsHealthy() throws InterruptedException {
        long subscriptionDurationSec = 1;
        SubscriptionSetup pendingSubscription = createSubscription(subscriptionDurationSec, Duration.ofMillis(150));
        pendingSubscription.setActive(false);
        String subscriptionId = UUID.randomUUID().toString();
        subscriptionManager.addSubscription(subscriptionId, pendingSubscription);

        subscriptionManager.activatePendingSubscription(subscriptionId);
        subscriptionManager.touchSubscription(subscriptionId);

        assertTrue(subscriptionManager.isSubscriptionHealthy(subscriptionId));

        Thread.sleep(pendingSubscription.getHeartbeatInterval().toMillis()* healthcheckIntervalFactor +150);

        assertFalse(subscriptionManager.isSubscriptionHealthy(subscriptionId));
    }

    @Test
    public void notStartedSubscriptionIsHealthy() throws InterruptedException {

        long subscriptionDurationSec = 1;
        SubscriptionSetup pendingSubscription = createSubscription(subscriptionDurationSec, Duration.ofMillis(150));
        pendingSubscription.setActive(false);
        String subscriptionId = UUID.randomUUID().toString();
        subscriptionManager.addSubscription(subscriptionId, pendingSubscription);

        assertTrue(subscriptionManager.isSubscriptionHealthy(subscriptionId));

        Thread.sleep(pendingSubscription.getHeartbeatInterval().toMillis()* healthcheckIntervalFactor + 150);

        assertTrue(subscriptionManager.isSubscriptionHealthy(subscriptionId));
    }


    @Test
    public void testAutomaticRestartTrigger() throws InterruptedException {

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        String restartTime = ZonedDateTime.now().plusMinutes(2).format(timeFormatter);

        SubscriptionSetup subscription = createSubscription(1000000L);

        subscription.setRestartTime(restartTime);

        String subscriptionId = subscription.getSubscriptionId();

        subscriptionManager.addSubscription(subscriptionId, subscription);
        subscriptionManager.activatedTimestamp.set(subscriptionId, Instant.now().minusSeconds(3600));

        assertTrue(subscriptionManager.isSubscriptionHealthy(subscriptionId));

        restartTime = ZonedDateTime.now().minusMinutes(2).format(timeFormatter);
        subscription.setRestartTime(restartTime);
        subscriptionManager.addSubscription(subscriptionId, subscription);
        subscriptionManager.activatedTimestamp.set(subscriptionId, Instant.now().minusSeconds(3600));

        assertFalse(subscriptionManager.isSubscriptionHealthy(subscriptionId));
        assertTrue(subscriptionManager.isForceRestart(subscriptionId));
    }

    @Test
    public void testForceRestartTrigger() throws InterruptedException {

        SubscriptionSetup subscription = createSubscription(1000000L);

        String subscriptionId = subscription.getSubscriptionId();

        subscriptionManager.addSubscription(subscriptionId, subscription);
        subscriptionManager.activatedTimestamp.set(subscriptionId, Instant.now().minusSeconds(3600));

        assertTrue(subscriptionManager.isSubscriptionHealthy(subscriptionId));

        subscriptionManager.forceRestart(subscriptionId);

        assertTrue(subscriptionManager.isForceRestart(subscriptionId));

        // Triggered restart does not affect health-status
        assertTrue(subscriptionManager.isSubscriptionHealthy(subscriptionId));

        //When a force restart is triggered, it is only triggered once
        assertFalse(subscriptionManager.isForceRestart(subscriptionId));
    }

    @Test
    public void testCheckStatusResponseOK() throws InterruptedException {
        long subscriptionDurationSec = 180;
        SubscriptionSetup subscription = createSubscription(subscriptionDurationSec);
        subscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);
        subscriptionManager.activatePendingSubscription(subscription.getSubscriptionId());

        ZonedDateTime serviceStartedTime = ZonedDateTime.now().minusMinutes(1);
        boolean touched = subscriptionManager.touchSubscription(subscription.getSubscriptionId(), serviceStartedTime,null);
        assertTrue(touched);
        assertTrue(subscriptionManager.isSubscriptionHealthy(subscription.getSubscriptionId()));

    }

    @Test
    public void testAddSubscription() {
        SubscriptionSetup subscription = createSubscription(1);
        assertFalse(subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()), "Subscription already marked as registered");
        subscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);
        subscriptionManager.activatePendingSubscription(subscription.getSubscriptionId());

        assertTrue(subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()), "Subscription not marked as registered");
        assertTrue(subscriptionManager.isActiveSubscription(subscription.getSubscriptionId()), "Subscription not marked as active");


        assertNotNull(subscriptionManager.get(subscription.getSubscriptionId()), "Subscription not found");
    }

    @Test
    public void testAddAndActivatePendingSubscription() {
        SubscriptionSetup subscription = createSubscription(1);
        assertFalse(subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()), "Unknown subscription has been found");
        subscription.setActive(false);

        subscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);

        assertNotNull(
            subscriptionManager.get(subscription.getSubscriptionId()),
            "Pending subscription not found"
        );

        assertTrue(
            subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()),
            "Subscription not marked as registered"
        );
        assertFalse(
            subscriptionManager.isActiveSubscription(subscription.getSubscriptionId()),
            "Subscription marked as active"
        );
        assertTrue(
            subscriptionManager.isSubscriptionHealthy(subscription.getSubscriptionId()),
            "Subscription not healthy"
        );

        assertTrue(
            subscriptionManager.activatePendingSubscription(subscription.getSubscriptionId()),
            "Activating pending subscription not returning successfully"
        );

        //Activating already activated subscription should be ignored
        assertTrue(
            subscriptionManager.activatePendingSubscription(subscription.getSubscriptionId()),
            "Activating already activated subscription not returning successfully"
        );

        assertTrue(
            subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()),
            "Subscription not marked as registered"
        );
        assertTrue(
            subscriptionManager.isActiveSubscription(subscription.getSubscriptionId()),
            "Subscription not marked as active"
        );
    }

    @Test
    public void testAddAndTouchPendingSubscription() {
        SubscriptionSetup subscription = createSubscription(1);
        subscription.setActive(false);
        assertFalse(subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));

        subscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);

        assertTrue(
            subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()),
            "Subscription not marked as registered"
        );
        assertFalse(
            subscriptionManager.isActiveSubscription(subscription.getSubscriptionId()),
            "Subscription marked as active"
        );

        assertTrue(
            subscriptionManager.isSubscriptionHealthy(subscription.getSubscriptionId()),
            "Subscription not healthy"
        );

        subscriptionManager.touchSubscription(subscription.getSubscriptionId());

        assertTrue(
            subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()),
            "Subscription not marked as registered"
        );
        assertFalse(
            subscriptionManager.isActiveSubscription(subscription.getSubscriptionId()),
            "Subscription marked as active"
        );
    }

    @Test
    public void testRemoveSubscription() {
        SubscriptionSetup subscription = createSubscription(1);
        assertFalse(subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));

        subscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);
        subscriptionManager.activatePendingSubscription(subscription.getSubscriptionId());

        assertTrue(
            subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()),
            "Subscription not registered"
        );
        assertTrue(
            subscriptionManager.isActiveSubscription(subscription.getSubscriptionId()),
            "Subscription not marked as active"
        );

        subscriptionManager.removeSubscription(subscription.getSubscriptionId());
        assertFalse(
            subscriptionManager.isActiveSubscription(subscription.getSubscriptionId()),
            "Removed subscription marked as active"
        );
    }

    @Test
    public void testForceRemoveSubscription() {
        SubscriptionSetup subscription = createSubscription(1);
        assertFalse(subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));

        subscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);
        subscriptionManager.activatePendingSubscription(subscription.getSubscriptionId());

        subscriptionManager.removeSubscription(subscription.getSubscriptionId(), true);
        assertFalse(
            subscriptionManager.isActiveSubscription(subscription.getSubscriptionId()),
            "Removed subscription marked as active"
        );
    }

    @Test
    public void testStatsObjectCounterHugeNumber() {
        SubscriptionSetup subscription = createSubscription(1);
        assertFalse(subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));

        subscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);
        subscriptionManager.activatePendingSubscription(subscription.getSubscriptionId());

        for (int i = 0; i < 10; i++) {
            subscriptionManager.incrementObjectCounter(subscription, Integer.MAX_VALUE);
        }

        JSONObject jsonObject = subscriptionManager.buildStats();

        assertNotNull( jsonObject.get("types"));
        assertTrue(jsonObject.get("types") instanceof JSONArray);

        JSONArray types = (JSONArray) jsonObject.get("types");

        JSONArray subscriptions = new JSONArray();
        for (int i = 0; i < types.size(); i++) {
            subscriptions.addAll((JSONArray) ((JSONObject)types.get(i)).get("subscriptions"));
        }

        boolean verifiedCounter = false;
        for (Object object : subscriptions) {
            JSONObject jsonStats = (JSONObject) object;
            if (subscription.getSubscriptionId().equals(jsonStats.get("subscriptionId"))) {
                assertNotNull(jsonStats.get("objectcount"));
                assertTrue(jsonStats.get("objectcount").toString().length() > String.valueOf(Integer.MAX_VALUE).length());
                verifiedCounter = true;
            }
        }
        assertTrue(verifiedCounter, "Counter has not been verified");
    }

    @Test
    public void testStatByteCounter() {
        SubscriptionSetup subscription = createSubscription(1);
        assertFalse(subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));

        subscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);
        subscriptionManager.activatePendingSubscription(subscription.getSubscriptionId());

        int sum = 0;
        int increment = 999;
        for (int i = 1; i < 10;i++) {
            sum += increment;
            subscriptionManager.incrementObjectCounter(subscription, increment);
        }

        JSONObject jsonObject = subscriptionManager.buildStats();
        assertNotNull(jsonObject.get("types"));
        assertTrue(jsonObject.get("types") instanceof JSONArray);

        JSONArray types = (JSONArray) jsonObject.get("types");

        JSONArray subscriptions = new JSONArray();
        for (int i = 0; i < types.size(); i++) {
            subscriptions.addAll((JSONArray) ((JSONObject)types.get(i)).get("subscriptions"));
        }
        assertTrue(subscriptions.size() > 0);

        boolean verifiedCounter = false;
        for (Object object : subscriptions) {
            JSONObject jsonStats = (JSONObject) object;
            if (subscription.getSubscriptionId().equals(jsonStats.get("subscriptionId"))) {
                assertEquals("" + sum, "" + jsonStats.get("objectcount"));
                verifiedCounter = true;
            }
        }
        assertTrue(verifiedCounter, "Counter has not been verified");
    }

    @Test
    public void testIsSubscriptionRegistered() {

        assertFalse(
            subscriptionManager.activatePendingSubscription("RandomSubscriptionId"),
            "Unknown subscription has been activated"
        );
        assertFalse(
            subscriptionManager.isSubscriptionRegistered("RandomSubscriptionId"),
            "Unknown subscription reported as registered"
        );
    }

    @Test
    public void testAddSubscriptionAndReceivingData() {
        SubscriptionSetup subscription = createSubscription(1000);
        subscription.setVendor("VIPVendor");
        subscription.setActive(true);

        String subscriptionId = subscription.getSubscriptionId();
        subscriptionManager.addSubscription(subscriptionId, subscription);
        assertTrue(subscriptionManager.isSubscriptionHealthy(subscriptionId));
        subscriptionManager.dataReceived(subscriptionId);

        Set<String> allUnhealthySubscriptions = subscriptionManager.getAllUnhealthySubscriptions(1);
        assertFalse(allUnhealthySubscriptions.contains(subscription.getVendor()));
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Set<String> allUnhealthySubscriptions_2 = subscriptionManager.getAllUnhealthySubscriptions(0);
        assertTrue(allUnhealthySubscriptions_2.contains(subscription.getVendor()));
    }

    private SubscriptionSetup createSubscription(long initialDuration) {
        return createSubscription(initialDuration, Duration.ofMinutes(4));
    }

    private SubscriptionSetup createSubscription(long initialDuration, Duration heartbeatInterval) {
        return new SubscriptionSetup(
                SiriDataType.SITUATION_EXCHANGE,
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                "http://localhost",
                heartbeatInterval,
                Duration.ofHours(1),
                "http://www.kolumbus.no/siri",
                new HashMap<>(),
                "1.4",
                "SwarcoMizar",
                "tst",
                SubscriptionSetup.ServiceType.SOAP,
                new ArrayList<>(),
                new HashMap<>(),
                new ArrayList<>(),
                UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofSeconds(initialDuration),
                true
        );
    }
}
