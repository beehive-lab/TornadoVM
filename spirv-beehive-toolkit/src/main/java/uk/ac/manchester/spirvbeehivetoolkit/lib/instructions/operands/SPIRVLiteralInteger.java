package uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands;

import uk.ac.manchester.spirvbeehivetoolkit.lib.disassembler.SPIRVPrintingOptions;

import java.io.PrintStream;
import java.nio.ByteBuffer;

public class SPIRVLiteralInteger implements SPIRVOperand {
    public final int value;
    public SPIRVCapability[] capabilities;

    public SPIRVLiteralInteger(int value) {
        this.value = value;
        capabilities = new SPIRVCapability[0];
    }

    @Override
    public void write(ByteBuffer output) {
        output.putInt(value);
    }

    @Override
    public int getWordCount() {
        return 1;
    }

    @Override
    public SPIRVCapability[] getCapabilities() {
        return capabilities;
    }

    @Override
    public void print(PrintStream output, SPIRVPrintingOptions options) {
        output.print(options.highlighter.highlightInt(Integer.toString(value)));
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVLiteralInteger) return this.value == ((SPIRVLiteralInteger) other).value;
        return super.equals(other);
    }
}
