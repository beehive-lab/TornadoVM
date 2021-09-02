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
public class SPIRVOpImageSparseGather extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _sampledImage;
    public final SPIRVId _coordinate;
    public final SPIRVId _component;
    public final SPIRVOptionalOperand<SPIRVImageOperands> _imageOperands;

    public SPIRVOpImageSparseGather(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _sampledImage, SPIRVId _coordinate, SPIRVId _component, SPIRVOptionalOperand<SPIRVImageOperands> _imageOperands) {
        super(314, _idResultType.getWordCount() + _idResult.getWordCount() + _sampledImage.getWordCount() + _coordinate.getWordCount() + _component.getWordCount() + _imageOperands.getWordCount() + 1, "OpImageSparseGather", SPIRVCapability.SparseResidency());
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._sampledImage = _sampledImage;
        this._coordinate = _coordinate;
        this._component = _component;
        this._imageOperands = _imageOperands;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _sampledImage.write(output);
        _coordinate.write(output);
        _component.write(output);
        _imageOperands.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _sampledImage.print(output, options);
        output.print(" ");
 
        _coordinate.print(output, options);
        output.print(" ");
 
        _component.print(output, options);
        output.print(" ");
 
        _imageOperands.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _sampledImage.getCapabilities().length + _coordinate.getCapabilities().length + _component.getCapabilities().length + _imageOperands.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResultType.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResult.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _sampledImage.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _coordinate.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _component.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _imageOperands.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpImageSparseGather) {
            SPIRVOpImageSparseGather otherInst = (SPIRVOpImageSparseGather) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._sampledImage.equals(otherInst._sampledImage)) return false;
            if (!this._coordinate.equals(otherInst._coordinate)) return false;
            if (!this._component.equals(otherInst._component)) return false;
            if (!this._imageOperands.equals(otherInst._imageOperands)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
