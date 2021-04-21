package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpExecutionModeId extends SPIRVExecutionModeInst {
    public final SPIRVId _entryPoint;
    public final SPIRVExecutionMode _mode;

    public SPIRVOpExecutionModeId(SPIRVId _entryPoint, SPIRVExecutionMode _mode) {
        super(331, _entryPoint.getWordCount() + _mode.getWordCount() + 1, "OpExecutionModeId");
        this._entryPoint = _entryPoint;
        this._mode = _mode;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _entryPoint.write(output);
        _mode.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _entryPoint.print(output, options);
        output.print(" ");
 
        _mode.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _entryPoint.getCapabilities().length + _mode.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _entryPoint.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _mode.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpExecutionModeId) {
            SPIRVOpExecutionModeId otherInst = (SPIRVOpExecutionModeId) other;
            if (!this._entryPoint.equals(otherInst._entryPoint)) return false;
            if (!this._mode.equals(otherInst._mode)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
