package no.rutebanken.anshar.data.frGeneralMessageStructure;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
@XmlType(name = "siri:FrGeneralMessageStructure")

public class Content implements Serializable {


    private List<String> lineRefs = new ArrayList<String>();


    private List<String> stopPointRefs = new ArrayList<String>();


    private List<String> journeyPatternRefs = new ArrayList<String>();


    private List<String> routeRefs = new ArrayList<String>();

    private List<String> groupOfLinesRefs = new ArrayList<String>();



    @XmlElement(name="Message", namespace="http://www.siri.org.uk/siri")
    private Message message;


    @XmlElement(name="LineRef", namespace="http://www.siri.org.uk/siri")
    public List<String> getLineRefs() {
        return lineRefs;
    }

    @XmlElement(name="StopPointRef" , namespace="http://www.siri.org.uk/siri")
    public List<String> getStopPointRefs() {
        return stopPointRefs;
    }

    @XmlElement(name="JourneyPatternRef" , namespace="http://www.siri.org.uk/siri")
    public List<String> getJourneyPatternRefs() {
        return journeyPatternRefs;
    }

    @XmlElement(name="RouteRef", namespace="http://www.siri.org.uk/siri")
    public List<String> getRouteRefs() {
        return routeRefs;
    }

    @XmlElement(name="GroupOfLinesRef", namespace="http://www.siri.org.uk/siri")
    public List<String> getGroupOfLinesRefs() {
        return groupOfLinesRefs;
    }

    public void setLineRefs(List<String> lineRefs) {
        this.lineRefs = lineRefs;
    }

    public void setStopPointRefs(List<String> stopPointRefs) {
        this.stopPointRefs = stopPointRefs;
    }

    public void setJourneyPatternRefs(List<String> journeyPatternRefs) {
        this.journeyPatternRefs = journeyPatternRefs;
    }

    public void setRouteRefs(List<String> routeRefs) {
        this.routeRefs = routeRefs;
    }

    public void setGroupOfLinesRefs(List<String> groupOfLinesRefs) {
        this.groupOfLinesRefs = groupOfLinesRefs;
    }





    public void setMessage(Message message) {
        this.message = message;
    }
}
