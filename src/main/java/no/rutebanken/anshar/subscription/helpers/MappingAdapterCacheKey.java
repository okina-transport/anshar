package no.rutebanken.anshar.subscription.helpers;

import com.google.common.base.Objects;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.subscription.SiriDataType;

import java.util.Map;
import java.util.Optional;

public class MappingAdapterCacheKey {
    private final SiriDataType dataType;
    private final OutboundIdMappingPolicy outboundIdMappingPolicy;
    private final Map<ObjectType, Optional<IdProcessingParameters>> idProcessingMap;

    public MappingAdapterCacheKey(SiriDataType dataType, OutboundIdMappingPolicy outboundIdMappingPolicy, Map<ObjectType, Optional<IdProcessingParameters>> idProcessingMap) {
        this.dataType = dataType;
        this.outboundIdMappingPolicy = outboundIdMappingPolicy;
        this.idProcessingMap = idProcessingMap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MappingAdapterCacheKey cacheKey = (MappingAdapterCacheKey) o;
        return dataType == cacheKey.dataType && outboundIdMappingPolicy == cacheKey.outboundIdMappingPolicy && areIdProcessingMapEquals(idProcessingMap, cacheKey.idProcessingMap);
    }

    private boolean areIdProcessingMapEquals(Map<ObjectType, Optional<IdProcessingParameters>> idProcessingMap, Map<ObjectType, Optional<IdProcessingParameters>> idProcessingMap1) {

        if (idProcessingMap == null && idProcessingMap1 == null) {
            return true;
        }

        if ((idProcessingMap == null && idProcessingMap1 != null) ||
                (idProcessingMap != null && idProcessingMap1 == null)) {
            return false;
        }

        for (Map.Entry<ObjectType, Optional<IdProcessingParameters>> idProcEntry : idProcessingMap.entrySet()) {

            ObjectType idProcType = idProcEntry.getKey();
            if (!idProcessingMap1.containsKey(idProcType)) {
                return false;
            }
            Optional<IdProcessingParameters> idProcValue = idProcEntry.getValue();
            Optional<IdProcessingParameters> idProc1Value = idProcessingMap1.get(idProcType);
            if (!areIdProcessingEquals(idProcValue, idProc1Value)) {
                return false;
            }
        }
        return true;
    }

    private boolean areIdProcessingEquals(Optional<IdProcessingParameters> idProcValue, Optional<IdProcessingParameters> idProc1Value) {
        if (idProcValue.isEmpty() && idProc1Value.isEmpty()) {
            return true;
        }

        if ((idProcValue.isPresent() && idProc1Value.isEmpty()) || (idProcValue.isEmpty() && idProc1Value.isPresent())) {
            return false;
        }

        IdProcessingParameters idProc = idProcValue.get();
        IdProcessingParameters idProc1 = idProc1Value.get();
        return idProc.equals(idProc1);

    }

    @Override
    public int hashCode() {
        return Objects.hashCode(dataType, outboundIdMappingPolicy, idProcessingMap);
    }
}
