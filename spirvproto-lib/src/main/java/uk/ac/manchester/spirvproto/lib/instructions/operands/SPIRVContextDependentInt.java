package uk.ac.manchester.spirvproto.lib.instructions.operands;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;

import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;

public class SPIRVContextDependentInt extends SPIRVLiteralContextDependentNumber {
    private final BigInteger value;

    public SPIRVContextDependentInt(BigInteger value) {
        this.value = value;
    }

    @Override
    public void write(ByteBuffer output) {
        output.putInt(value.intValue());
    }

    @Override
    public int getWordCount() {
        return 1;
    }

    @Override
    public void print(PrintStream output, SPIRVPrintingOptions options) {
        output.print(value);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVContextDependentInt) return this.value.equals(((SPIRVContextDependentInt) other).value);
        else return super.equals(other);
    }
}
