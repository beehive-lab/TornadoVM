package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpLoopMerge extends SPIRVInstruction {
    public final SPIRVId _mergeBlock;
    public final SPIRVId _continueTarget;
    public final SPIRVLoopControl _loopControl;

    public SPIRVOpLoopMerge(SPIRVId _mergeBlock, SPIRVId _continueTarget, SPIRVLoopControl _loopControl) {
        super(246, _mergeBlock.getWordCount() + _continueTarget.getWordCount() + _loopControl.getWordCount() + 1, "OpLoopMerge");
        this._mergeBlock = _mergeBlock;
        this._continueTarget = _continueTarget;
        this._loopControl = _loopControl;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _mergeBlock.write(output);
        _continueTarget.write(output);
        _loopControl.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _mergeBlock.print(output, options);
        output.print(" ");
 
        _continueTarget.print(output, options);
        output.print(" ");
 
        _loopControl.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _mergeBlock.getCapabilities().length + _continueTarget.getCapabilities().length + _loopControl.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _mergeBlock.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _continueTarget.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _loopControl.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpLoopMerge) {
            SPIRVOpLoopMerge otherInst = (SPIRVOpLoopMerge) other;
            if (!this._mergeBlock.equals(otherInst._mergeBlock)) return false;
            if (!this._continueTarget.equals(otherInst._continueTarget)) return false;
            if (!this._loopControl.equals(otherInst._loopControl)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
