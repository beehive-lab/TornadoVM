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
public class SPIRVOpBranchConditional extends SPIRVTerminationInst {
    public final SPIRVId _condition;
    public final SPIRVId _trueLabel;
    public final SPIRVId _falseLabel;
    public final SPIRVMultipleOperands<SPIRVLiteralInteger> _branchWeights;

    public SPIRVOpBranchConditional(SPIRVId _condition, SPIRVId _trueLabel, SPIRVId _falseLabel, SPIRVMultipleOperands<SPIRVLiteralInteger> _branchWeights) {
        super(250, _condition.getWordCount() + _trueLabel.getWordCount() + _falseLabel.getWordCount() + _branchWeights.getWordCount() + 1, "OpBranchConditional");
        this._condition = _condition;
        this._trueLabel = _trueLabel;
        this._falseLabel = _falseLabel;
        this._branchWeights = _branchWeights;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _condition.write(output);
        _trueLabel.write(output);
        _falseLabel.write(output);
        _branchWeights.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _condition.print(output, options);
        output.print(" ");
 
        _trueLabel.print(output, options);
        output.print(" ");
 
        _falseLabel.print(output, options);
        output.print(" ");
 
        _branchWeights.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _condition.getCapabilities().length + _trueLabel.getCapabilities().length + _falseLabel.getCapabilities().length + _branchWeights.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _condition.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _trueLabel.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _falseLabel.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _branchWeights.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpBranchConditional) {
            SPIRVOpBranchConditional otherInst = (SPIRVOpBranchConditional) other;
            if (!this._condition.equals(otherInst._condition)) return false;
            if (!this._trueLabel.equals(otherInst._trueLabel)) return false;
            if (!this._falseLabel.equals(otherInst._falseLabel)) return false;
            if (!this._branchWeights.equals(otherInst._branchWeights)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
