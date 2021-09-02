package uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands;

import uk.ac.manchester.spirvbeehivetoolkit.lib.disassembler.SPIRVPrintingOptions;

import java.io.PrintStream;
import java.nio.ByteBuffer;

public class SPIRVContextDependentDouble extends SPIRVLiteralContextDependentNumber {
    private final double value;

    public SPIRVContextDependentDouble(double value) {
        this.value = value;
    }

    @Override
    public void write(ByteBuffer output) {
        output.putDouble(value);
    }

    @Override
    public int getWordCount() {
        return 2;
    }

    @Override
    public void print(PrintStream output, SPIRVPrintingOptions options) {
        String number;
        if (value == (long) value) number = String.format("%d", (long) value);
        else number = String.format("%s", value);

        output.print(number);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVContextDependentDouble) return this.value == ((SPIRVContextDependentDouble) other).value;
        else return super.equals(other);
    }
}
