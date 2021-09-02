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
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVCapability;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVId;

import java.io.PrintStream;
import java.nio.ByteBuffer;

public abstract class SPIRVInstruction {
    protected final int opCode;
    protected final int wordCount;
    public final String name;
    public final SPIRVCapability[] capabilities;

    protected SPIRVInstruction(int opCode, int wordCount, String name, SPIRVCapability... capabilities) {
        this.opCode = opCode;
        this.wordCount = wordCount;
        this.name = name;
        this.capabilities = capabilities;
    }

    public int getWordCount() {
        return wordCount;
    }

    public void write(ByteBuffer output) {
        int operation = (wordCount << 16) | (opCode);
        output.putInt(operation);
        writeOperands(output);
    }

    protected abstract void writeOperands(ByteBuffer output);

    public void print(PrintStream output, SPIRVPrintingOptions options) {
        int indent = options.indent - getResultAssigmentSize(options.shouldInlineNames);
        for (int i = 0; i < indent; i++) {
            output.print(" ");
        }

        printResultAssignment(output, options);
        output.print(name + " ");
        printOperands(output, options);
        output.println();
    }

    public int getResultAssigmentSize(boolean shouldInlineNames) {
        SPIRVId id = getResultId();
        if (id != null) return id.nameSize(shouldInlineNames) + 3;

        return 0;
    }

    protected void printResultAssignment(PrintStream output, SPIRVPrintingOptions options) {
        SPIRVId id = getResultId();
        if (id != null) {
            id.print(output, options);
            output.print(" = ");
        }
    }

    protected abstract void printOperands(PrintStream output, SPIRVPrintingOptions options);

    public abstract SPIRVId getResultId();

    public abstract SPIRVCapability[] getAllCapabilities();
}
