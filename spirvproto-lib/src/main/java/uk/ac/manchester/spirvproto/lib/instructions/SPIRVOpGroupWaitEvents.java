package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpGroupWaitEvents extends SPIRVInstruction {
    public final SPIRVId _execution;
    public final SPIRVId _numEvents;
    public final SPIRVId _eventsList;

    public SPIRVOpGroupWaitEvents(SPIRVId _execution, SPIRVId _numEvents, SPIRVId _eventsList) {
        super(260, _execution.getWordCount() + _numEvents.getWordCount() + _eventsList.getWordCount() + 1, "OpGroupWaitEvents", SPIRVCapability.Kernel());
        this._execution = _execution;
        this._numEvents = _numEvents;
        this._eventsList = _eventsList;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _execution.write(output);
        _numEvents.write(output);
        _eventsList.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _execution.print(output, options);
        output.print(" ");
 
        _numEvents.print(output, options);
        output.print(" ");
 
        _eventsList.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _execution.getCapabilities().length + _numEvents.getCapabilities().length + _eventsList.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _execution.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _numEvents.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _eventsList.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpGroupWaitEvents) {
            SPIRVOpGroupWaitEvents otherInst = (SPIRVOpGroupWaitEvents) other;
            if (!this._execution.equals(otherInst._execution)) return false;
            if (!this._numEvents.equals(otherInst._numEvents)) return false;
            if (!this._eventsList.equals(otherInst._eventsList)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
