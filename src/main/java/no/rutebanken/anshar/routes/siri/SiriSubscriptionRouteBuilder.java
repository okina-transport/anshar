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

package no.rutebanken.anshar.routes.siri;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.DataNotReceivedAction;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http.HttpMethods;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.time.Duration;
import java.time.Instant;

@Component
public abstract class SiriSubscriptionRouteBuilder extends BaseRouteBuilder {


    NamespacePrefixMapper customNamespacePrefixMapper;

    SubscriptionSetup subscriptionSetup;


    public static final String START_ROUTE_PREFIX = "start.";
    public static final String CANCEL_ROUTE_PREFIX = "cancel.";
    public static final String MONITOR_ROUTE_PREFIX = "monitor.subscription.";
    public static final String SERVICE_REQUEST_ROUTE_PREFIX = "service.request.";

    @Autowired
    EstimatedTimetables estimatedTimetables;

    boolean hasBeenStarted;


    public static String getStartRouteId(SubscriptionSetup subscriptionSetup) {
        return START_ROUTE_PREFIX + subscriptionSetup.getSubscriptionId();
    }


    public static String getCancelRouteId(SubscriptionSetup subscriptionSetup) {
        return CANCEL_ROUTE_PREFIX + subscriptionSetup.getSubscriptionId();
    }

    public static String getMonitorRouteId(SubscriptionSetup subscriptionSetup) {
        return MONITOR_ROUTE_PREFIX + subscriptionSetup.getSubscriptionId();
    }

    public static String getServiceRequestRouteId(SubscriptionSetup subscriptionSetup) {
        return SERVICE_REQUEST_ROUTE_PREFIX + subscriptionSetup.getSubscriptionId();
    }

    public SiriSubscriptionRouteBuilder(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
        estimatedTimetables = ApplicationContextHolder.getContext().getBean(EstimatedTimetables.class);
    }

    String getTimeout() {
        int timeout;
        Duration heartbeatInterval = subscriptionSetup.getHeartbeatInterval();
        if (heartbeatInterval != null) {
            long heartbeatIntervalMillis = heartbeatInterval.toMillis();
            timeout = (int) heartbeatIntervalMillis / 2;
        } else {
            timeout = 30000;
        }

        return "?httpClient.responseTimeout=" + timeout + "&httpClient.connectTimeout=" + timeout;
    }

    protected Processor addCustomHeaders() {
        return exchange -> {
            if (subscriptionSetup.getCustomHeaders() != null && !subscriptionSetup.getCustomHeaders().isEmpty()) {
                exchange.getOut().setHeaders(exchange.getIn().getHeaders());
                exchange.getOut().setBody(exchange.getIn().getBody());
                exchange.getOut().setMessageId(exchange.getIn().getMessageId());
                exchange.getOut().getHeaders().putAll(subscriptionSetup.getCustomHeaders());
            }
        };
    }

}