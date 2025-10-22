package no.rutebanken.anshar.health;

import no.rutebanken.anshar.api.FlowStatus;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.health.DailyStatus;
import no.rutebanken.anshar.routes.health.IncomingDataHealthService;
import no.rutebanken.anshar.routes.health.IncomingFlowParameters;
import no.rutebanken.anshar.routes.health.IncomingFlowType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;


public class IncomingDataHealthServiceTest extends SpringBootBaseTest {

    @Autowired
    private IncomingDataHealthService incomingDataHealthService;

    @Test
    public void testIncomingDataHealthService() {

        incomingDataHealthService.clearDailyStatuses();

        /// Adding first status
        String url1 = "http://url1";
        IncomingFlowParameters flowParameters = new IncomingFlowParameters();
        flowParameters.setUrl(url1);
        flowParameters.setType(IncomingFlowType.GTFS);
        incomingDataHealthService.recordStatus(flowParameters, FlowStatus.OK);
        Assertions.assertEquals(1, incomingDataHealthService.getDailyStatuses().size());
        Assertions.assertEquals(DailyStatus.GREEN, incomingDataHealthService.getDailyStatuses().get(flowParameters));


        /// Adding second status KO
        String url2 = "http://url2";
        IncomingFlowParameters flowParameters2 = new IncomingFlowParameters();
        flowParameters2.setUrl(url2);
        flowParameters2.setType(IncomingFlowType.GTFS);
        incomingDataHealthService.recordStatus(flowParameters2, FlowStatus.ERROR);
        Assertions.assertEquals(2, incomingDataHealthService.getDailyStatuses().size());
        Assertions.assertEquals(DailyStatus.GREEN, incomingDataHealthService.getDailyStatuses().get(flowParameters));
        Assertions.assertEquals(DailyStatus.RED, incomingDataHealthService.getDailyStatuses().get(flowParameters2));

        /// First became KO
        incomingDataHealthService.recordStatus(flowParameters, FlowStatus.ERROR);
        Assertions.assertEquals(2, incomingDataHealthService.getDailyStatuses().size());
        Assertions.assertEquals(DailyStatus.RED, incomingDataHealthService.getDailyStatuses().get(flowParameters));
        Assertions.assertEquals(DailyStatus.RED, incomingDataHealthService.getDailyStatuses().get(flowParameters2));


        /// First became ok  again
        incomingDataHealthService.recordStatus(flowParameters, FlowStatus.OK);
        Assertions.assertEquals(2, incomingDataHealthService.getDailyStatuses().size());
        Assertions.assertEquals(DailyStatus.GREEN, incomingDataHealthService.getDailyStatuses().get(flowParameters));
        Assertions.assertEquals(DailyStatus.RED, incomingDataHealthService.getDailyStatuses().get(flowParameters2));

        /// second became ok
        incomingDataHealthService.recordStatus(flowParameters2, FlowStatus.OK);
        Assertions.assertEquals(2, incomingDataHealthService.getDailyStatuses().size());
        Assertions.assertEquals(DailyStatus.GREEN, incomingDataHealthService.getDailyStatuses().get(flowParameters));
        Assertions.assertEquals(DailyStatus.GREEN, incomingDataHealthService.getDailyStatuses().get(flowParameters2));

    }
}
