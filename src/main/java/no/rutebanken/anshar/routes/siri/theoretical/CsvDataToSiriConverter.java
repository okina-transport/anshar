package no.rutebanken.anshar.routes.siri.theoretical;

import org.springframework.stereotype.Component;
import uk.org.siri.siri20.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static java.time.ZonedDateTime.now;

@Component
public class CsvDataToSiriConverter {

    private static final ZoneId PARIS_ZONE_ID = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");

    public MonitoredStopVisit mapToStopVisit(TheoreticalStopMonitoringInfo data) {
        MonitoredStopVisit msv = new MonitoredStopVisit();

        MonitoringRefStructure mrs = new MonitoringRefStructure();
        mrs.setValue(data.getMonitoringRef());
        msv.setMonitoringRef(mrs);
        msv.setMonitoredVehicleJourney(buildMonitoredVehicleJourneyStructure(data));
        msv.setItemIdentifier(
            data.getMonitoringRef() + "_" +
            data.getMonitoredVehicleJourneyRef() + "_" +
            data.getDate().format(DATE_FORMATTER) + "_" +
            data.getAimedArrivalTime().format(HOUR_FORMATTER)
        );
        msv.setRecordedAtTime(now());

        return msv;
    }

    private MonitoredVehicleJourneyStructure buildMonitoredVehicleJourneyStructure(TheoreticalStopMonitoringInfo data) {
        MonitoredVehicleJourneyStructure mvjs = new MonitoredVehicleJourneyStructure();

        mvjs.setLineRef(buildLineRef(data));

        mvjs.setFramedVehicleJourneyRef(buildFramedVehicleJourneyRefStructure(data));

        mvjs.setMonitoredCall(buildMonitoredCallStructure(data));

        mvjs.setDestinationRef(buildDestinationRef(data));
        mvjs.getDestinationNames().add(buildDestinationName(data));

        mvjs.setOriginRef(buildOriginRef(data));
        mvjs.getOriginNames().add(buildOriginName(data));

        mvjs.setMonitored(Boolean.FALSE);

        mvjs.getPublishedLineNames().add(buildPublishLineName(data));

        mvjs.getDirectionNames().add(buildDirectionName(data));

        return mvjs;
    }

    private NaturalLanguageStringStructure buildDirectionName(TheoreticalStopMonitoringInfo data) {
        NaturalLanguageStringStructure direction = new NaturalLanguageStringStructure();
        direction.setValue(data.getDirectionName());
        return direction;
    }

    private NaturalLanguageStringStructure buildPublishLineName(TheoreticalStopMonitoringInfo data) {
        NaturalLanguageStringStructure lineNameStructure = new NaturalLanguageStringStructure();
        lineNameStructure.setValue(data.getPublishedLineName());
        return lineNameStructure;
    }

    private NaturalLanguagePlaceNameStructure buildOriginName(TheoreticalStopMonitoringInfo data) {
        NaturalLanguagePlaceNameStructure originName = new NaturalLanguagePlaceNameStructure();
        originName.setValue(data.getOriginName());
        return originName;
    }

    private JourneyPlaceRefStructure buildOriginRef(TheoreticalStopMonitoringInfo data) {
        JourneyPlaceRefStructure journeyPlaceRefStructure = new JourneyPlaceRefStructure();
        journeyPlaceRefStructure.setValue(data.getOriginRef());
        return journeyPlaceRefStructure;
    }

    private NaturalLanguageStringStructure buildDestinationName(TheoreticalStopMonitoringInfo data) {
        NaturalLanguageStringStructure destinationName = new NaturalLanguageStringStructure();
        destinationName.setValue(data.getDestinationName());
        return destinationName;
    }

    private DestinationRef buildDestinationRef(TheoreticalStopMonitoringInfo data) {
        DestinationRef destinationRef = new DestinationRef();
        destinationRef.setValue(data.getDestinationRef());
        return destinationRef;
    }

    private FramedVehicleJourneyRefStructure buildFramedVehicleJourneyRefStructure(TheoreticalStopMonitoringInfo data) {
        FramedVehicleJourneyRefStructure fvjrs = new FramedVehicleJourneyRefStructure();
        DataFrameRefStructure dfr = new DataFrameRefStructure();
        dfr.setValue("any");
        fvjrs.setDataFrameRef(dfr);
        fvjrs.setDatedVehicleJourneyRef(data.getMonitoredVehicleJourneyRef());
        return fvjrs;
    }

    private LineRef buildLineRef(TheoreticalStopMonitoringInfo data) {
        LineRef lineRef = new LineRef();
        lineRef.setValue(data.getLineRef());
        return lineRef;
    }

    private MonitoredCallStructure buildMonitoredCallStructure(TheoreticalStopMonitoringInfo data) {
        MonitoredCallStructure mcs = new MonitoredCallStructure();
        StopPointRef sp = new StopPointRef();
        sp.setValue(data.getMonitoringRef());
        mcs.setStopPointRef(sp);

        NaturalLanguageStringStructure stopPointName = new NaturalLanguageStringStructure();
        stopPointName.setValue(data.getStopPointName());
        mcs.getStopPointNames().add(stopPointName);

        LocalDate date = data.getDate();
        LocalTime aimedArrivalTime = data.getAimedArrivalTime();
        LocalTime aimedDepartureTime = data.getAimedDepartureTime();

        mcs.setAimedArrivalTime(ZonedDateTime.of(
                date.getYear(),
                date.getMonthValue(),
                date.getDayOfMonth(),
                aimedArrivalTime.getHour(),
                aimedArrivalTime.getMinute(),
                aimedArrivalTime.getSecond(),
                0,
                PARIS_ZONE_ID));
        mcs.setAimedDepartureTime(ZonedDateTime.of(
                date.getYear(),
                date.getMonthValue(),
                date.getDayOfMonth(),
                aimedDepartureTime.getHour(),
                aimedDepartureTime.getMinute(),
                aimedDepartureTime.getSecond(),
                0,
                PARIS_ZONE_ID));

        return mcs;
    }
}
