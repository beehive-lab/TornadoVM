package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpTypeInt extends SPIRVGlobalInst {
    public final SPIRVId _idResult;
    public final SPIRVLiteralInteger _width;
    public final SPIRVLiteralInteger _signedness;

    public SPIRVOpTypeInt(SPIRVId _idResult, SPIRVLiteralInteger _width, SPIRVLiteralInteger _signedness) {
        super(21, _idResult.getWordCount() + _width.getWordCount() + _signedness.getWordCount() + 1, "OpTypeInt");
        this._idResult = _idResult;
        this._width = _width;
        this._signedness = _signedness;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResult.write(output);
        _width.write(output);
        _signedness.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
          
        _width.print(output, options);
        output.print(" ");
 
        _signedness.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResult.getCapabilities().length + _width.getCapabilities().length + _signedness.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResult.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _width.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _signedness.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpTypeInt) {
            SPIRVOpTypeInt otherInst = (SPIRVOpTypeInt) other;
            if (!this._width.equals(otherInst._width)) return false;
            if (!this._signedness.equals(otherInst._signedness)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
