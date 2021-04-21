package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpAtomicFlagClear extends SPIRVInstruction {
    public final SPIRVId _pointer;
    public final SPIRVId _scope;
    public final SPIRVId _semantics;

    public SPIRVOpAtomicFlagClear(SPIRVId _pointer, SPIRVId _scope, SPIRVId _semantics) {
        super(319, _pointer.getWordCount() + _scope.getWordCount() + _semantics.getWordCount() + 1, "OpAtomicFlagClear", SPIRVCapability.Kernel());
        this._pointer = _pointer;
        this._scope = _scope;
        this._semantics = _semantics;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _pointer.write(output);
        _scope.write(output);
        _semantics.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _pointer.print(output, options);
        output.print(" ");
 
        _scope.print(output, options);
        output.print(" ");
 
        _semantics.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _pointer.getCapabilities().length + _scope.getCapabilities().length + _semantics.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _pointer.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _scope.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _semantics.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpAtomicFlagClear) {
            SPIRVOpAtomicFlagClear otherInst = (SPIRVOpAtomicFlagClear) other;
            if (!this._pointer.equals(otherInst._pointer)) return false;
            if (!this._scope.equals(otherInst._scope)) return false;
            if (!this._semantics.equals(otherInst._semantics)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
