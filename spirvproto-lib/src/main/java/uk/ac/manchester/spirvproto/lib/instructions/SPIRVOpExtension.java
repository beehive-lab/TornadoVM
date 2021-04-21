package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpExtension extends SPIRVInstruction {
    public final SPIRVLiteralString _name;

    public SPIRVOpExtension(SPIRVLiteralString _name) {
        super(10, _name.getWordCount() + 1, "OpExtension");
        this._name = _name;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _name.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _name.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _name.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _name.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpExtension) {
            SPIRVOpExtension otherInst = (SPIRVOpExtension) other;
            if (!this._name.equals(otherInst._name)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
