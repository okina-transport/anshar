package no.rutebanken.anshar.ishtar.converter;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.ishtar.model.IdProcessingParameterDto;
import org.springframework.core.convert.converter.Converter;

/**
 * Convert ISHTAR IPPDto into ANSHAR IPP.
 */
@Slf4j
public class IdProcessingParameterDtoConverter implements Converter<IdProcessingParameterDto, IdProcessingParameters> {

    @Override
    public IdProcessingParameters convert(IdProcessingParameterDto source) {
        log.debug("source: {}", source);
        IdProcessingParameters target = new IdProcessingParameters();
        target.setDatasetId(source.getDatasetId());
        target.setInputPrefixToRemove(source.getInputPrefixToRemove());
        target.setInputSuffixToRemove(source.getInputSuffixToRemove());
        target.setOutputPrefixToAdd(source.getOutputPrefixToAdd());
        target.setOutputSuffixToAdd(source.getOutputSuffixToAdd());
        target.setObjectType(ObjectType.valueOf(source.getObjectType()));
        log.debug("target: {}", target);
        return target;
    }

}
