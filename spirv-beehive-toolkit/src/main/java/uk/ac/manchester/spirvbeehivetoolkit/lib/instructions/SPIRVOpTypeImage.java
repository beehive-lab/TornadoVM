/*
* MIT License
*
* Copyright (c) 2021, APT Group, Department of Computer Science,
* The University of Manchester.
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in all
* copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
* SOFTWARE.
*/

package uk.ac.manchester.spirvbeehivetoolkit.lib.instructions;

import uk.ac.manchester.spirvbeehivetoolkit.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirvbeehivetoolkit.generator")
public class SPIRVOpTypeImage extends SPIRVGlobalInst {
    public final SPIRVId _idResult;
    public final SPIRVId _sampledType;
    public final SPIRVDim _dim;
    public final SPIRVLiteralInteger _depth;
    public final SPIRVLiteralInteger _arrayed;
    public final SPIRVLiteralInteger _mS;
    public final SPIRVLiteralInteger _sampled;
    public final SPIRVImageFormat _imageFormat;
    public final SPIRVOptionalOperand<SPIRVAccessQualifier> _accessQualifier;

    public SPIRVOpTypeImage(SPIRVId _idResult, SPIRVId _sampledType, SPIRVDim _dim, SPIRVLiteralInteger _depth, SPIRVLiteralInteger _arrayed, SPIRVLiteralInteger _mS, SPIRVLiteralInteger _sampled, SPIRVImageFormat _imageFormat, SPIRVOptionalOperand<SPIRVAccessQualifier> _accessQualifier) {
        super(25, _idResult.getWordCount() + _sampledType.getWordCount() + _dim.getWordCount() + _depth.getWordCount() + _arrayed.getWordCount() + _mS.getWordCount() + _sampled.getWordCount() + _imageFormat.getWordCount() + _accessQualifier.getWordCount() + 1, "OpTypeImage");
        this._idResult = _idResult;
        this._sampledType = _sampledType;
        this._dim = _dim;
        this._depth = _depth;
        this._arrayed = _arrayed;
        this._mS = _mS;
        this._sampled = _sampled;
        this._imageFormat = _imageFormat;
        this._accessQualifier = _accessQualifier;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResult.write(output);
        _sampledType.write(output);
        _dim.write(output);
        _depth.write(output);
        _arrayed.write(output);
        _mS.write(output);
        _sampled.write(output);
        _imageFormat.write(output);
        _accessQualifier.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
          
        _sampledType.print(output, options);
        output.print(" ");
 
        _dim.print(output, options);
        output.print(" ");
 
        _depth.print(output, options);
        output.print(" ");
 
        _arrayed.print(output, options);
        output.print(" ");
 
        _mS.print(output, options);
        output.print(" ");
 
        _sampled.print(output, options);
        output.print(" ");
 
        _imageFormat.print(output, options);
        output.print(" ");
 
        _accessQualifier.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResult.getCapabilities().length + _sampledType.getCapabilities().length + _dim.getCapabilities().length + _depth.getCapabilities().length + _arrayed.getCapabilities().length + _mS.getCapabilities().length + _sampled.getCapabilities().length + _imageFormat.getCapabilities().length + _accessQualifier.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResult.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _sampledType.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _dim.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _depth.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _arrayed.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _mS.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _sampled.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _imageFormat.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _accessQualifier.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpTypeImage) {
            SPIRVOpTypeImage otherInst = (SPIRVOpTypeImage) other;
            if (!this._sampledType.equals(otherInst._sampledType)) return false;
            if (!this._dim.equals(otherInst._dim)) return false;
            if (!this._depth.equals(otherInst._depth)) return false;
            if (!this._arrayed.equals(otherInst._arrayed)) return false;
            if (!this._mS.equals(otherInst._mS)) return false;
            if (!this._sampled.equals(otherInst._sampled)) return false;
            if (!this._imageFormat.equals(otherInst._imageFormat)) return false;
            if (!this._accessQualifier.equals(otherInst._accessQualifier)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
