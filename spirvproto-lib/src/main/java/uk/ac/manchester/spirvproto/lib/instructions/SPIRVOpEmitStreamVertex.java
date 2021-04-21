package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpEmitStreamVertex extends SPIRVInstruction {
    public final SPIRVId _stream;

    public SPIRVOpEmitStreamVertex(SPIRVId _stream) {
        super(220, _stream.getWordCount() + 1, "OpEmitStreamVertex", SPIRVCapability.GeometryStreams());
        this._stream = _stream;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _stream.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _stream.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _stream.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _stream.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpEmitStreamVertex) {
            SPIRVOpEmitStreamVertex otherInst = (SPIRVOpEmitStreamVertex) other;
            if (!this._stream.equals(otherInst._stream)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
