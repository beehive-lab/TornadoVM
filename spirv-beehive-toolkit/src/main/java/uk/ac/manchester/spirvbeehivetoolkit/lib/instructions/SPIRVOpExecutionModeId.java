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
public class SPIRVOpExecutionModeId extends SPIRVExecutionModeInst {
    public final SPIRVId _entryPoint;
    public final SPIRVExecutionMode _mode;

    public SPIRVOpExecutionModeId(SPIRVId _entryPoint, SPIRVExecutionMode _mode) {
        super(331, _entryPoint.getWordCount() + _mode.getWordCount() + 1, "OpExecutionModeId");
        this._entryPoint = _entryPoint;
        this._mode = _mode;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _entryPoint.write(output);
        _mode.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _entryPoint.print(output, options);
        output.print(" ");
 
        _mode.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _entryPoint.getCapabilities().length + _mode.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _entryPoint.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _mode.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpExecutionModeId) {
            SPIRVOpExecutionModeId otherInst = (SPIRVOpExecutionModeId) other;
            if (!this._entryPoint.equals(otherInst._entryPoint)) return false;
            if (!this._mode.equals(otherInst._mode)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
