package uk.ac.manchester.spirvproto.lib.instructions.operands;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;

import java.io.PrintStream;
import java.nio.ByteBuffer;

public class SPIRVLiteralString implements SPIRVOperand {
    public final String value;
    public final SPIRVCapability[] capabilities;

    public SPIRVLiteralString(String value) {
        this.value = value;
        capabilities = new SPIRVCapability[0];
    }

    @Override
    public void write(ByteBuffer output) {
        for (byte b : value.getBytes()) {
            output.put(b);
        }
        int alignRemaining = 4 - value.length() % 4;
        for (int i = 0; i < alignRemaining; i++) {
            output.put((byte) 0);
        }
    }

    @Override
    public int getWordCount() {
        return (value.length() / 4) + 1;
    }

    @Override
    public SPIRVCapability[] getCapabilities() {
        return capabilities;
    }

    @Override
    public void print(PrintStream output, SPIRVPrintingOptions options) {
        output.print(options.highlighter.highlightString("\"" + value + "\""));
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVLiteralString) return this.value == ((SPIRVLiteralString) other).value;
        return super.equals(other);
    }
}
