package no.rutebanken.anshar.gtfsrt.ingesters;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.MonitoredStopVisit;
import uk.org.siri.siri20.Siri;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static no.rutebanken.anshar.routes.validation.validators.Constants.*;

@Service
public class StopMonitoringIngester extends RestRouteBuilder {

    private final Logger logger = LoggerFactory.getLogger(StopMonitoringIngester.class);

    @Autowired
    AnsharConfiguration configuration;

    @Autowired
    private SiriHandler handler;

    @Autowired
    private SubscriptionManager subscriptionManager;

    private static JAXBContext jaxbContext;

    private static XMLInputFactory xmlif;

    private static XMLStreamReader xmler;

    private static Unmarshaller jaxbUnmarshaller;




    public void processIncomingSMFromGTFSRT(Exchange e) throws JAXBException {
        InputStream xml = e.getIn().getBody(InputStream.class);

        if (jaxbContext == null) {
            jaxbContext = JAXBContext.newInstance(MonitoredStopVisit.class);
        }

        try {
            MonitoredStopVisit stopVisit = parseXml(xml);


            List<MonitoredStopVisit> stopVisitsToIngest = new ArrayList<>();
            stopVisitsToIngest.add(stopVisit);



            String datasetId = e.getIn().getHeader(DATASET_ID_HEADER_NAME, String.class);
            String url = e.getIn().getHeader(URL_HEADER_NAME, String.class);

//            if (siri.getServiceDelivery() == null || siri.getServiceDelivery().getStopMonitoringDeliveries() == null ||
//                    siri.getServiceDelivery().getStopMonitoringDeliveries().get(0) == null){
//                logger.info("Empty StopMonitoring from GTFS-RT on dataset:" + datasetId);
//                return;
//            }
//
//
//            List<MonitoredStopVisit> stopVisits = siri.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits();

            Collection<MonitoredStopVisit> ingestedVisits = handler.ingestStopVisits(datasetId, stopVisitsToIngest);

            for (MonitoredStopVisit visit : ingestedVisits) {
                subscriptionManager.touchSubscription(GTFSRT_SM_PREFIX + visit.getMonitoringRef().getValue(),false);
            }

          //  logger.info("GTFS-RT - Ingested  stop Times {} on {} . datasetId:{}, URL:{}", ingestedVisits.size(), stopVisitsToIngest.size(), datasetId, url);



        } catch (JAXBException | XMLStreamException jaxbException) {
            logger.error("Error while unmarshalling siri message from gtfsrt SM", e);
        }
    }

    public static MonitoredStopVisit parseXml(InputStream inputStream) throws JAXBException, XMLStreamException {
        if (jaxbUnmarshaller == null){
            jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        }

        if (xmlif == null){
            xmlif = XMLInputFactory.newInstance();
        }


         xmler = xmlif.createXMLStreamReader(inputStream);



        return (MonitoredStopVisit) jaxbUnmarshaller.unmarshal(xmler);
    }

}
