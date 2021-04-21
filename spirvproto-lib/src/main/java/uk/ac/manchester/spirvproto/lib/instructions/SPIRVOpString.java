package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpString extends SPIRVSourceInst {
    public final SPIRVId _idResult;
    public final SPIRVLiteralString _string;

    public SPIRVOpString(SPIRVId _idResult, SPIRVLiteralString _string) {
        super(7, _idResult.getWordCount() + _string.getWordCount() + 1, "OpString");
        this._idResult = _idResult;
        this._string = _string;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResult.write(output);
        _string.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
          
        _string.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResult.getCapabilities().length + _string.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResult.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _string.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpString) {
            SPIRVOpString otherInst = (SPIRVOpString) other;
            if (!this._string.equals(otherInst._string)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
