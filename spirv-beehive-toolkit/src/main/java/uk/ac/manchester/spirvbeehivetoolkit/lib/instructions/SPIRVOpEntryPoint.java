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
public class SPIRVOpEntryPoint extends SPIRVInstruction {
    public final SPIRVExecutionModel _executionModel;
    public final SPIRVId _entryPoint;
    public final SPIRVLiteralString _name;
    public final SPIRVMultipleOperands<SPIRVId> _interface;

    public SPIRVOpEntryPoint(SPIRVExecutionModel _executionModel, SPIRVId _entryPoint, SPIRVLiteralString _name, SPIRVMultipleOperands<SPIRVId> _interface) {
        super(15, _executionModel.getWordCount() + _entryPoint.getWordCount() + _name.getWordCount() + _interface.getWordCount() + 1, "OpEntryPoint");
        this._executionModel = _executionModel;
        this._entryPoint = _entryPoint;
        this._name = _name;
        this._interface = _interface;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _executionModel.write(output);
        _entryPoint.write(output);
        _name.write(output);
        _interface.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _executionModel.print(output, options);
        output.print(" ");
 
        _entryPoint.print(output, options);
        output.print(" ");
 
        _name.print(output, options);
        output.print(" ");
 
        _interface.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _executionModel.getCapabilities().length + _entryPoint.getCapabilities().length + _name.getCapabilities().length + _interface.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _executionModel.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _entryPoint.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _name.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _interface.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpEntryPoint) {
            SPIRVOpEntryPoint otherInst = (SPIRVOpEntryPoint) other;
            if (!this._executionModel.equals(otherInst._executionModel)) return false;
            if (!this._entryPoint.equals(otherInst._entryPoint)) return false;
            if (!this._name.equals(otherInst._name)) return false;
            if (!this._interface.equals(otherInst._interface)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
