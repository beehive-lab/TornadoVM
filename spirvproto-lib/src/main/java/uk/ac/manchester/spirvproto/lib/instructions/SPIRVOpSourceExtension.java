package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpSourceExtension extends SPIRVSourceInst {
    public final SPIRVLiteralString _extension;

    public SPIRVOpSourceExtension(SPIRVLiteralString _extension) {
        super(4, _extension.getWordCount() + 1, "OpSourceExtension");
        this._extension = _extension;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _extension.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _extension.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _extension.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _extension.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpSourceExtension) {
            SPIRVOpSourceExtension otherInst = (SPIRVOpSourceExtension) other;
            if (!this._extension.equals(otherInst._extension)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
