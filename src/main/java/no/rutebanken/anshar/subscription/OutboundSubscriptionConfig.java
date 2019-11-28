package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.List;

@PropertySource(value = "${anshar.outbound.subscriptions.config.path}", factory = YamlPropertySourceFactory.class)
@ConfigurationProperties(prefix = "anshar")
@Configuration
public class OutboundSubscriptionConfig {

    private List<OutboundSubscriptionSetup> outboundSubscriptions;

    public List<OutboundSubscriptionSetup> getOutboundSubscriptions() {
        return outboundSubscriptions;
    }

    public void setOutboundSubscriptions(List<OutboundSubscriptionSetup> outboundSubscriptions) {
        this.outboundSubscriptions = outboundSubscriptions;
    }
}
