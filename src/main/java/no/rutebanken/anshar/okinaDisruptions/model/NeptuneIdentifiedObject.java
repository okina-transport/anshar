package no.rutebanken.anshar.okinaDisruptions.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
public class NeptuneIdentifiedObject  implements Serializable {

    protected Long id;
    protected boolean detached = false;
    protected String objectId;
    protected Integer objectVersion = 1;
    protected Date creationTime = new Date();
    protected String creatorId;
    protected boolean saved = false;
    protected boolean isFilled = false;


}
