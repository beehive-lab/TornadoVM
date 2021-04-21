package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpMemoryModel extends SPIRVInstruction {
    public final SPIRVAddressingModel _addressingModel;
    public final SPIRVMemoryModel _memoryModel;

    public SPIRVOpMemoryModel(SPIRVAddressingModel _addressingModel, SPIRVMemoryModel _memoryModel) {
        super(14, _addressingModel.getWordCount() + _memoryModel.getWordCount() + 1, "OpMemoryModel");
        this._addressingModel = _addressingModel;
        this._memoryModel = _memoryModel;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _addressingModel.write(output);
        _memoryModel.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _addressingModel.print(output, options);
        output.print(" ");
 
        _memoryModel.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _addressingModel.getCapabilities().length + _memoryModel.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _addressingModel.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _memoryModel.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpMemoryModel) {
            SPIRVOpMemoryModel otherInst = (SPIRVOpMemoryModel) other;
            if (!this._addressingModel.equals(otherInst._addressingModel)) return false;
            if (!this._memoryModel.equals(otherInst._memoryModel)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
