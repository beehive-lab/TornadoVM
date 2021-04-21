package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpTypeMatrix extends SPIRVGlobalInst {
    public final SPIRVId _idResult;
    public final SPIRVId _columnType;
    public final SPIRVLiteralInteger _columnCount;

    public SPIRVOpTypeMatrix(SPIRVId _idResult, SPIRVId _columnType, SPIRVLiteralInteger _columnCount) {
        super(24, _idResult.getWordCount() + _columnType.getWordCount() + _columnCount.getWordCount() + 1, "OpTypeMatrix", SPIRVCapability.Matrix());
        this._idResult = _idResult;
        this._columnType = _columnType;
        this._columnCount = _columnCount;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResult.write(output);
        _columnType.write(output);
        _columnCount.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
          
        _columnType.print(output, options);
        output.print(" ");
 
        _columnCount.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResult.getCapabilities().length + _columnType.getCapabilities().length + _columnCount.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResult.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _columnType.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _columnCount.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpTypeMatrix) {
            SPIRVOpTypeMatrix otherInst = (SPIRVOpTypeMatrix) other;
            if (!this._columnType.equals(otherInst._columnType)) return false;
            if (!this._columnCount.equals(otherInst._columnCount)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
