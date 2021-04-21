package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpTypeArray extends SPIRVGlobalInst {
    public final SPIRVId _idResult;
    public final SPIRVId _elementType;
    public final SPIRVId _length;

    public SPIRVOpTypeArray(SPIRVId _idResult, SPIRVId _elementType, SPIRVId _length) {
        super(28, _idResult.getWordCount() + _elementType.getWordCount() + _length.getWordCount() + 1, "OpTypeArray");
        this._idResult = _idResult;
        this._elementType = _elementType;
        this._length = _length;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResult.write(output);
        _elementType.write(output);
        _length.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
          
        _elementType.print(output, options);
        output.print(" ");
 
        _length.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResult.getCapabilities().length + _elementType.getCapabilities().length + _length.getCapabilities().length];
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
        for (SPIRVCapability capability : _length.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpTypeArray) {
            SPIRVOpTypeArray otherInst = (SPIRVOpTypeArray) other;
            if (!this._elementType.equals(otherInst._elementType)) return false;
            if (!this._length.equals(otherInst._length)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
