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

package no.rutebanken.anshar.routes.siri.lite;

import jakarta.servlet.http.HttpServletResponse;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.outbound.DiscoveryLinesOutbound;
import no.rutebanken.anshar.routes.siri.handlers.outbound.DiscoveryStopPointsOutbound;
import no.rutebanken.anshar.util.SiriUtils;
import org.apache.camel.Exchange;
import org.apache.camel.model.rest.RestParamType;
import org.apache.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;

import javax.ws.rs.core.MediaType;
import java.util.HashSet;
import java.util.Set;

import static no.rutebanken.anshar.routes.HttpParameter.*;
import static no.rutebanken.anshar.routes.validation.validators.Constants.SIRI_LITE_SERVICE_NAME;

@Service
public class SiriLiteRoute extends RestRouteBuilder {

    private static final Set<String> SIRI_LITE_AUTHORIZED_SERVICES = new HashSet<>(Set.of("stoppoints-discovery", "lines-discovery", "stop-monitoring", "general-message", "vehicle-monitoring", "situation-exchange", "estimated-timetables", "facility-monitoring"));

    private static final Set<String> SIRI_LITE_AUTHORIZED_FORMATS = new HashSet<>(Set.of("json", "xml"));


    @Autowired
    private DiscoveryStopPointsOutbound discoveryStopPointsOutbound;

    @Autowired
    private DiscoveryLinesOutbound discoveryLinesOutbound;


    // @formatter:off
    @Override
    public void configure() throws Exception {
        super.configure();


        rest("/siri")
                .tag("siri.lite")
                .get("/{version}/{serviceAndFormat}").to("direct:handle.siri.lite.idf.request")
                .param().required(true).name(PARAM_VERSION).type(RestParamType.path).description("The siri version").dataType("string").endParam()
                .param().required(true).name(PARAM_SERVICE_AND_FORMAT).type(RestParamType.path).description("the requested service and the format").dataType("string").endParam()
                .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response").dataType("string").endParam()
                .param().required(false).name(PARAM_USE_ORIGINAL_ID).type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_USE_ALT_ID).type(RestParamType.query).description("Option to return alternative Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_MAX_SIZE).type(RestParamType.query).description("Specify max number of returned elements").dataType("integer").endParam()
        ;

        rest("/anshar/rest")
                .get("/sx").to("direct:anshar.rest.sx")
                .apiDocs(false)
                .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                .param().required(false).name(PARAM_USE_ORIGINAL_ID).type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_USE_ALT_ID).type(RestParamType.query).description("Option to return alternative Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_MAX_SIZE).type(RestParamType.query).description("Specify max number of returned elements").dataType("integer").endParam()

                .get("/vm").to("direct:anshar.rest.vm")
                .apiDocs(false)
                .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response").dataType("string").endParam()
                .param().required(false).name(PARAM_USE_ORIGINAL_ID).type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_USE_ALT_ID).type(RestParamType.query).description("Option to return alternative Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_MAX_SIZE).type(RestParamType.query).description("Specify max number of returned elements").dataType("integer").endParam()

                .get("/et").to("direct:anshar.rest.et")
                .apiDocs(false)
                .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response").dataType("string").endParam()
                .param().required(false).name(PARAM_USE_ORIGINAL_ID).type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_USE_ALT_ID).type(RestParamType.query).description("Option to return alternative Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_MAX_SIZE).type(RestParamType.query).description("Specify max number of returned elements").dataType("integer").endParam()

                .get("/et-monitored").apiDocs(false).to("direct:anshar.rest.et.monitored")
                .get("/et-monitored-cache").apiDocs(false).to("direct:anshar.rest.et.monitored.cached")
                .get("/sx-cache").apiDocs(false).to("direct:anshar.rest.sx.cached")
                .get("/vm-cache").apiDocs(false).to("direct:anshar.rest.vm.cached")

                .get("/sm").apiDocs(false).to("direct:anshar.rest.sm")
                .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response").dataType("string").endParam()
                .param().required(false).name(PARAM_USE_ORIGINAL_ID).type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_USE_ALT_ID).type(RestParamType.query).description("Option to return alternative Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_MAX_SIZE).type(RestParamType.query).description("Specify max number of returned elements").dataType("integer").endParam()
                .get("/gm").apiDocs(false).to("direct:anshar.rest.gm")
                .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response").dataType("string").endParam()
                .param().required(false).name(PARAM_USE_ORIGINAL_ID).type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_MAX_SIZE).type(RestParamType.query).description("Specify max number of returned elements").dataType("integer").endParam()
                .get("/fm").apiDocs(false).to("direct:anshar.rest.fm")
                .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response").dataType("string").endParam()
                .param().required(false).name(PARAM_USE_ORIGINAL_ID).type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                .param().required(false).name(PARAM_MAX_SIZE).type(RestParamType.query).description("Specify max number of returned elements").dataType("integer").endParam()


        ;

