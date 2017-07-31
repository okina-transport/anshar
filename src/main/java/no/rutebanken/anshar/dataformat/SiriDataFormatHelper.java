package no.rutebanken.anshar.dataformat;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.spi.DataFormat;

import java.util.HashMap;
import java.util.Map;

public class SiriDataFormatHelper {

    public static DataFormat getSiriJaxbDataformat() {
    	return createDataformat("");
    }

    public static DataFormat getSiriJaxbDataformat(NamespacePrefixMapper namespacePrefixMapper) {

        if (namespacePrefixMapper != null) {
            String preferredPrefix = namespacePrefixMapper.getPreferredPrefix("", "", true);
            if (preferredPrefix != null) {
                return createDataformat(preferredPrefix);
            }
        }

    	return getSiriJaxbDataformat();
    }

    private static DataFormat createDataformat(String prefix) {
        Map<String, String> prefixMap = new HashMap<>();
        prefixMap.put("http://www.siri.org.uk/siri", prefix);
        JaxbDataFormat siriJaxb = new JaxbDataFormat("uk.org.siri.siri20");
        siriJaxb.setNamespacePrefix(prefixMap );
        return  siriJaxb;
    }


}

