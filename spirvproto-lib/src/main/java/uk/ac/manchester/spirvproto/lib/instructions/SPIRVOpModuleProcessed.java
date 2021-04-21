package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpModuleProcessed extends SPIRVDebugInst {
    public final SPIRVLiteralString _process;

    public SPIRVOpModuleProcessed(SPIRVLiteralString _process) {
        super(330, _process.getWordCount() + 1, "OpModuleProcessed");
        this._process = _process;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _process.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _process.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _process.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _process.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpModuleProcessed) {
            SPIRVOpModuleProcessed otherInst = (SPIRVOpModuleProcessed) other;
            if (!this._process.equals(otherInst._process)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
