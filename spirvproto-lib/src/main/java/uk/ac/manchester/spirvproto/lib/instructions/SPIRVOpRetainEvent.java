package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpRetainEvent extends SPIRVInstruction {
    public final SPIRVId _event;

    public SPIRVOpRetainEvent(SPIRVId _event) {
        super(297, _event.getWordCount() + 1, "OpRetainEvent", SPIRVCapability.DeviceEnqueue());
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
        if (other instanceof SPIRVOpRetainEvent) {
            SPIRVOpRetainEvent otherInst = (SPIRVOpRetainEvent) other;
            if (!this._event.equals(otherInst._event)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
