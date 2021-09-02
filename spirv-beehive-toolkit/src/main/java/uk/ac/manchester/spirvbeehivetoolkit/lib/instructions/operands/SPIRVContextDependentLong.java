package uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands;

import uk.ac.manchester.spirvbeehivetoolkit.lib.disassembler.SPIRVPrintingOptions;

import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;

public class SPIRVContextDependentLong extends SPIRVLiteralContextDependentNumber {
    private final BigInteger value;

    public SPIRVContextDependentLong(BigInteger value) {
        this.value = value;
    }

    @Override
    public void write(ByteBuffer output) {
        output.putLong(value.longValue());
    }

    @Override
    public int getWordCount() {
        return 2;
    }

    @Override
    public void print(PrintStream output, SPIRVPrintingOptions options) {
        output.print(value);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVContextDependentLong) return this.value.equals(((SPIRVContextDependentLong) other).value);
        else return super.equals(other);
    }
}
