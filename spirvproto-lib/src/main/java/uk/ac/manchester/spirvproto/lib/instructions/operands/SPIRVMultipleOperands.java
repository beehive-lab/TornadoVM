package uk.ac.manchester.spirvproto.lib.instructions.operands;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;

public class SPIRVMultipleOperands<T extends SPIRVOperand> extends ArrayList<T> implements SPIRVOperand {
    public final SPIRVCapability[] capabilities;

    @SafeVarargs
    public SPIRVMultipleOperands(T... operands) {
        Collections.addAll(this, operands);

        // Since these operands need to be the same type they will have the same capabilities
        if (operands.length > 0) capabilities = operands[0].getCapabilities();
        else capabilities = new SPIRVCapability[0];
    }

    @Override
    public void write(ByteBuffer output) {
        this.forEach(spirvOperand -> spirvOperand.write(output));
    }

    @Override
    public int getWordCount() {
        return this.stream().mapToInt(SPIRVOperand::getWordCount).sum();
    }

    @Override
    public SPIRVCapability[] getCapabilities() {
        return new SPIRVCapability[0];
    }

    @Override
    public void print(PrintStream output, SPIRVPrintingOptions options) {
        this.forEach(o -> {
            o.print(output, options);
            output.print(" ");
        });
    }
}
