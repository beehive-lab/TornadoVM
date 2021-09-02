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

import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVInstruction;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVId;

import java.util.function.Consumer;

/**
 * A scope represents a block of code that can hold instructions: a module, a function or a block
 * There is a hierarchy between scopes where:
 *   1. There is always a single module
 *   2. A module holds one or more functions
 *   3. A function holds zero or more blocks
 *
 *   The SPIR-V specification describes a strict rule for, which instructions can go into, which scope.
 *   For more details check the docs or visit:
 *   https://www.khronos.org/registry/spir-v/specs/unified1/SPIRV.html#_a_id_logicallayout_a_logical_layout_of_a_module
 */
public interface SPIRVInstScope {
    /**
     * Add the instruction to the scope.
     * @param instruction - a SPIRVInstruction
     * @return The resulting scope, where the next instruction should go. E.g. OpFunction creates a new function scope and returns that.
     */
    SPIRVInstScope add(SPIRVInstruction instruction);

    /**
     * Retrieves an id that is mapped to the given name.
     * @param name - The string to, which the id is mapped.
     * @return The id mapped to this name.
     */
    SPIRVId getOrCreateId(String name);
    SPIRVIdGenerator getIdGen();

    /**
     * Iterates through all the instructions in the scope in order and applies the given function on the instructions.
     * @param instructionConsumer - Function that takes a SPIRVInstruction as it's parameter.
     */
    void forEachInstruction(Consumer<SPIRVInstruction> instructionConsumer);

    /**
     * Gets an instruction that has the given id as it's result id.
     * @param id - the result id of the function.
     * @return The SPIRVInstruction whose result id is the one that has been given.
     */
    SPIRVInstruction getInstruction(SPIRVId id);

    /**
     * Retrieves an id based on it's value. If it does not exist yet it creates one.
     * @param id - The value of the id to be retrieved.
     * @return the SPIRVId object with the given value.
     */
    SPIRVId getOrAddId(int id);

    /**
     * Ensure that the corresponding capabilities are present
     * @param instruction The instruction that has the capabilities
     */
    void ensureCapabilitiesPresent(SPIRVInstruction instruction);
}
