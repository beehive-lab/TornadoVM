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
public class SPIRVOpTypeFunction extends SPIRVGlobalInst {
    public final SPIRVId _idResult;
    public final SPIRVId _returnType;
    public final SPIRVMultipleOperands<SPIRVId> _parameter0TypeParameter1Type;

    public SPIRVOpTypeFunction(SPIRVId _idResult, SPIRVId _returnType, SPIRVMultipleOperands<SPIRVId> _parameter0TypeParameter1Type) {
        super(33, _idResult.getWordCount() + _returnType.getWordCount() + _parameter0TypeParameter1Type.getWordCount() + 1, "OpTypeFunction");
        this._idResult = _idResult;
        this._returnType = _returnType;
        this._parameter0TypeParameter1Type = _parameter0TypeParameter1Type;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResult.write(output);
        _returnType.write(output);
        _parameter0TypeParameter1Type.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
          
        _returnType.print(output, options);
        output.print(" ");
 
        _parameter0TypeParameter1Type.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResult.getCapabilities().length + _returnType.getCapabilities().length + _parameter0TypeParameter1Type.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResult.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _returnType.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _parameter0TypeParameter1Type.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpTypeFunction) {
            SPIRVOpTypeFunction otherInst = (SPIRVOpTypeFunction) other;
            if (!this._returnType.equals(otherInst._returnType)) return false;
            if (!this._parameter0TypeParameter1Type.equals(otherInst._parameter0TypeParameter1Type)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
