package uk.ac.manchester.spirvproto.lib.instructions.operands;

import java.io.PrintStream;
import java.nio.ByteBuffer;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;

public class SPIRVId implements SPIRVOperand {
    public final int id;
    public SPIRVCapability[] capabilities;
    private String name;

    public SPIRVId(int id) {
        this.id = id;
        name = null;
        capabilities = new SPIRVCapability[0];
    }

    @Override
    public void write(ByteBuffer output) {
        output.putInt(id);
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
    public int hashCode() {
        return id;
    }

    @Override
    public void print(PrintStream output, SPIRVPrintingOptions options) {
        checkName(options.shouldInlineNames);
        output.print(options.highlighter.highlightId("%" + name));
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVId)
            return this.id == ((SPIRVId) other).id;
        return super.equals(other);
    }

    public int nameSize(boolean inlineNames) {
        checkName(inlineNames);
        return name.length() + 1;
    }

    private void checkName(boolean inlineNames) {
        if (!inlineNames || name == null)
            name = Integer.toString(id);
    }

    public void setName(String newName) {
        name = newName;
    }

    public String getName() {
        if (name == null)
            return "";
        return name;
    }
}
