package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpSubgroupImageBlockWriteINTEL extends SPIRVInstruction {
    public final SPIRVId _image;
    public final SPIRVId _coordinate;
    public final SPIRVId _data;

    public SPIRVOpSubgroupImageBlockWriteINTEL(SPIRVId _image, SPIRVId _coordinate, SPIRVId _data) {
        super(5578, _image.getWordCount() + _coordinate.getWordCount() + _data.getWordCount() + 1, "OpSubgroupImageBlockWriteINTEL", SPIRVCapability.SubgroupImageBlockIOINTEL());
        this._image = _image;
        this._coordinate = _coordinate;
        this._data = _data;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _image.write(output);
        _coordinate.write(output);
        _data.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _image.print(output, options);
        output.print(" ");
 
        _coordinate.print(output, options);
        output.print(" ");
 
        _data.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _image.getCapabilities().length + _coordinate.getCapabilities().length + _data.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _image.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _coordinate.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _data.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpSubgroupImageBlockWriteINTEL) {
            SPIRVOpSubgroupImageBlockWriteINTEL otherInst = (SPIRVOpSubgroupImageBlockWriteINTEL) other;
            if (!this._image.equals(otherInst._image)) return false;
            if (!this._coordinate.equals(otherInst._coordinate)) return false;
            if (!this._data.equals(otherInst._data)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
