package no.rutebanken.anshar.okinaDisruptions.model.enums;

import java.util.EnumSet;

public enum TransportSubModeNameEnum {
    AirportLinkBus,
    ExpressBus,
    LocalBus,
    NightBus,
    RailReplacementBus,
    RegionalBus,
    SchoolBus,
    ShuttleBus,
    SightseeingBus,
    LocalTram,
    CityTram,
    International,
    InterregionalRail,
    Local,
    LongDistance,
    NightRail,
    RegionalRail,
    TouristRailway,
    AirportLinkRail,
    Metro,
    DomesticFlight,
    HelicopterService,
    InternationalFlight,
    HighSpeedPassengerService,
    HighSpeedVehicleService,
    InternationalCarFerry,
    InternationalPassengerFerry,
    LocalCarFerry,
    LocalPassengerFerry,
    NationalCarFerry,
    SightseeingService,
    Telecabin,
    Funicular,
    InternationalCoach,
    NationalCoach,
    TouristCoach;

    public static final EnumSet<TransportSubModeNameEnum> CABELWAY_SUB_MODES = EnumSet.of(Telecabin);
    public static final EnumSet<TransportSubModeNameEnum> FUNICULAR_SUB_MODES = EnumSet.of(Funicular);
    public static final EnumSet<TransportSubModeNameEnum> AIR_SUB_MODES = EnumSet.of(DomesticFlight, HelicopterService, InternationalFlight);
    public static final EnumSet<TransportSubModeNameEnum> RAIL_SUB_MODES = EnumSet.of(International, InterregionalRail, Local, LongDistance, NightRail, RegionalRail, TouristRailway, AirportLinkRail);
    public static final EnumSet<TransportSubModeNameEnum> FERRY_SUB_MODES = EnumSet.of(InternationalCarFerry, InternationalPassengerFerry, LocalCarFerry, LocalPassengerFerry, NationalCarFerry);
    public static final EnumSet<TransportSubModeNameEnum> WATER_SUB_MODES = EnumSet.of(HighSpeedPassengerService, HighSpeedVehicleService, InternationalCarFerry, InternationalPassengerFerry, LocalCarFerry, LocalPassengerFerry, NationalCarFerry, SightseeingService);
    public static final EnumSet<TransportSubModeNameEnum> BUS_SUB_MODES = EnumSet.of(AirportLinkBus, ExpressBus, LocalBus, NightBus, RailReplacementBus, RegionalBus, SchoolBus, ShuttleBus, SightseeingBus);
    public static final EnumSet<TransportSubModeNameEnum> COACH_SUB_MODES = EnumSet.of(InternationalCoach, NationalCoach, TouristCoach);
    public static final EnumSet<TransportSubModeNameEnum> TRAM_SUB_MODES = EnumSet.of(LocalTram, CityTram);

    private TransportSubModeNameEnum() {
    }
}