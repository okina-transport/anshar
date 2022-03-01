package no.rutebanken.anshar.okinaDisruptions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import no.rutebanken.anshar.okinaDisruptions.model.enums.OkinaNetworkType;
import no.rutebanken.anshar.okinaDisruptions.model.enums.PTNetworkSourceTypeEnum;

import javax.ws.rs.DefaultValue;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OkinaNetwork  extends NeptuneIdentifiedObject{

    private String name;
    private String comment;
    private Date versionDate;
    private String description;
    private String registrationNumber;
    private PTNetworkSourceTypeEnum sourceType;
    private String sourceIdentifier;
    private boolean filled = false;
    private String sourceName;


    @Getter
    @Setter
    @DefaultValue("false")
    private Boolean supprime = false;

    @Getter
    @Setter
    private String atlasRef;

    @Getter
    @Setter
    private Timestamp lastModified;

    @Getter
    @Setter
    private OkinaNetworkType networkType;

    /**
     * lines
     *
     * @param lines
     * New value
     * @return The actual value
     */
    @Getter
    @Setter
    private List<OkinaLine> lines = new ArrayList<>(0);


}