        from("direct:handle.siri.lite.idf.request")
                .process(e -> {
                    String version = getVersion(e);
                    handleServiceAndFormat(e);
                })
                .choice()
                .when(header(SIRI_LITE_SERVICE_NAME).isEqualTo(simple("stop-monitoring")))
                        .to("direct:anshar.rest.sm")
                .when(header(SIRI_LITE_SERVICE_NAME).isEqualTo(simple("vehicle-monitoring")))
                        .to("direct:anshar.rest.vm")
                .when(header(SIRI_LITE_SERVICE_NAME).isEqualTo(simple("situation-exchange")))
                        .to("direct:anshar.rest.sx")
                .when(header(SIRI_LITE_SERVICE_NAME).isEqualTo(simple("general-message")))
                        .to("direct:anshar.rest.gm")
                .when(header(SIRI_LITE_SERVICE_NAME).isEqualTo(simple("estimated-timetables")))
                        .to("direct:anshar.rest.et")
                .when(header(SIRI_LITE_SERVICE_NAME).isEqualTo(simple("stoppoints-discovery")))
                        .to("direct:anshar.sirilite.stoppoints.discovery")
                .when(header(SIRI_LITE_SERVICE_NAME).isEqualTo(simple("lines-discovery")))
                        .to("direct:anshar.sirilite.lines.discovery")
                .when(header(SIRI_LITE_SERVICE_NAME).isEqualTo(simple("facility-monitoring")))
                        .to("direct:anshar.rest.fm")
                .otherwise()
                .process(e -> {
                    String errorMsg = "Service not yet implemented : " + e.getIn().getHeader(SIRI_LITE_SERVICE_NAME);
                    e.getIn().setBody(errorMsg);
                    throw new IllegalArgumentException(errorMsg);
                })
                .endChoice()
        ;

        from("direct:anshar.sirilite.lines.discovery")
                .process(p -> {
                    String datasetId = p.getIn().getHeader(PARAM_DATASET_ID, String.class);
                    String originalId = p.getIn().getHeader(PARAM_USE_ORIGINAL_ID, String.class);
                    String altId = p.getIn().getHeader(PARAM_USE_ALT_ID, String.class);

                    OutboundIdMappingPolicy mappingPolicy;

                    if (Boolean.parseBoolean(altId)){
                        mappingPolicy = OutboundIdMappingPolicy.ALT_ID;
                    }else if (Boolean.parseBoolean(originalId)){
                        mappingPolicy = OutboundIdMappingPolicy.ORIGINAL_ID;
                    }else{
                        mappingPolicy = OutboundIdMappingPolicy.DEFAULT;
                    }

           
                    Set<String> datasets = SiriUtils.generateDatasetListFromHeader(datasetId);

                    Siri response = null;
                    if (datasets.isEmpty()){
                        response = discoveryLinesOutbound.getDiscoveryLines(null,mappingPolicy);
                    }else{
                        for (String dataset : datasets) {
                            Siri datasetResponse = discoveryLinesOutbound.getDiscoveryLines(dataset,mappingPolicy);
                            response = SiriUtils.mergeSiris(response, datasetResponse);
                        }
                    }

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);
                    streamOutput(p, response, out);
                })
        ;


        from("direct:anshar.sirilite.stoppoints.discovery")
                .process(p -> {
                    String datasetId = p.getIn().getHeader(PARAM_DATASET_ID, String.class);
                    String originalId = p.getIn().getHeader(PARAM_USE_ORIGINAL_ID, String.class);
                    String altId = p.getIn().getHeader(PARAM_USE_ALT_ID, String.class);

                    OutboundIdMappingPolicy mappingPolicy;

                    if (Boolean.parseBoolean(altId)){
                        mappingPolicy = OutboundIdMappingPolicy.ALT_ID;
                    }else if (Boolean.parseBoolean(originalId)){
                        mappingPolicy = OutboundIdMappingPolicy.ORIGINAL_ID;
                    }else{
                        mappingPolicy = OutboundIdMappingPolicy.DEFAULT;
                    }

                    Set<String> datasets = SiriUtils.generateDatasetListFromHeader(datasetId);                    
                    Siri response = null;
                    if (datasets.isEmpty()){
                        response = discoveryStopPointsOutbound.getDiscoveryStopPoints(null, mappingPolicy);
                    }else{
                        for (String dataset : datasets) {
                            Siri datasetResponse = discoveryStopPointsOutbound.getDiscoveryStopPoints(dataset, mappingPolicy);
                            response = SiriUtils.mergeSiris(response, datasetResponse);
                        }
                    }

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);
                    streamOutput(p, response, out);
                })
        ;
    }// @formatter:on


    private void handleServiceAndFormat(Exchange e) {
        String serviceAndFormat = e.getIn().getHeader(PARAM_SERVICE_AND_FORMAT, String.class);
        if (!serviceAndFormat.contains(".")) {
            String errorMsg = "Unsupported service and format :" + serviceAndFormat + ". (should be stop-monitoring.json or vehicle-monitoring.json for example)";
            e.getIn().setBody(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        String[] serviceAndFormatTab = serviceAndFormat.split("\\.");
        String service = serviceAndFormatTab[0];

        if (!SIRI_LITE_AUTHORIZED_SERVICES.contains(service)) {
            String errorMsg = "Unsupported service:" + service + ". (should be stoppoints-discovery, lines-discovery, stop-monitoring, general-message, vehicle-monitoring, estimated-timetables, situation-exchange)";
            e.getIn().setBody(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        e.getIn().setHeader(SIRI_LITE_SERVICE_NAME, service);

        String format = serviceAndFormatTab[1];
        if (!SIRI_LITE_AUTHORIZED_FORMATS.contains(format)) {
            String errorMsg = "Unsupported format:" + format + ". (should be json or xml)";
            e.getIn().setBody(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        String mediaType = "json".equals(format) ? MediaType.APPLICATION_JSON : MediaType.TEXT_XML;
        e.getMessage().setHeader(HttpHeaders.CONTENT_TYPE, mediaType);

    }

    private String getVersion(Exchange e) {
        String version = e.getIn().getHeader(PARAM_VERSION, String.class);
        if (!"2.0".equals(version) && !"2.1".equals(version)) {
            String errorMsg = "Unsupported version:" + version;
            e.getIn().setBody(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        if ("2.1".equals(version)) {
            e.getIn().setHeader(SIRI_VERSION_HEADER_NAME, "2.1");
        }
        return version;
    }


}
