package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVCapability;

public abstract class SPIRVSourceInst extends SPIRVDebugInst {
    protected SPIRVSourceInst(int opCode, int wordCount, String name, SPIRVCapability... capabilities) {
        super(opCode, wordCount, name, capabilities);
    }
}
