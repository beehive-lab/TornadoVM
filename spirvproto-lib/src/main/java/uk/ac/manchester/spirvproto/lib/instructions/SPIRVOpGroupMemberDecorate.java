package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpGroupMemberDecorate extends SPIRVAnnotationInst {
    public final SPIRVId _decorationGroup;
    public final SPIRVMultipleOperands<SPIRVPairIdRefLiteralInteger> _targets;

    public SPIRVOpGroupMemberDecorate(SPIRVId _decorationGroup, SPIRVMultipleOperands<SPIRVPairIdRefLiteralInteger> _targets) {
        super(75, _decorationGroup.getWordCount() + _targets.getWordCount() + 1, "OpGroupMemberDecorate");
        this._decorationGroup = _decorationGroup;
        this._targets = _targets;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _decorationGroup.write(output);
        _targets.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _decorationGroup.print(output, options);
        output.print(" ");
 
        _targets.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _decorationGroup.getCapabilities().length + _targets.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _decorationGroup.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _targets.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpGroupMemberDecorate) {
            SPIRVOpGroupMemberDecorate otherInst = (SPIRVOpGroupMemberDecorate) other;
            if (!this._decorationGroup.equals(otherInst._decorationGroup)) return false;
            if (!this._targets.equals(otherInst._targets)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
