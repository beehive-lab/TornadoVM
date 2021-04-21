package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpVariable extends SPIRVGlobalInst {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVStorageClass _storageClass;
    public final SPIRVOptionalOperand<SPIRVId> _initializer;

    public SPIRVOpVariable(SPIRVId _idResultType, SPIRVId _idResult, SPIRVStorageClass _storageClass, SPIRVOptionalOperand<SPIRVId> _initializer) {
        super(59, _idResultType.getWordCount() + _idResult.getWordCount() + _storageClass.getWordCount() + _initializer.getWordCount() + 1, "OpVariable");
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._storageClass = _storageClass;
        this._initializer = _initializer;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _storageClass.write(output);
        _initializer.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _storageClass.print(output, options);
        output.print(" ");
 
        _initializer.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _storageClass.getCapabilities().length + _initializer.getCapabilities().length];
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
        for (SPIRVCapability capability : _storageClass.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _initializer.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpVariable) {
            SPIRVOpVariable otherInst = (SPIRVOpVariable) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._storageClass.equals(otherInst._storageClass)) return false;
            if (!this._initializer.equals(otherInst._initializer)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
