package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpEnqueueMarker extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _queue;
    public final SPIRVId _numEvents;
    public final SPIRVId _waitEvents;
    public final SPIRVId _retEvent;

    public SPIRVOpEnqueueMarker(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _queue, SPIRVId _numEvents, SPIRVId _waitEvents, SPIRVId _retEvent) {
        super(291, _idResultType.getWordCount() + _idResult.getWordCount() + _queue.getWordCount() + _numEvents.getWordCount() + _waitEvents.getWordCount() + _retEvent.getWordCount() + 1, "OpEnqueueMarker", SPIRVCapability.DeviceEnqueue());
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._queue = _queue;
        this._numEvents = _numEvents;
        this._waitEvents = _waitEvents;
        this._retEvent = _retEvent;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _queue.write(output);
        _numEvents.write(output);
        _waitEvents.write(output);
        _retEvent.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _queue.print(output, options);
        output.print(" ");
 
        _numEvents.print(output, options);
        output.print(" ");
 
        _waitEvents.print(output, options);
        output.print(" ");
 
        _retEvent.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _queue.getCapabilities().length + _numEvents.getCapabilities().length + _waitEvents.getCapabilities().length + _retEvent.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResultType.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResult.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _queue.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _numEvents.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _waitEvents.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _retEvent.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpEnqueueMarker) {
            SPIRVOpEnqueueMarker otherInst = (SPIRVOpEnqueueMarker) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._queue.equals(otherInst._queue)) return false;
            if (!this._numEvents.equals(otherInst._numEvents)) return false;
            if (!this._waitEvents.equals(otherInst._waitEvents)) return false;
            if (!this._retEvent.equals(otherInst._retEvent)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
