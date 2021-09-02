package uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands;

import uk.ac.manchester.spirvbeehivetoolkit.lib.disassembler.SPIRVPrintingOptions;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.List;

public abstract class SPIRVEnum implements SPIRVOperand {
    protected int value;
    public String name;
    protected List<SPIRVOperand> parameters;
    public SPIRVCapability[] capabilities;

    protected SPIRVEnum(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        this.value = value;
        this.name = name;
        this.parameters = parameters;
        this.capabilities = capabilities;
    }

    @Override
    public void write(ByteBuffer output) {
        output.putInt(value);
    }

    @Override
    public int getWordCount() {
        return 1 + parameters.stream().mapToInt(SPIRVOperand::getWordCount).sum();
    }

    @Override
    public void print(PrintStream output, SPIRVPrintingOptions options) {
        output.print(name);
        if (parameters.size() > 0) output.print(" ");
        for (int i = 0, parametersSize = parameters.size(); i < parametersSize; i++) {
            SPIRVOperand p = parameters.get(i);
            p.print(output, options);
            if (i < parameters.size() - 1) output.print(" ");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVEnum) {
            SPIRVEnum otherEnum = (SPIRVEnum) other;
            if (this.value != otherEnum.value) return false;
            if (!this.name.equals(otherEnum.name)) return false;

            return this.parameters.equals(otherEnum.parameters);
        }

        return super.equals(other);
    }

    @Override
    public SPIRVCapability[] getCapabilities() {
        return capabilities;
    }
}
