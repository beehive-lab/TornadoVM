package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpSourceContinued extends SPIRVSourceInst {
    public final SPIRVLiteralString _continuedSource;

    public SPIRVOpSourceContinued(SPIRVLiteralString _continuedSource) {
        super(2, _continuedSource.getWordCount() + 1, "OpSourceContinued");
        this._continuedSource = _continuedSource;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _continuedSource.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _continuedSource.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _continuedSource.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _continuedSource.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpSourceContinued) {
            SPIRVOpSourceContinued otherInst = (SPIRVOpSourceContinued) other;
            if (!this._continuedSource.equals(otherInst._continuedSource)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
