package uk.ac.manchester.spirvproto.lib.instructions.operands;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;

import javax.annotation.Generated;
import java.io.PrintStream;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVLiteralExtInstInteger extends SPIRVLiteralInteger {
    private final String name;

    public SPIRVLiteralExtInstInteger(int value, String name) {
        super(value);
        this.name = name;
    }

    @Override
    public void print(PrintStream output, SPIRVPrintingOptions options) {
        output.print(name);
    }
}
