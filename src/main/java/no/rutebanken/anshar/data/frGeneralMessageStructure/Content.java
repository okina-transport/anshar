package no.rutebanken.anshar.data.frGeneralMessageStructure;

import lombok.Getter;
import lombok.Setter;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@XmlType(name = "siri:FrGeneralMessageStructure")
@XmlAccessorType(XmlAccessType.FIELD)
public class Content implements Serializable {

    @XmlElement(name = "LineRef", namespace = "http://www.siri.org.uk/siri")
    private List<String> lineRefs = new ArrayList<>();

    @XmlElement(name = "StopPointRef", namespace = "http://www.siri.org.uk/siri")
    private List<String> stopPointRefs = new ArrayList<>();

    @XmlElement(name = "JourneyPatternRef", namespace = "http://www.siri.org.uk/siri")
    private List<String> journeyPatternRefs = new ArrayList<>();

    @XmlElement(name = "RouteRef", namespace = "http://www.siri.org.uk/siri")
    private List<String> routeRefs = new ArrayList<>();

    @XmlElement(name = "GroupOfLinesRef", namespace = "http://www.siri.org.uk/siri")
    private List<String> groupOfLinesRefs = new ArrayList<>();

    @XmlElement(name = "Message", namespace = "http://www.siri.org.uk/siri")
    private Message message;
}
