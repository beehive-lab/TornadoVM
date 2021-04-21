package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpTypeRuntimeArray extends SPIRVGlobalInst {
    public final SPIRVId _idResult;
    public final SPIRVId _elementType;

    public SPIRVOpTypeRuntimeArray(SPIRVId _idResult, SPIRVId _elementType) {
        super(29, _idResult.getWordCount() + _elementType.getWordCount() + 1, "OpTypeRuntimeArray", SPIRVCapability.Shader());
        this._idResult = _idResult;
        this._elementType = _elementType;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResult.write(output);
        _elementType.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
          
        _elementType.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResult.getCapabilities().length + _elementType.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResult.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _elementType.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpTypeRuntimeArray) {
            SPIRVOpTypeRuntimeArray otherInst = (SPIRVOpTypeRuntimeArray) other;
            if (!this._elementType.equals(otherInst._elementType)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
