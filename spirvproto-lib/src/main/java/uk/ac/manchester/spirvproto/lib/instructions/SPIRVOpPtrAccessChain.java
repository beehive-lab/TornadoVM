package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpPtrAccessChain extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _base;
    public final SPIRVId _element;
    public final SPIRVMultipleOperands<SPIRVId> _indexes;

    public SPIRVOpPtrAccessChain(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _base, SPIRVId _element, SPIRVMultipleOperands<SPIRVId> _indexes) {
        super(67, _idResultType.getWordCount() + _idResult.getWordCount() + _base.getWordCount() + _element.getWordCount() + _indexes.getWordCount() + 1, "OpPtrAccessChain", SPIRVCapability.Addresses(), SPIRVCapability.VariablePointers(), SPIRVCapability.VariablePointersStorageBuffer());
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._base = _base;
        this._element = _element;
        this._indexes = _indexes;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _base.write(output);
        _element.write(output);
        _indexes.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _base.print(output, options);
        output.print(" ");
 
        _element.print(output, options);
        output.print(" ");
 
        _indexes.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _base.getCapabilities().length + _element.getCapabilities().length + _indexes.getCapabilities().length];
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
        for (SPIRVCapability capability : _base.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _element.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _indexes.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpPtrAccessChain) {
            SPIRVOpPtrAccessChain otherInst = (SPIRVOpPtrAccessChain) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._base.equals(otherInst._base)) return false;
            if (!this._element.equals(otherInst._element)) return false;
            if (!this._indexes.equals(otherInst._indexes)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
