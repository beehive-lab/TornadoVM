package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpTypePipe extends SPIRVGlobalInst {
    public final SPIRVId _idResult;
    public final SPIRVAccessQualifier _qualifier;

    public SPIRVOpTypePipe(SPIRVId _idResult, SPIRVAccessQualifier _qualifier) {
        super(38, _idResult.getWordCount() + _qualifier.getWordCount() + 1, "OpTypePipe", SPIRVCapability.Pipes());
        this._idResult = _idResult;
        this._qualifier = _qualifier;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResult.write(output);
        _qualifier.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
          
        _qualifier.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResult.getCapabilities().length + _qualifier.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResult.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _qualifier.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpTypePipe) {
            SPIRVOpTypePipe otherInst = (SPIRVOpTypePipe) other;
            if (!this._qualifier.equals(otherInst._qualifier)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
