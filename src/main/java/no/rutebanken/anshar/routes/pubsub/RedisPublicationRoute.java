package no.rutebanken.anshar.routes.pubsub;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.redis.RedisConstants;
import org.apache.commons.lang3.StringUtils;
import org.rutebanken.siri20.util.SiriJson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.org.siri.siri20.ServiceDelivery;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.VehicleActivityStructure;
import uk.org.siri.siri20.VehicleMonitoringDeliveryStructure;

import java.time.ZonedDateTime;

@Component
public class RedisPublicationRoute extends RouteBuilder {


    @Value("${spring.redis.host:0.0.0.0}")
    protected String redisHost;

    @Value("${spring.redis.port:6379}")
    protected String redisPort;

    String vmChannel = "vmPublication";

    @Value("${anshar.redis.additionnal.network.vm}")
    protected String additionalNetworkVM;

    @Override
    public void configure() throws Exception {

        from("direct:sendVMToRedis")
                .setHeader(RedisConstants.CHANNEL, constant(vmChannel))
                .process(e -> {
                    VehicleActivityStructure vehAct = e.getIn().getBody(VehicleActivityStructure.class);


                    if (StringUtils.isNotEmpty(additionalNetworkVM) && vehAct.getVehicleMonitoringRef() != null) {
                        String oldVehicleRef = vehAct.getVehicleMonitoringRef().getValue();
                        vehAct.getVehicleMonitoringRef().setValue(additionalNetworkVM + ":" + oldVehicleRef);
                    }

                    Siri siriResp = new Siri();
                    ServiceDelivery serviceDelivery = new ServiceDelivery();
                    serviceDelivery.setResponseTimestamp(ZonedDateTime.now());
                    VehicleMonitoringDeliveryStructure vehMonDelStruct = new VehicleMonitoringDeliveryStructure();
                    vehMonDelStruct.getVehicleActivities().add(vehAct);
                    serviceDelivery.getVehicleMonitoringDeliveries().add(vehMonDelStruct);
                    siriResp.setServiceDelivery(serviceDelivery);
                    String jsonMsg = SiriJson.toJson(siriResp);
                    e.getIn().setBody(jsonMsg);
                })
                .to("direct:sendMessageToRedis");


        from("direct:sendMessageToRedis")
                .log(String.format("Publishing with redis in channel: %s, message body: ${body}", header(RedisConstants.CHANNEL)))
                .setHeader(RedisConstants.MESSAGE, body())
                .to(String.format("spring-redis://%s:%s?connectionFactory=#customRedisConnectionFactory&redisTemplate=#customTemplate&command=PUBLISH", redisHost, redisPort));
    }
}
