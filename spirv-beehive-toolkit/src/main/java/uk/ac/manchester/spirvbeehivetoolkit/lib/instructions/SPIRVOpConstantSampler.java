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
public class SPIRVOpConstantSampler extends SPIRVGlobalInst {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVSamplerAddressingMode _samplerAddressingMode;
    public final SPIRVLiteralInteger _param;
    public final SPIRVSamplerFilterMode _samplerFilterMode;

    public SPIRVOpConstantSampler(SPIRVId _idResultType, SPIRVId _idResult, SPIRVSamplerAddressingMode _samplerAddressingMode, SPIRVLiteralInteger _param, SPIRVSamplerFilterMode _samplerFilterMode) {
        super(45, _idResultType.getWordCount() + _idResult.getWordCount() + _samplerAddressingMode.getWordCount() + _param.getWordCount() + _samplerFilterMode.getWordCount() + 1, "OpConstantSampler", SPIRVCapability.LiteralSampler());
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._samplerAddressingMode = _samplerAddressingMode;
        this._param = _param;
        this._samplerFilterMode = _samplerFilterMode;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _samplerAddressingMode.write(output);
        _param.write(output);
        _samplerFilterMode.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _samplerAddressingMode.print(output, options);
        output.print(" ");
 
        _param.print(output, options);
        output.print(" ");
 
        _samplerFilterMode.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _samplerAddressingMode.getCapabilities().length + _param.getCapabilities().length + _samplerFilterMode.getCapabilities().length];
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
        for (SPIRVCapability capability : _samplerAddressingMode.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _param.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _samplerFilterMode.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpConstantSampler) {
            SPIRVOpConstantSampler otherInst = (SPIRVOpConstantSampler) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._samplerAddressingMode.equals(otherInst._samplerAddressingMode)) return false;
            if (!this._param.equals(otherInst._param)) return false;
            if (!this._samplerFilterMode.equals(otherInst._samplerFilterMode)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
