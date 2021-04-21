package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpTypeForwardPointer extends SPIRVGlobalInst {
    public final SPIRVId _pointerType;
    public final SPIRVStorageClass _storageClass;

    public SPIRVOpTypeForwardPointer(SPIRVId _pointerType, SPIRVStorageClass _storageClass) {
        super(39, _pointerType.getWordCount() + _storageClass.getWordCount() + 1, "OpTypeForwardPointer", SPIRVCapability.Addresses());
        this._pointerType = _pointerType;
        this._storageClass = _storageClass;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _pointerType.write(output);
        _storageClass.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _pointerType.print(output, options);
        output.print(" ");
 
        _storageClass.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _pointerType.getCapabilities().length + _storageClass.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _pointerType.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _storageClass.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpTypeForwardPointer) {
            SPIRVOpTypeForwardPointer otherInst = (SPIRVOpTypeForwardPointer) other;
            if (!this._pointerType.equals(otherInst._pointerType)) return false;
            if (!this._storageClass.equals(otherInst._storageClass)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
