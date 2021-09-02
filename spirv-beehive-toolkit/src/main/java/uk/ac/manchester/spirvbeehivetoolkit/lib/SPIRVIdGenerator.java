/*
 * MIT License
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package uk.ac.manchester.spirvbeehivetoolkit.lib;

import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVId;

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
