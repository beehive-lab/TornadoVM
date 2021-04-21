package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpReleaseEvent extends SPIRVInstruction {
    public final SPIRVId _event;

    public SPIRVOpReleaseEvent(SPIRVId _event) {
        super(298, _event.getWordCount() + 1, "OpReleaseEvent", SPIRVCapability.DeviceEnqueue());
        this._event = _event;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _event.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _event.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _event.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _event.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpReleaseEvent) {
            SPIRVOpReleaseEvent otherInst = (SPIRVOpReleaseEvent) other;
            if (!this._event.equals(otherInst._event)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
