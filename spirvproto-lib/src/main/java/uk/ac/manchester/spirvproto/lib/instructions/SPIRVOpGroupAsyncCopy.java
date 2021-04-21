package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpGroupAsyncCopy extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _execution;
    public final SPIRVId _destination;
    public final SPIRVId _source;
    public final SPIRVId _numElements;
    public final SPIRVId _stride;
    public final SPIRVId _event;

    public SPIRVOpGroupAsyncCopy(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _execution, SPIRVId _destination, SPIRVId _source, SPIRVId _numElements, SPIRVId _stride, SPIRVId _event) {
        super(259, _idResultType.getWordCount() + _idResult.getWordCount() + _execution.getWordCount() + _destination.getWordCount() + _source.getWordCount() + _numElements.getWordCount() + _stride.getWordCount() + _event.getWordCount() + 1, "OpGroupAsyncCopy", SPIRVCapability.Kernel());
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._execution = _execution;
        this._destination = _destination;
        this._source = _source;
        this._numElements = _numElements;
        this._stride = _stride;
        this._event = _event;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _execution.write(output);
        _destination.write(output);
        _source.write(output);
        _numElements.write(output);
        _stride.write(output);
        _event.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _execution.print(output, options);
        output.print(" ");
 
        _destination.print(output, options);
        output.print(" ");
 
        _source.print(output, options);
        output.print(" ");
 
        _numElements.print(output, options);
        output.print(" ");
 
        _stride.print(output, options);
        output.print(" ");
 
        _event.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _execution.getCapabilities().length + _destination.getCapabilities().length + _source.getCapabilities().length + _numElements.getCapabilities().length + _stride.getCapabilities().length + _event.getCapabilities().length];
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
        for (SPIRVCapability capability : _execution.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _destination.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _source.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _numElements.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _stride.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _event.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpGroupAsyncCopy) {
            SPIRVOpGroupAsyncCopy otherInst = (SPIRVOpGroupAsyncCopy) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._execution.equals(otherInst._execution)) return false;
            if (!this._destination.equals(otherInst._destination)) return false;
            if (!this._source.equals(otherInst._source)) return false;
            if (!this._numElements.equals(otherInst._numElements)) return false;
            if (!this._stride.equals(otherInst._stride)) return false;
            if (!this._event.equals(otherInst._event)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
