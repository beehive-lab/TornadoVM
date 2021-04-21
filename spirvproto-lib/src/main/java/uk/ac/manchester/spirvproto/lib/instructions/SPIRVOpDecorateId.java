package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpDecorateId extends SPIRVInstruction {
    public final SPIRVId _target;
    public final SPIRVDecoration _decoration;

    public SPIRVOpDecorateId(SPIRVId _target, SPIRVDecoration _decoration) {
        super(332, _target.getWordCount() + _decoration.getWordCount() + 1, "OpDecorateId");
        this._target = _target;
        this._decoration = _decoration;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _target.write(output);
        _decoration.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _target.print(output, options);
        output.print(" ");
 
        _decoration.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _target.getCapabilities().length + _decoration.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _target.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _decoration.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpDecorateId) {
            SPIRVOpDecorateId otherInst = (SPIRVOpDecorateId) other;
            if (!this._target.equals(otherInst._target)) return false;
            if (!this._decoration.equals(otherInst._decoration)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
