package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpSetUserEventStatus extends SPIRVInstruction {
    public final SPIRVId _event;
    public final SPIRVId _status;

    public SPIRVOpSetUserEventStatus(SPIRVId _event, SPIRVId _status) {
        super(301, _event.getWordCount() + _status.getWordCount() + 1, "OpSetUserEventStatus", SPIRVCapability.DeviceEnqueue());
        this._event = _event;
        this._status = _status;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _event.write(output);
        _status.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _event.print(output, options);
        output.print(" ");
 
        _status.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _event.getCapabilities().length + _status.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _event.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _status.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpSetUserEventStatus) {
            SPIRVOpSetUserEventStatus otherInst = (SPIRVOpSetUserEventStatus) other;
            if (!this._event.equals(otherInst._event)) return false;
            if (!this._status.equals(otherInst._status)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
