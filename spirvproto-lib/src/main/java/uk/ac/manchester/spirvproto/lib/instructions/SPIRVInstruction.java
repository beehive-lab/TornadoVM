package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVCapability;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;

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
