package no.rutebanken.anshar.data;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Cache that saves all associations : imported-id/netex-id
 */
@Component
public class StopPlaceIdCache {

    // key : imported-id, value : netex-id
    private Map<String, String> importedIdToNetexIdMap = new HashMap<>();

    //key : netex-id, value : imported-id
    private Map<String, String> netexIdToimportedIdMap = new HashMap<>();

    public void addNewAssociationToCache(String importedId, String netexId){
        importedIdToNetexIdMap.put(importedId, netexId);
        netexIdToimportedIdMap.put(netexId, importedId);
    }

    public String getNetexIdFromImportedId(String importedId){
        return importedIdToNetexIdMap.get(importedId);
    }

    public String getImportedIdFromNetexId(String netexId){
        return netexIdToimportedIdMap.get(netexId);
    }

    public boolean isKnownImportedId(String importedId){
        return importedIdToNetexIdMap.containsKey(importedId);
    }

}
