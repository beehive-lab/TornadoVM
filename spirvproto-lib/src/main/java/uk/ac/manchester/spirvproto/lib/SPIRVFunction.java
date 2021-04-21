package uk.ac.manchester.spirvproto.lib;

import uk.ac.manchester.spirvproto.lib.instructions.*;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SPIRVFunction implements SPIRVInstScope {
    protected final List<SPIRVBlock> blocks;
    protected final SPIRVInstScope enclosingScope;
    protected final SPIRVIdGenerator idGen;
    protected SPIRVOpFunction functionDeclaration;
    protected List<SPIRVOpFunctionParameter> parameters;
    protected SPIRVOpFunctionEnd end;
    private final Map<SPIRVId, SPIRVInstruction> idToInstMap;

    public SPIRVFunction(SPIRVOpFunction instruction, SPIRVInstScope enclosingScope) {
        functionDeclaration = instruction;
        parameters = new ArrayList<>();
        blocks = new ArrayList<>();
        this.enclosingScope = enclosingScope;
        idGen = enclosingScope.getIdGen();
        idToInstMap = new HashMap<>(1);
        idToInstMap.put(functionDeclaration.getResultId(), functionDeclaration);
    }

    @Override
    public SPIRVInstScope add(SPIRVInstruction instruction) {
        ensureCapabilitiesPresent(instruction);

        if (instruction instanceof SPIRVOpFunctionParameter) {
            parameters.add((SPIRVOpFunctionParameter) instruction);
            idToInstMap.put(instruction.getResultId(), instruction);
            return this;
        } else if (instruction instanceof SPIRVOpFunctionEnd) {
            end = (SPIRVOpFunctionEnd) instruction;
            return enclosingScope;
        }
        else if (instruction instanceof SPIRVOpLabel) {
            SPIRVBlock newBlock = new SPIRVBlock((SPIRVOpLabel) instruction, this);
            blocks.add(newBlock);
            return newBlock;
        }
        throw new IllegalArgumentException(instruction.getClass().getSimpleName() + " is not a valid instruction in a function level scope");
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
        instructionConsumer.accept(functionDeclaration);
        parameters.forEach(instructionConsumer);
        blocks.forEach(b -> b.forEachInstruction(instructionConsumer));
        instructionConsumer.accept(end);
    }

    @Override
    public SPIRVInstruction getInstruction(SPIRVId id) {
        if (idToInstMap.containsKey(id)) {
            return idToInstMap.get(id);
        }
        else {
            return enclosingScope.getInstruction(id);
        }
    }

    @Override
    public SPIRVId getOrAddId(int id) {
        return idGen.getOrAddId(id);
    }

    @Override
    public void ensureCapabilitiesPresent(SPIRVInstruction instruction) {
        enclosingScope.ensureCapabilitiesPresent(instruction);
    }

    public boolean hasBlocks() {
        return blocks.size() > 0;
    }
}
