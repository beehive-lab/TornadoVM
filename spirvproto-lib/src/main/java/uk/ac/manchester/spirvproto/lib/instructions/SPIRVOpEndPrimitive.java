package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpEndPrimitive extends SPIRVInstruction {

    public SPIRVOpEndPrimitive() {
        super(219, 1, "OpEndPrimitive", SPIRVCapability.Geometry());
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
        if (other instanceof SPIRVOpEndPrimitive) {
            return true;
        }
        else return super.equals(other);
    }
}
