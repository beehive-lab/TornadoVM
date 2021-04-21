package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpReturnValue extends SPIRVTerminationInst {
    public final SPIRVId _value;

    public SPIRVOpReturnValue(SPIRVId _value) {
        super(254, _value.getWordCount() + 1, "OpReturnValue");
        this._value = _value;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _value.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _value.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _value.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _value.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpReturnValue) {
            SPIRVOpReturnValue otherInst = (SPIRVOpReturnValue) other;
            if (!this._value.equals(otherInst._value)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
