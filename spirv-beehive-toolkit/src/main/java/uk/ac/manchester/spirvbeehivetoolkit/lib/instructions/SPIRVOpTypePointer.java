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
public class SPIRVOpTypePointer extends SPIRVGlobalInst {
    public final SPIRVId _idResult;
    public final SPIRVStorageClass _storageClass;
    public final SPIRVId _type;

    public SPIRVOpTypePointer(SPIRVId _idResult, SPIRVStorageClass _storageClass, SPIRVId _type) {
        super(32, _idResult.getWordCount() + _storageClass.getWordCount() + _type.getWordCount() + 1, "OpTypePointer");
        this._idResult = _idResult;
        this._storageClass = _storageClass;
        this._type = _type;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResult.write(output);
        _storageClass.write(output);
        _type.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
          
        _storageClass.print(output, options);
        output.print(" ");
 
        _type.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResult.getCapabilities().length + _storageClass.getCapabilities().length + _type.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResult.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _storageClass.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _type.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpTypePointer) {
            SPIRVOpTypePointer otherInst = (SPIRVOpTypePointer) other;
            if (!this._storageClass.equals(otherInst._storageClass)) return false;
            if (!this._type.equals(otherInst._type)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
