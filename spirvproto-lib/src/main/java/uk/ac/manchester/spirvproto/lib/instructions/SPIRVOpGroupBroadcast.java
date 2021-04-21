package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpGroupBroadcast extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _execution;
    public final SPIRVId _value;
    public final SPIRVId _localId;

    public SPIRVOpGroupBroadcast(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _execution, SPIRVId _value, SPIRVId _localId) {
        super(263, _idResultType.getWordCount() + _idResult.getWordCount() + _execution.getWordCount() + _value.getWordCount() + _localId.getWordCount() + 1, "OpGroupBroadcast", SPIRVCapability.Groups());
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._execution = _execution;
        this._value = _value;
        this._localId = _localId;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _execution.write(output);
        _value.write(output);
        _localId.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _execution.print(output, options);
        output.print(" ");
 
        _value.print(output, options);
        output.print(" ");
 
        _localId.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _execution.getCapabilities().length + _value.getCapabilities().length + _localId.getCapabilities().length];
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
        for (SPIRVCapability capability : _value.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _localId.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpGroupBroadcast) {
            SPIRVOpGroupBroadcast otherInst = (SPIRVOpGroupBroadcast) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._execution.equals(otherInst._execution)) return false;
            if (!this._value.equals(otherInst._value)) return false;
            if (!this._localId.equals(otherInst._localId)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
