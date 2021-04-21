package uk.ac.manchester.spirvproto.lib;

import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;

import java.util.HashMap;
import java.util.Map;

class SPIRVIdGenerator {
    private int currentId;
    private final Map<Integer, SPIRVId> idNameMap;

    public SPIRVIdGenerator() {
        currentId = 1;
        idNameMap = new HashMap<>();
    }

    /**
     * Get the next ID that is guaranteed to be different from the previous IDs
     * @return The new SPIRVId
     */
    public SPIRVId getNextId() {
        return new SPIRVId(currentId++);
    }

    /**
     * Get the current bound.
     * @return The current bound that is guaranteed to be larger than all IDs
     */
    public int getCurrentBound() {
        return currentId;
    }

    /**
     * Retrieve the ID mapped to the given name, if it does not exist it is created
     * @param name The name the ID is mapped to
     * @return The ID mapped to the given name
     */
    public SPIRVId getOrCreateId(String name) {
        SPIRVId id;
        int key = name.hashCode();
        if (idNameMap.containsKey(key)) {
            id = idNameMap.get(key);
        }
        else {
            id = getNextId();
            idNameMap.put(key, id);
        }
        return id;
    }
    /**
     * Retrieve the ID mapped to the given number, if it does not exist it is created
     * @param id The number the ID is mapped to
     * @return The ID mapped to the given number
     */
    public SPIRVId getOrAddId(int id) {
        SPIRVId idObj;
        if (idNameMap.containsKey(id)) {
            idObj = idNameMap.get(id);
        }
        else {
            idObj = new SPIRVId(id);
            idNameMap.put(id, idObj);
        }

        return idObj;
    }
}
