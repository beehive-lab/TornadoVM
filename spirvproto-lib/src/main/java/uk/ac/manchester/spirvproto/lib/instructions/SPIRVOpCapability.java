package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpCapability extends SPIRVInstruction {
    public final SPIRVCapability _capability;

    public SPIRVOpCapability(SPIRVCapability _capability) {
        super(17, _capability.getWordCount() + 1, "OpCapability");
        this._capability = _capability;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _capability.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _capability.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _capability.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _capability.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpCapability) {
            SPIRVOpCapability otherInst = (SPIRVOpCapability) other;
            if (!this._capability.equals(otherInst._capability)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
