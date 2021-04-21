package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpSubgroupShuffleUpINTEL extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _previous;
    public final SPIRVId _current;
    public final SPIRVId _delta;

    public SPIRVOpSubgroupShuffleUpINTEL(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _previous, SPIRVId _current, SPIRVId _delta) {
        super(5573, _idResultType.getWordCount() + _idResult.getWordCount() + _previous.getWordCount() + _current.getWordCount() + _delta.getWordCount() + 1, "OpSubgroupShuffleUpINTEL", SPIRVCapability.SubgroupShuffleINTEL());
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._previous = _previous;
        this._current = _current;
        this._delta = _delta;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _previous.write(output);
        _current.write(output);
        _delta.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _previous.print(output, options);
        output.print(" ");
 
        _current.print(output, options);
        output.print(" ");
 
        _delta.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _previous.getCapabilities().length + _current.getCapabilities().length + _delta.getCapabilities().length];
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
        for (SPIRVCapability capability : _previous.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _current.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _delta.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpSubgroupShuffleUpINTEL) {
            SPIRVOpSubgroupShuffleUpINTEL otherInst = (SPIRVOpSubgroupShuffleUpINTEL) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._previous.equals(otherInst._previous)) return false;
            if (!this._current.equals(otherInst._current)) return false;
            if (!this._delta.equals(otherInst._delta)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
