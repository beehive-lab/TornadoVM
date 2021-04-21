package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpSubgroupBlockWriteINTEL extends SPIRVInstruction {
    public final SPIRVId _ptr;
    public final SPIRVId _data;

    public SPIRVOpSubgroupBlockWriteINTEL(SPIRVId _ptr, SPIRVId _data) {
        super(5576, _ptr.getWordCount() + _data.getWordCount() + 1, "OpSubgroupBlockWriteINTEL", SPIRVCapability.SubgroupBufferBlockIOINTEL());
        this._ptr = _ptr;
        this._data = _data;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _ptr.write(output);
        _data.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _ptr.print(output, options);
        output.print(" ");
 
        _data.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _ptr.getCapabilities().length + _data.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _ptr.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _data.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpSubgroupBlockWriteINTEL) {
            SPIRVOpSubgroupBlockWriteINTEL otherInst = (SPIRVOpSubgroupBlockWriteINTEL) other;
            if (!this._ptr.equals(otherInst._ptr)) return false;
            if (!this._data.equals(otherInst._data)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
