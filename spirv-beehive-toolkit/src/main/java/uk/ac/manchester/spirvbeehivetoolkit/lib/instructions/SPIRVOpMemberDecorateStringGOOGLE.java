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
public class SPIRVOpMemberDecorateStringGOOGLE extends SPIRVInstruction {
    public final SPIRVId _structType;
    public final SPIRVLiteralInteger _member;
    public final SPIRVDecoration _decoration;

    public SPIRVOpMemberDecorateStringGOOGLE(SPIRVId _structType, SPIRVLiteralInteger _member, SPIRVDecoration _decoration) {
        super(5633, _structType.getWordCount() + _member.getWordCount() + _decoration.getWordCount() + 1, "OpMemberDecorateStringGOOGLE");
        this._structType = _structType;
        this._member = _member;
        this._decoration = _decoration;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _structType.write(output);
        _member.write(output);
        _decoration.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _structType.print(output, options);
        output.print(" ");
 
        _member.print(output, options);
        output.print(" ");
 
        _decoration.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _structType.getCapabilities().length + _member.getCapabilities().length + _decoration.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _structType.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _member.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _decoration.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpMemberDecorateStringGOOGLE) {
            SPIRVOpMemberDecorateStringGOOGLE otherInst = (SPIRVOpMemberDecorateStringGOOGLE) other;
            if (!this._structType.equals(otherInst._structType)) return false;
            if (!this._member.equals(otherInst._member)) return false;
            if (!this._decoration.equals(otherInst._decoration)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
