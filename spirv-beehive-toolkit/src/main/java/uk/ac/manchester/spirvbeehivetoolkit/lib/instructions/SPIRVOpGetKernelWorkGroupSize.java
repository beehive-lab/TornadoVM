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
public class SPIRVOpGetKernelWorkGroupSize extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _invoke;
    public final SPIRVId _param;
    public final SPIRVId _paramSize;
    public final SPIRVId _paramAlign;

    public SPIRVOpGetKernelWorkGroupSize(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _invoke, SPIRVId _param, SPIRVId _paramSize, SPIRVId _paramAlign) {
        super(295, _idResultType.getWordCount() + _idResult.getWordCount() + _invoke.getWordCount() + _param.getWordCount() + _paramSize.getWordCount() + _paramAlign.getWordCount() + 1, "OpGetKernelWorkGroupSize", SPIRVCapability.DeviceEnqueue());
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._invoke = _invoke;
        this._param = _param;
        this._paramSize = _paramSize;
        this._paramAlign = _paramAlign;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _invoke.write(output);
        _param.write(output);
        _paramSize.write(output);
        _paramAlign.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _invoke.print(output, options);
        output.print(" ");
 
        _param.print(output, options);
        output.print(" ");
 
        _paramSize.print(output, options);
        output.print(" ");
 
        _paramAlign.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _invoke.getCapabilities().length + _param.getCapabilities().length + _paramSize.getCapabilities().length + _paramAlign.getCapabilities().length];
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
        for (SPIRVCapability capability : _invoke.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _param.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _paramSize.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _paramAlign.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpGetKernelWorkGroupSize) {
            SPIRVOpGetKernelWorkGroupSize otherInst = (SPIRVOpGetKernelWorkGroupSize) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._invoke.equals(otherInst._invoke)) return false;
            if (!this._param.equals(otherInst._param)) return false;
            if (!this._paramSize.equals(otherInst._paramSize)) return false;
            if (!this._paramAlign.equals(otherInst._paramAlign)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
