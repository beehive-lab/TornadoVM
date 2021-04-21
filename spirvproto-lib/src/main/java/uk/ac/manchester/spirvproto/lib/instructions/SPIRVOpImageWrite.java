package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpImageWrite extends SPIRVInstruction {
    public final SPIRVId _image;
    public final SPIRVId _coordinate;
    public final SPIRVId _texel;
    public final SPIRVOptionalOperand<SPIRVImageOperands> _imageOperands;

    public SPIRVOpImageWrite(SPIRVId _image, SPIRVId _coordinate, SPIRVId _texel, SPIRVOptionalOperand<SPIRVImageOperands> _imageOperands) {
        super(99, _image.getWordCount() + _coordinate.getWordCount() + _texel.getWordCount() + _imageOperands.getWordCount() + 1, "OpImageWrite");
        this._image = _image;
        this._coordinate = _coordinate;
        this._texel = _texel;
        this._imageOperands = _imageOperands;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _image.write(output);
        _coordinate.write(output);
        _texel.write(output);
        _imageOperands.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _image.print(output, options);
        output.print(" ");
 
        _coordinate.print(output, options);
        output.print(" ");
 
        _texel.print(output, options);
        output.print(" ");
 
        _imageOperands.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _image.getCapabilities().length + _coordinate.getCapabilities().length + _texel.getCapabilities().length + _imageOperands.getCapabilities().length];
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
        for (SPIRVCapability capability : _texel.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _imageOperands.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpImageWrite) {
            SPIRVOpImageWrite otherInst = (SPIRVOpImageWrite) other;
            if (!this._image.equals(otherInst._image)) return false;
            if (!this._coordinate.equals(otherInst._coordinate)) return false;
            if (!this._texel.equals(otherInst._texel)) return false;
            if (!this._imageOperands.equals(otherInst._imageOperands)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
