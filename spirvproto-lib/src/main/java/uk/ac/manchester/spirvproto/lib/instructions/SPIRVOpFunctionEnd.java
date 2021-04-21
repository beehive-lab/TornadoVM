package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpFunctionEnd extends SPIRVInstruction {

    public SPIRVOpFunctionEnd() {
        super(56, 1, "OpFunctionEnd");
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpFunctionEnd) {
            return true;
        }
        else return super.equals(other);
    }
}
