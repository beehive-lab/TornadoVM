package uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands;

import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVInstruction;

public abstract class SPIRVGlobalInst extends SPIRVInstruction {
    protected SPIRVGlobalInst(int opCode, int wordCount, String name, SPIRVCapability... capabilities) {
        super(opCode, wordCount, name, capabilities);
    }
}
