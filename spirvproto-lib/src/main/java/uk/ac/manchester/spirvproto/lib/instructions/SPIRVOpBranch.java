package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpBranch extends SPIRVTerminationInst {
    public final SPIRVId _targetLabel;

    public SPIRVOpBranch(SPIRVId _targetLabel) {
        super(249, _targetLabel.getWordCount() + 1, "OpBranch");
        this._targetLabel = _targetLabel;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _targetLabel.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _targetLabel.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _targetLabel.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _targetLabel.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpBranch) {
            SPIRVOpBranch otherInst = (SPIRVOpBranch) other;
            if (!this._targetLabel.equals(otherInst._targetLabel)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
