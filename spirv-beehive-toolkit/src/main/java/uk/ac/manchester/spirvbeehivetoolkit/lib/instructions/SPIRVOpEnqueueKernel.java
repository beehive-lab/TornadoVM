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
public class SPIRVOpEnqueueKernel extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _queue;
    public final SPIRVId _flags;
    public final SPIRVId _nDRange;
    public final SPIRVId _numEvents;
    public final SPIRVId _waitEvents;
    public final SPIRVId _retEvent;
    public final SPIRVId _invoke;
    public final SPIRVId _param;
    public final SPIRVId _paramSize;
    public final SPIRVId _paramAlign;
    public final SPIRVMultipleOperands<SPIRVId> _localSize;

    public SPIRVOpEnqueueKernel(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _queue, SPIRVId _flags, SPIRVId _nDRange, SPIRVId _numEvents, SPIRVId _waitEvents, SPIRVId _retEvent, SPIRVId _invoke, SPIRVId _param, SPIRVId _paramSize, SPIRVId _paramAlign, SPIRVMultipleOperands<SPIRVId> _localSize) {
        super(292, _idResultType.getWordCount() + _idResult.getWordCount() + _queue.getWordCount() + _flags.getWordCount() + _nDRange.getWordCount() + _numEvents.getWordCount() + _waitEvents.getWordCount() + _retEvent.getWordCount() + _invoke.getWordCount() + _param.getWordCount() + _paramSize.getWordCount() + _paramAlign.getWordCount() + _localSize.getWordCount() + 1, "OpEnqueueKernel", SPIRVCapability.DeviceEnqueue());
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._queue = _queue;
        this._flags = _flags;
        this._nDRange = _nDRange;
        this._numEvents = _numEvents;
        this._waitEvents = _waitEvents;
        this._retEvent = _retEvent;
        this._invoke = _invoke;
        this._param = _param;
        this._paramSize = _paramSize;
        this._paramAlign = _paramAlign;
        this._localSize = _localSize;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _queue.write(output);
        _flags.write(output);
        _nDRange.write(output);
        _numEvents.write(output);
        _waitEvents.write(output);
        _retEvent.write(output);
        _invoke.write(output);
        _param.write(output);
        _paramSize.write(output);
        _paramAlign.write(output);
        _localSize.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _queue.print(output, options);
        output.print(" ");
 
        _flags.print(output, options);
        output.print(" ");
 
        _nDRange.print(output, options);
        output.print(" ");
 
        _numEvents.print(output, options);
        output.print(" ");
 
        _waitEvents.print(output, options);
        output.print(" ");
 
        _retEvent.print(output, options);
        output.print(" ");
 
        _invoke.print(output, options);
        output.print(" ");
 
        _param.print(output, options);
        output.print(" ");
 
        _paramSize.print(output, options);
        output.print(" ");
 
        _paramAlign.print(output, options);
        output.print(" ");
 
        _localSize.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _queue.getCapabilities().length + _flags.getCapabilities().length + _nDRange.getCapabilities().length + _numEvents.getCapabilities().length + _waitEvents.getCapabilities().length + _retEvent.getCapabilities().length + _invoke.getCapabilities().length + _param.getCapabilities().length + _paramSize.getCapabilities().length + _paramAlign.getCapabilities().length + _localSize.getCapabilities().length];
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
        for (SPIRVCapability capability : _queue.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _flags.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _nDRange.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _numEvents.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _waitEvents.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _retEvent.getCapabilities()) {
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
        for (SPIRVCapability capability : _localSize.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpEnqueueKernel) {
            SPIRVOpEnqueueKernel otherInst = (SPIRVOpEnqueueKernel) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._queue.equals(otherInst._queue)) return false;
            if (!this._flags.equals(otherInst._flags)) return false;
            if (!this._nDRange.equals(otherInst._nDRange)) return false;
            if (!this._numEvents.equals(otherInst._numEvents)) return false;
            if (!this._waitEvents.equals(otherInst._waitEvents)) return false;
            if (!this._retEvent.equals(otherInst._retEvent)) return false;
            if (!this._invoke.equals(otherInst._invoke)) return false;
            if (!this._param.equals(otherInst._param)) return false;
            if (!this._paramSize.equals(otherInst._paramSize)) return false;
            if (!this._paramAlign.equals(otherInst._paramAlign)) return false;
            if (!this._localSize.equals(otherInst._localSize)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
