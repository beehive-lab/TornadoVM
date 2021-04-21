package uk.ac.manchester.spirvproto.lib.instructions.operands;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVPairIdRefIdRef implements SPIRVOperand {
    private final SPIRVId member1;
    private final SPIRVId member2;

    public final SPIRVCapability[] capabilities;

    public SPIRVPairIdRefIdRef(SPIRVId member1, SPIRVId member2) {
        this.member1 = member1;
        this.member2 = member2;

        capabilities = new SPIRVCapability[member1.getCapabilities().length + member2.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : member1.getCapabilities()) {
            capabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : member2.getCapabilities()) {
            capabilities[capPos++] = capability;
        }
    }

    @Override
    public void write(ByteBuffer output) {
        member1.write(output);
        member2.write(output);
    }

    @Override
    public int getWordCount() {
        return member1.getWordCount() + member2.getWordCount();
    }

    @Override
    public void print(PrintStream output, SPIRVPrintingOptions options) {
        if (options.shouldGroup) output.print("{");
        member1.print(output, options);
        output.print(" ");
        member2.print(output, options);
        if (options.shouldGroup) output.print("}");
    }

    @Override
    public SPIRVCapability[] getCapabilities() {
        return capabilities;
    }
}
