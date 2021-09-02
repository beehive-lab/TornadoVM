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
public class SPIRVOpLoopMerge extends SPIRVInstruction {
    public final SPIRVId _mergeBlock;
    public final SPIRVId _continueTarget;
    public final SPIRVLoopControl _loopControl;

    public SPIRVOpLoopMerge(SPIRVId _mergeBlock, SPIRVId _continueTarget, SPIRVLoopControl _loopControl) {
        super(246, _mergeBlock.getWordCount() + _continueTarget.getWordCount() + _loopControl.getWordCount() + 1, "OpLoopMerge");
        this._mergeBlock = _mergeBlock;
        this._continueTarget = _continueTarget;
        this._loopControl = _loopControl;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _mergeBlock.write(output);
        _continueTarget.write(output);
        _loopControl.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _mergeBlock.print(output, options);
        output.print(" ");
 
        _continueTarget.print(output, options);
        output.print(" ");
 
        _loopControl.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _mergeBlock.getCapabilities().length + _continueTarget.getCapabilities().length + _loopControl.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _mergeBlock.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _continueTarget.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _loopControl.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpLoopMerge) {
            SPIRVOpLoopMerge otherInst = (SPIRVOpLoopMerge) other;
            if (!this._mergeBlock.equals(otherInst._mergeBlock)) return false;
            if (!this._continueTarget.equals(otherInst._continueTarget)) return false;
            if (!this._loopControl.equals(otherInst._loopControl)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
