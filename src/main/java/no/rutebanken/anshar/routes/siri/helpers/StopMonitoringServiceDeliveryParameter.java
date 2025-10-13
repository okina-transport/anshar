package no.rutebanken.anshar.routes.siri.helpers;

import no.rutebanken.anshar.config.IncomingSiriParameters;
import uk.org.siri.siri21.ServiceRequest;

public record StopMonitoringServiceDeliveryParameter(ServiceRequest serviceRequest,
                                                     IncomingSiriParameters incomingSiriParameters) {

}
