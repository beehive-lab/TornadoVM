package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpTypeDeviceEvent extends SPIRVGlobalInst {
    public final SPIRVId _idResult;

    public SPIRVOpTypeDeviceEvent(SPIRVId _idResult) {
        super(35, _idResult.getWordCount() + 1, "OpTypeDeviceEvent", SPIRVCapability.DeviceEnqueue());
        this._idResult = _idResult;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResult.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
             }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResult.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResult.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpTypeDeviceEvent) {
            SPIRVOpTypeDeviceEvent otherInst = (SPIRVOpTypeDeviceEvent) other;
            return true;
        }
        else return super.equals(other);
    }
}
