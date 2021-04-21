package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpTypeFloat extends SPIRVGlobalInst {
    public final SPIRVId _idResult;
    public final SPIRVLiteralInteger _width;

    public SPIRVOpTypeFloat(SPIRVId _idResult, SPIRVLiteralInteger _width) {
        super(22, _idResult.getWordCount() + _width.getWordCount() + 1, "OpTypeFloat");
        this._idResult = _idResult;
        this._width = _width;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResult.write(output);
        _width.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
          
        _width.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResult.getCapabilities().length + _width.getCapabilities().length];
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

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpTypeFloat) {
            SPIRVOpTypeFloat otherInst = (SPIRVOpTypeFloat) other;
            if (!this._width.equals(otherInst._width)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
