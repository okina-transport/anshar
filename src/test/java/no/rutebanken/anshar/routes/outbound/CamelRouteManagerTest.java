package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.App;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.org.siri.siri20.CourseOfJourneyRefStructure;
import uk.org.siri.siri20.DirectionRefStructure;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.LocationStructure;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.SubscriptionRequest;
import uk.org.siri.siri20.VehicleActivityStructure;
import uk.org.siri.siri20.VehicleMonitoringRefStructure;
import uk.org.siri.siri20.VehicleRef;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@CamelSpringBootTest
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.NONE, classes = App.class)
public class CamelRouteManagerTest {

    static final String TEST_SUBSCRIPTION_ID = "test.subscription.id";

    @Autowired
    private SiriHelper siriHelper;

    @Autowired
    private CamelRouteManager camelRouteManager;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    private static final int DEFAULT_HEARTBEAT_SECONDS = 300;

    protected String url="http://defURL";
    protected String prefix = "TEST";
    protected SiriDataType dataType = SiriDataType.VEHICLE_MONITORING;
    protected RequestType requestType = RequestType.GET_VEHICLE_MONITORING;

    @Autowired
    protected ProducerTemplate siriSubscriptionProcessor;


//    @Test
//    void pushSiriData() throws InterruptedException {
//        Siri vmSubscriptionRequest = SiriObjectFactory.createSubscriptionRequest(createStandardSubscription("TestObjectRef", "TestDatasetId"));
//        final Siri siriVM = serverSubscriptionManager.handleSubscriptionRequest(vmSubscriptionRequest.getSubscriptionRequest(), "TestDatasetId", null, null);
//        VehicleActivityStructure vehicleActivityStructure = createVehicleActivityStructure(ZonedDateTime.now(), "TestVehicleReference", "TestDatasource");
//        Siri payload = siriObjectFactory.createVMServiceDelivery(Collections.singleton(vehicleActivityStructure));
//        camelRouteManager.pushSiriData(payload, getOutboundSubscriptionSetup(), true);
//        Thread.sleep(10000);
//    }
//
//    OutboundSubscriptionSetup getOutboundSubscriptionSetup() {
//        return new OutboundSubscriptionSetup(
//                ZonedDateTime.now(),
//                SiriDataType.VEHICLE_MONITORING,
//                "https://test.fr",
//                0,
//                true,
//                0,
//                30000,
//                siriHelper.getFilter(new SubscriptionRequest(), OutboundIdMappingPolicy.DEFAULT, "TestDatasetId"),
//                new ArrayList<>(),
//                "TestSubscriptionId",
//                "TestRequestorRef",
//                ZonedDateTime.now(),
//                "TestDatasetId",
//                "TestClientTrackingName"
//        );
//    }
//
//
//
//    private VehicleActivityStructure createVehicleActivityStructure(ZonedDateTime recordedAtTime, String vehicleReference, String dataSource) {
//        VehicleActivityStructure element = new VehicleActivityStructure();
//        element.setRecordedAtTime(recordedAtTime);
//        element.setValidUntilTime(recordedAtTime.plusMinutes(10));
//
//        VehicleActivityStructure.MonitoredVehicleJourney vehicleJourney = new VehicleActivityStructure.MonitoredVehicleJourney();
//
//        VehicleRef vRef = new VehicleRef();
//        vRef.setValue(vehicleReference);
//        vehicleJourney.setVehicleRef(vRef);
//        VehicleMonitoringRefStructure vehicleMonitoringRef = new VehicleMonitoringRefStructure();
//        vehicleMonitoringRef.setValue(vehicleReference);
//        element.setVehicleMonitoringRef(vehicleMonitoringRef);
//
//        CourseOfJourneyRefStructure journeyRefStructure = new CourseOfJourneyRefStructure();
//        journeyRefStructure.setValue("yadayada");
//        vehicleJourney.setCourseOfJourneyRef(journeyRefStructure);
//
//        DirectionRefStructure directionRef = new DirectionRefStructure();
//        directionRef.setValue("1");
//        vehicleJourney.setDirectionRef(directionRef);
//
//
//        LineRef lineRef = new LineRef();
//        lineRef.setValue("TEST:Line:1");
//        vehicleJourney.setLineRef(lineRef);
//
//        vehicleJourney.setDataSource(dataSource);
//
//        LocationStructure location = new LocationStructure();
//        location.setLatitude(BigDecimal.valueOf(10.63));
//        location.setLongitude(BigDecimal.valueOf(63.10));
//        vehicleJourney.setVehicleLocation(location);
//
//
//        element.setMonitoredVehicleJourney(vehicleJourney);
//        return element;
//    }
//
//    private SubscriptionSetup createStandardSubscription(String objectRef, String datasetId){
//        SubscriptionSetup setup = new SubscriptionSetup();
//        setup.setDatasetId(datasetId);
//        setup.setHeartbeatIntervalSeconds(DEFAULT_HEARTBEAT_SECONDS);
//        setup.setRequestorRef("TestRequestorRef");
//        setup.setAddress(url);
//        setup.setServiceType(SubscriptionSetup.ServiceType.REST);
//        setup.setSubscriptionMode(SubscriptionSetup.SubscriptionMode.SUBSCRIBE);
//        setup.setDurationOfSubscriptionHours(24);
//        setup.setVendor("OKINA");
//        setup.setContentType("TEST");
//        setup.setActive(true);
//        setup.setIncrementalUpdates(true);
//        setup.setUpdateIntervalSeconds(0);
//        setup.setChangeBeforeUpdatesSeconds(0);
//
//        String subscriptionId = prefix + objectRef;
//        setup.setName(subscriptionId);
//        setup.setSubscriptionType(dataType);
//        setup.setSubscriptionId(subscriptionId);
//        Map<RequestType, String> urlMap = new HashMap<>();
//        urlMap.put(requestType,url);
//        setup.setUrlMap(urlMap);
//
//
//        return setup;
//    }
}