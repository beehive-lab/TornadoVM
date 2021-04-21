package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpAtomicIIncrement extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _pointer;
    public final SPIRVId _scope;
    public final SPIRVId _semantics;

    public SPIRVOpAtomicIIncrement(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _pointer, SPIRVId _scope, SPIRVId _semantics) {
        super(232, _idResultType.getWordCount() + _idResult.getWordCount() + _pointer.getWordCount() + _scope.getWordCount() + _semantics.getWordCount() + 1, "OpAtomicIIncrement");
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._pointer = _pointer;
        this._scope = _scope;
        this._semantics = _semantics;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _pointer.write(output);
        _scope.write(output);
        _semantics.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _pointer.print(output, options);
        output.print(" ");
 
        _scope.print(output, options);
        output.print(" ");
 
        _semantics.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _pointer.getCapabilities().length + _scope.getCapabilities().length + _semantics.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResultType.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResult.getCapabilities()) {
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
        if (other instanceof SPIRVOpAtomicIIncrement) {
            SPIRVOpAtomicIIncrement otherInst = (SPIRVOpAtomicIIncrement) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._pointer.equals(otherInst._pointer)) return false;
            if (!this._scope.equals(otherInst._scope)) return false;
            if (!this._semantics.equals(otherInst._semantics)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
