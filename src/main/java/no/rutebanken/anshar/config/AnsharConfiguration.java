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

package no.rutebanken.anshar.config;

import com.hazelcast.map.IMap;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.TimeZone;

@Configuration
@Getter
@Setter
public class AnsharConfiguration {

    private static final String CURRENT_INSTANCE_LEADER_KEY = "currentInstanceLeader";

    private static final Logger logger = LoggerFactory.getLogger(AnsharConfiguration.class);
    @Value("${anshar.disable.subscription.healthcheck:false}")
    boolean isHealthcheckDisabled;
    @Value("${rutebanken.kubernetes.url:}")
    private String kubernetesUrl;
    @Value("${rutebanken.kubernetes.enabled:true}")
    private boolean kubernetesEnabled;
    @Value("${rutebanken.kubernetes.namespace:default}")
    private String namespace;
    @Value("${rutebanken.hazelcast.management.url:}")
    private String hazelcastManagementUrl;
    @Value("${anshar.incoming.port}")
    private String inboundPort;
    @Value("${anshar.incoming.concurrentConsumers}")
    private long concurrentConsumers;
    @Value("${anshar.incoming.logdirectory}")
    private String incomingLogDirectory = "/tmp";
    @Value("${anshar.inbound.pattern}")
    private String incomingPathPattern;
    @Value("${anshar.inbound.url}")
    private String inboundUrl = "http://localhost:8080";
    @Value("${anshar.healthcheck.interval.seconds}")
    private int healthCheckInterval = 30;
    @Value("${anshar.environment}")
    private String environment;
    @Value("${anshar.default.max.elements.per.delivery:1500}")
    private int defaultMaxSize;
    @Value("${anshar.outbound.polling.tracking.period.minutes:30}")
    private int trackingPeriodMinutes;
    @Value("${anshar.outbound.adhoc.tracking.period.minutes:3}")
    private int adHocTrackingPeriodMinutes;
    @Value("${anshar.siri.default.producerRef:OKI}")
    private String producerRef;
    @Value("${anshar.siri.sx.graceperiod.minutes:0}")
    private long sxGraceperiodMinutes;
    @Value("${anshar.siri.et.graceperiod.minutes:0}")
    private long etGraceperiodMinutes;
    @Value("${anshar.siri.vm.graceperiod.minutes:0}")
    private long vmGraceperiodMinutes;
    @Value("${anshar.siri.sm.graceperiod.minutes:0}")
    private long smGraceperiodMinutes;
    @Value("${anshar.siri.fm.graceperiod.minutes:0}")
    private long fmGraceperiodMinutes;
    @Value("${anshar.validation.profile.enabled}")
    private boolean profileValidation;
    @Value("${anshar.validation.enabled:false}")
    private boolean fullValidationEnabled;
    @Value("${anshar.validation.profile.name}")
    private String validationProfileName;
    @Value("${anshar.tracking.header.required.post:false}")
    private boolean trackingHeaderRequiredforPost;
    @Value("${anshar.tracking.header.required.get:false}")
    private boolean trackingHeaderRequiredForGet;
    @Value("${anshar.tracking.header.name:Client-Name}")
    private String trackingHeaderName;
    @Value("${anshar.validation.total.max.size.mb:4}")
    private int maxTotalXmlSizeOfValidation;
    @Value("${anshar.validation.total.max.count:10}")
    private int maxNumberOfValidations;
    @Value("${anshar.validation.data.persist.hours:6}")
    private int numberOfHoursToKeepValidation;
    @Value("${anshar.tracking.data.buffer.commit.frequency.seconds:2}")
    private int changeBufferCommitFrequency;
    @Value("${anshar.message.queue.camel.route.prefix}")
    private String messageQueueCamelRoutePrefix;
    @Value("${anshar.admin.blocked.clients:}")
    private List<String> blockedEtClientNames;
    @Value("${anshar.application.mode:}")
    private List<AppMode> appModes;

    @Autowired
    @Qualifier("getLockMap")
    private IMap<String, Instant> lockMap;

    @Value("${anshar.default.time.zone}")
    private String defaultTimeZone;

    private Boolean isCurrentInstanceLeader;

    public boolean processET() {
        return (appModes.isEmpty() || appModes.contains(AppMode.DATA_ET));
    }

    public boolean processVM() {
        return (appModes.isEmpty() || appModes.contains(AppMode.DATA_VM));
    }

    public boolean processSX() {
        return (appModes.isEmpty() || appModes.contains(AppMode.DATA_SX));
    }

    public boolean processSM() {
        return (appModes.isEmpty() || appModes.contains(AppMode.DATA_SM));
    }

    public boolean processGM() {
        return (appModes.isEmpty() || appModes.contains(AppMode.DATA_GM));
    }

    public boolean processFM() {
        return (appModes.isEmpty() || appModes.contains(AppMode.DATA_FM));
    }

    public boolean processAdmin() {
        return (appModes.isEmpty() || appModes.contains(AppMode.PROXY));
    }

    public boolean processData() {
        return (appModes.isEmpty() || ((appModes.contains(AppMode.DATA_ET) | appModes.contains(AppMode.DATA_VM) | appModes.contains(AppMode.DATA_SX))));
    }

    public boolean isHealthcheckDisabled() {
        return isHealthcheckDisabled;
    }

    public boolean isCurrentInstanceLeader() {

        if (isCurrentInstanceLeader == null) {
            initCurrentInstanceLeader();
        }
        return isCurrentInstanceLeader;
    }

    private void initCurrentInstanceLeader() {
        if (!lockMap.containsKey(CURRENT_INSTANCE_LEADER_KEY)) {
            lockMap.set(CURRENT_INSTANCE_LEADER_KEY, Instant.now());
            isCurrentInstanceLeader = true;
            logger.info("=====> Current instance is leader. Will launch all GTFS-RT or SIRI Requests   <=================");
        } else {
            isCurrentInstanceLeader = false;
            logger.info("=====> Current instance is not leader. Will not launch any GTFS-RT or SIRI Requests   <=================");
        }
    }

    @PostConstruct
    public void init() {
        System.setProperty("default.time.zone", defaultTimeZone);
        TimeZone.setDefault(TimeZone.getTimeZone(defaultTimeZone));
    }
}
