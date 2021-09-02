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
public class SPIRVOpCopyMemorySized extends SPIRVInstruction {
    public final SPIRVId _target;
    public final SPIRVId _source;
    public final SPIRVId _size;
    public final SPIRVOptionalOperand<SPIRVMemoryAccess> _memoryAccess;

    public SPIRVOpCopyMemorySized(SPIRVId _target, SPIRVId _source, SPIRVId _size, SPIRVOptionalOperand<SPIRVMemoryAccess> _memoryAccess) {
        super(64, _target.getWordCount() + _source.getWordCount() + _size.getWordCount() + _memoryAccess.getWordCount() + 1, "OpCopyMemorySized", SPIRVCapability.Addresses());
        this._target = _target;
        this._source = _source;
        this._size = _size;
        this._memoryAccess = _memoryAccess;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _target.write(output);
        _source.write(output);
        _size.write(output);
        _memoryAccess.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _target.print(output, options);
        output.print(" ");
 
        _source.print(output, options);
        output.print(" ");
 
        _size.print(output, options);
        output.print(" ");
 
        _memoryAccess.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _target.getCapabilities().length + _source.getCapabilities().length + _size.getCapabilities().length + _memoryAccess.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _target.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _source.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _size.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _memoryAccess.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpCopyMemorySized) {
            SPIRVOpCopyMemorySized otherInst = (SPIRVOpCopyMemorySized) other;
            if (!this._target.equals(otherInst._target)) return false;
            if (!this._source.equals(otherInst._source)) return false;
            if (!this._size.equals(otherInst._size)) return false;
            if (!this._memoryAccess.equals(otherInst._memoryAccess)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
