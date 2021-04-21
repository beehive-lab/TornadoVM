package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpMemoryNamedBarrier extends SPIRVInstruction {
    public final SPIRVId _namedBarrier;
    public final SPIRVId _memory;
    public final SPIRVId _semantics;

    public SPIRVOpMemoryNamedBarrier(SPIRVId _namedBarrier, SPIRVId _memory, SPIRVId _semantics) {
        super(329, _namedBarrier.getWordCount() + _memory.getWordCount() + _semantics.getWordCount() + 1, "OpMemoryNamedBarrier", SPIRVCapability.NamedBarrier());
        this._namedBarrier = _namedBarrier;
        this._memory = _memory;
        this._semantics = _semantics;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _namedBarrier.write(output);
        _memory.write(output);
        _semantics.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _namedBarrier.print(output, options);
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
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _namedBarrier.getCapabilities().length + _memory.getCapabilities().length + _semantics.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _namedBarrier.getCapabilities()) {
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
        if (other instanceof SPIRVOpMemoryNamedBarrier) {
            SPIRVOpMemoryNamedBarrier otherInst = (SPIRVOpMemoryNamedBarrier) other;
            if (!this._namedBarrier.equals(otherInst._namedBarrier)) return false;
            if (!this._memory.equals(otherInst._memory)) return false;
            if (!this._semantics.equals(otherInst._semantics)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
