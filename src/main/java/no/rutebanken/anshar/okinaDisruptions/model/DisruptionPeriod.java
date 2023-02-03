package no.rutebanken.anshar.okinaDisruptions.model;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


import java.io.Serializable;
import java.time.LocalDateTime;




@Getter
@Setter
@NoArgsConstructor
public class DisruptionPeriod implements Serializable {


    private Long id;

    private LocalDateTime startDate;

    private LocalDateTime endDate;








}

