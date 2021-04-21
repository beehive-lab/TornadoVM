package uk.ac.manchester.spirvproto.lib;

import uk.ac.manchester.spirvproto.lib.instructions.SPIRVInstruction;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpLabel;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVTerminationInst;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;

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
