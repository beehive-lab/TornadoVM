package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpTypeSampledImage extends SPIRVGlobalInst {
    public final SPIRVId _idResult;
    public final SPIRVId _imageType;

    public SPIRVOpTypeSampledImage(SPIRVId _idResult, SPIRVId _imageType) {
        super(27, _idResult.getWordCount() + _imageType.getWordCount() + 1, "OpTypeSampledImage");
        this._idResult = _idResult;
        this._imageType = _imageType;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResult.write(output);
        _imageType.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
          
        _imageType.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResult.getCapabilities().length + _imageType.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResult.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _imageType.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpTypeSampledImage) {
            SPIRVOpTypeSampledImage otherInst = (SPIRVOpTypeSampledImage) other;
            if (!this._imageType.equals(otherInst._imageType)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
