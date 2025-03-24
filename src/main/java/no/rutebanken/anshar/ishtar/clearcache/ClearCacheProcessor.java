package no.rutebanken.anshar.ishtar.clearcache;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.*;
import no.rutebanken.anshar.kishar.KisharClient;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static no.rutebanken.anshar.routes.HttpParameter.PARAM_RESPONSE_CODE;
import static no.rutebanken.anshar.routes.validation.validators.Constants.DATASET_ID_HEADER_NAME;

@Component
public class ClearCacheProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(ClearCacheProcessor.class);

    private final AnsharConfiguration config;
    private final EstimatedTimetables et;
    private final FacilityMonitoring fm;
    private final GeneralMessages gm;
    private final GeneralMessagesCancellations gmc;
    private final MonitoredStopVisits sm;
    private final Situations sx;
    private final VehicleActivities vm;
    private final KisharClient kisharClient;

    public ClearCacheProcessor(AnsharConfiguration config, EstimatedTimetables et, FacilityMonitoring fm, GeneralMessages gm, GeneralMessagesCancellations gmc, MonitoredStopVisits sm, Situations sx, VehicleActivities vm, KisharClient kisharClient) {
        this.config = config;
        this.et = et;
        this.fm = fm;
        this.gm = gm;
        this.gmc = gmc;
        this.sm = sm;
        this.sx = sx;
        this.vm = vm;
        this.kisharClient = kisharClient;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (!config.isCurrentInstanceLeader()) {
            log.info("Current instance is not leader, abort processing");
            return;
        }
        String datasetId = exchange.getIn().getHeader(DATASET_ID_HEADER_NAME, String.class);
        if (StringUtils.isBlank(datasetId)) {
            log.error("Missing datasetId header, abort processing");
            exchange.getIn().setHeader(PARAM_RESPONSE_CODE, 400);
            exchange.getIn().setBody("Missing datasetId");
            return;
        }
        log.info("Start clearing cache for dataset {}", datasetId);
        if (config.processET()) {
            et.clearAllByDatasetId(datasetId);
        }
        if (config.processFM()) {
            fm.clearAllByDatasetId(datasetId);
        }
        if (config.processGM()) {
            gm.clearAllByDatasetId(datasetId);
            gmc.clearAllByDatasetId(datasetId);
        }
        if(config.processSM()) {
            sm.clearAllByDatasetId(datasetId);
        }
        if (config.processSX()) {
            sx.clearAllByDatasetId(datasetId);
        }
        if (config.processVM()) {
            vm.clearAllByDatasetId(datasetId);
        }
        try {
            this.kisharClient.clearCacheByDatasetId(datasetId);
        } catch (Exception e) {
            // no big deal if KISHAR cache clearing fails
            log.error("Error clearing KISHAR cache for dataset {}", datasetId);
            log.debug("Error clearing KISHAR cache", e);
        }
        exchange.getIn().setHeader(PARAM_RESPONSE_CODE, 204);
    }
}
