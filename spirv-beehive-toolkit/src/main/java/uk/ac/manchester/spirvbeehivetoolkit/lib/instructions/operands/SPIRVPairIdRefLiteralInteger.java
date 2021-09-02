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

package uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands;

import uk.ac.manchester.spirvbeehivetoolkit.lib.disassembler.SPIRVPrintingOptions;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirvbeehivetoolkit.generator")
public class SPIRVPairIdRefLiteralInteger implements SPIRVOperand {
    private final SPIRVId member1;
    private final SPIRVLiteralInteger member2;

    public final SPIRVCapability[] capabilities;

    public SPIRVPairIdRefLiteralInteger(SPIRVId member1, SPIRVLiteralInteger member2) {
        this.member1 = member1;
        this.member2 = member2;

        capabilities = new SPIRVCapability[member1.getCapabilities().length + member2.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : member1.getCapabilities()) {
            capabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : member2.getCapabilities()) {
            capabilities[capPos++] = capability;
        }
    }

    @Override
    public void write(ByteBuffer output) {
        member1.write(output);
        member2.write(output);
    }

    @Override
    public int getWordCount() {
        return member1.getWordCount() + member2.getWordCount();
    }

    @Override
    public void print(PrintStream output, SPIRVPrintingOptions options) {
        if (options.shouldGroup) output.print("{");
        member1.print(output, options);
        output.print(" ");
        member2.print(output, options);
        if (options.shouldGroup) output.print("}");
    }

    @Override
    public SPIRVCapability[] getCapabilities() {
        return capabilities;
    }
}
