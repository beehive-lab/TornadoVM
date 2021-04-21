package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpSelectionMerge extends SPIRVInstruction {
    public final SPIRVId _mergeBlock;
    public final SPIRVSelectionControl _selectionControl;

    public SPIRVOpSelectionMerge(SPIRVId _mergeBlock, SPIRVSelectionControl _selectionControl) {
        super(247, _mergeBlock.getWordCount() + _selectionControl.getWordCount() + 1, "OpSelectionMerge");
        this._mergeBlock = _mergeBlock;
        this._selectionControl = _selectionControl;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _mergeBlock.write(output);
        _selectionControl.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _mergeBlock.print(output, options);
        output.print(" ");
 
        _selectionControl.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _mergeBlock.getCapabilities().length + _selectionControl.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _mergeBlock.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _selectionControl.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpSelectionMerge) {
            SPIRVOpSelectionMerge otherInst = (SPIRVOpSelectionMerge) other;
            if (!this._mergeBlock.equals(otherInst._mergeBlock)) return false;
            if (!this._selectionControl.equals(otherInst._selectionControl)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
