package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpCaptureEventProfilingInfo extends SPIRVInstruction {
    public final SPIRVId _event;
    public final SPIRVId _profilingInfo;
    public final SPIRVId _value;

    public SPIRVOpCaptureEventProfilingInfo(SPIRVId _event, SPIRVId _profilingInfo, SPIRVId _value) {
        super(302, _event.getWordCount() + _profilingInfo.getWordCount() + _value.getWordCount() + 1, "OpCaptureEventProfilingInfo", SPIRVCapability.DeviceEnqueue());
        this._event = _event;
        this._profilingInfo = _profilingInfo;
        this._value = _value;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _event.write(output);
        _profilingInfo.write(output);
        _value.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _event.print(output, options);
        output.print(" ");
 
        _profilingInfo.print(output, options);
        output.print(" ");
 
        _value.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _event.getCapabilities().length + _profilingInfo.getCapabilities().length + _value.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _event.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _profilingInfo.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _value.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpCaptureEventProfilingInfo) {
            SPIRVOpCaptureEventProfilingInfo otherInst = (SPIRVOpCaptureEventProfilingInfo) other;
            if (!this._event.equals(otherInst._event)) return false;
            if (!this._profilingInfo.equals(otherInst._profilingInfo)) return false;
            if (!this._value.equals(otherInst._value)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
