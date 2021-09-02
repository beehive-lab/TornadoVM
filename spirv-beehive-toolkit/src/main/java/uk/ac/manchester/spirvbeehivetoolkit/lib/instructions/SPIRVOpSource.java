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
public class SPIRVOpSource extends SPIRVSourceInst {
    public final SPIRVSourceLanguage _sourceLanguage;
    public final SPIRVLiteralInteger _version;
    public final SPIRVOptionalOperand<SPIRVId> _file;
    public final SPIRVOptionalOperand<SPIRVLiteralString> _source;

    public SPIRVOpSource(SPIRVSourceLanguage _sourceLanguage, SPIRVLiteralInteger _version, SPIRVOptionalOperand<SPIRVId> _file, SPIRVOptionalOperand<SPIRVLiteralString> _source) {
        super(3, _sourceLanguage.getWordCount() + _version.getWordCount() + _file.getWordCount() + _source.getWordCount() + 1, "OpSource");
        this._sourceLanguage = _sourceLanguage;
        this._version = _version;
        this._file = _file;
        this._source = _source;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _sourceLanguage.write(output);
        _version.write(output);
        _file.write(output);
        _source.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _sourceLanguage.print(output, options);
        output.print(" ");
 
        _version.print(output, options);
        output.print(" ");
 
        _file.print(output, options);
        output.print(" ");
 
        _source.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _sourceLanguage.getCapabilities().length + _version.getCapabilities().length + _file.getCapabilities().length + _source.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _sourceLanguage.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _version.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _file.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _source.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpSource) {
            SPIRVOpSource otherInst = (SPIRVOpSource) other;
            if (!this._sourceLanguage.equals(otherInst._sourceLanguage)) return false;
            if (!this._version.equals(otherInst._version)) return false;
            if (!this._file.equals(otherInst._file)) return false;
            if (!this._source.equals(otherInst._source)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
