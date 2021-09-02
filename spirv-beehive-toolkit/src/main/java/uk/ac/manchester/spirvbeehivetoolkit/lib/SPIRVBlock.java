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
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpLabel;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVTerminationInst;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SPIRVBlock implements SPIRVInstScope {
    private final SPIRVOpLabel label;
    private final SPIRVIdGenerator idGen;
    private final SPIRVInstScope enclosingScope;
    private final List<SPIRVInstruction> instructions;
    private SPIRVTerminationInst end;
    private final Map<SPIRVId, SPIRVInstruction> idToInstMap;

    public SPIRVBlock(SPIRVOpLabel instruction, SPIRVInstScope enclosingScope) {
        label = instruction;
        this.enclosingScope = enclosingScope;
        this.idGen = enclosingScope.getIdGen();
        instructions = new ArrayList<>();
        idToInstMap = new HashMap<>(1);
        idToInstMap.put(label.getResultId(), label);
    }

    @Override
    public SPIRVInstScope add(SPIRVInstruction instruction) {
        ensureCapabilitiesPresent(instruction);

        if (instruction instanceof SPIRVTerminationInst) {
            end = (SPIRVTerminationInst) instruction;
            return enclosingScope;
        }

        instructions.add(instruction);

        SPIRVId resultId = instruction.getResultId();
        if (resultId != null) idToInstMap.put(resultId, instruction);

        return this;
    }

    @Override
    public SPIRVId getOrCreateId(String name) {
        return idGen.getOrCreateId(name);
    }

    @Override
    public SPIRVIdGenerator getIdGen() {
        return idGen;
    }

    @Override
    public void forEachInstruction(Consumer<SPIRVInstruction> instructionConsumer) {
        instructionConsumer.accept(label);
        instructions.forEach(instructionConsumer);
        instructionConsumer.accept(end);
    }

    @Override
    public SPIRVInstruction getInstruction(SPIRVId id) {
        if (idToInstMap.containsKey(id)) {
            return idToInstMap.get(id);
        }
        else return enclosingScope.getInstruction(id);
    }

    @Override
    public SPIRVId getOrAddId(int id) {
        return idGen.getOrAddId(id);
    }

    @Override
    public void ensureCapabilitiesPresent(SPIRVInstruction instruction) {
        enclosingScope.ensureCapabilitiesPresent(instruction);
    }

}
