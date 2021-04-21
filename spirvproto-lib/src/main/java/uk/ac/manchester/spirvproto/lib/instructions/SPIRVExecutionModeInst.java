package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVCapability;

public abstract class SPIRVExecutionModeInst extends SPIRVInstruction{
    protected SPIRVExecutionModeInst(int opCode, int wordCount, String name, SPIRVCapability... capabilities) {
        super(opCode, wordCount, name, capabilities);
    }
}
