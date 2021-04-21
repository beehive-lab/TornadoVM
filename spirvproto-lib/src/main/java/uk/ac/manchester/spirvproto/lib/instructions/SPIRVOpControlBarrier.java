package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpControlBarrier extends SPIRVInstruction {
    public final SPIRVId _execution;
    public final SPIRVId _memory;
    public final SPIRVId _semantics;

    public SPIRVOpControlBarrier(SPIRVId _execution, SPIRVId _memory, SPIRVId _semantics) {
        super(224, _execution.getWordCount() + _memory.getWordCount() + _semantics.getWordCount() + 1, "OpControlBarrier");
        this._execution = _execution;
        this._memory = _memory;
        this._semantics = _semantics;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _execution.write(output);
        _memory.write(output);
        _semantics.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _execution.print(output, options);
        output.print(" ");
 
        _memory.print(output, options);
        output.print(" ");
 
        _semantics.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _execution.getCapabilities().length + _memory.getCapabilities().length + _semantics.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _execution.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _memory.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _semantics.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpControlBarrier) {
            SPIRVOpControlBarrier otherInst = (SPIRVOpControlBarrier) other;
            if (!this._execution.equals(otherInst._execution)) return false;
            if (!this._memory.equals(otherInst._memory)) return false;
            if (!this._semantics.equals(otherInst._semantics)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
