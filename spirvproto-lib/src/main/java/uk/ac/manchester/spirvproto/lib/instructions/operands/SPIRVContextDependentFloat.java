package uk.ac.manchester.spirvproto.lib.instructions.operands;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;

import java.io.PrintStream;
import java.nio.ByteBuffer;

public class SPIRVContextDependentFloat extends SPIRVLiteralContextDependentNumber {
    private final float value;

    public SPIRVContextDependentFloat(float value) {
        this.value = value;
    }

    @Override
    public void write(ByteBuffer output) {
        output.putFloat(value);
    }

    @Override
    public int getWordCount() {
        return 1;
    }

    @Override
    public void print(PrintStream output, SPIRVPrintingOptions options) {
        String number;
        if (value == (int) value) number = String.format("%d", (int) value);
        else number = String.format("%s", value);
        output.print(number);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVContextDependentFloat) return this.value == ((SPIRVContextDependentFloat) other).value;
        else return super.equals(other);
    }
}
