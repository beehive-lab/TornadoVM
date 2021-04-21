package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpTypePointer extends SPIRVGlobalInst {
    public final SPIRVId _idResult;
    public final SPIRVStorageClass _storageClass;
    public final SPIRVId _type;

    public SPIRVOpTypePointer(SPIRVId _idResult, SPIRVStorageClass _storageClass, SPIRVId _type) {
        super(32, _idResult.getWordCount() + _storageClass.getWordCount() + _type.getWordCount() + 1, "OpTypePointer");
        this._idResult = _idResult;
        this._storageClass = _storageClass;
        this._type = _type;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResult.write(output);
        _storageClass.write(output);
        _type.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
          
        _storageClass.print(output, options);
        output.print(" ");
 
        _type.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResult.getCapabilities().length + _storageClass.getCapabilities().length + _type.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResult.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _storageClass.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _type.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpTypePointer) {
            SPIRVOpTypePointer otherInst = (SPIRVOpTypePointer) other;
            if (!this._storageClass.equals(otherInst._storageClass)) return false;
            if (!this._type.equals(otherInst._type)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
