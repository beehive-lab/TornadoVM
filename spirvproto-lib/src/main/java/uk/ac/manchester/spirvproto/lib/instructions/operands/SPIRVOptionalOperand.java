package uk.ac.manchester.spirvproto.lib.instructions.operands;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;

import java.io.PrintStream;
import java.nio.ByteBuffer;

public class SPIRVOptionalOperand<T extends SPIRVOperand> implements SPIRVOperand {
    public SPIRVCapability[] capabilities;
    private T operand;

    public SPIRVOptionalOperand() {
        operand = null;
        capabilities = new SPIRVCapability[0];
    }

    public SPIRVOptionalOperand(T operand) {
        this.operand = operand;
        this.capabilities = operand.getCapabilities();
    }

    @Override
    public void write(ByteBuffer output) {
        if (operand != null) operand.write(output);
    }

    @Override
    public int getWordCount() {
        return operand == null ? 0: operand.getWordCount();
    }

    @Override
    public SPIRVCapability[] getCapabilities() {
        return capabilities;
    }

    @Override
    public void print(PrintStream output, SPIRVPrintingOptions options) {
        if (operand != null) operand.print(output, options);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOptionalOperand){
            SPIRVOptionalOperand<?> otherOp = (SPIRVOptionalOperand<?>) other;
            if (this.operand == null) return otherOp.operand == null;

            return this.operand.equals(otherOp.operand);
        }
        return super.equals(other);
    }

    public void setValue(T value) {
        operand = value;
    }
}
