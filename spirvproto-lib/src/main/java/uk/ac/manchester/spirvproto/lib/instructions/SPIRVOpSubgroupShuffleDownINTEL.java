package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpSubgroupShuffleDownINTEL extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _current;
    public final SPIRVId _next;
    public final SPIRVId _delta;

    public SPIRVOpSubgroupShuffleDownINTEL(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _current, SPIRVId _next, SPIRVId _delta) {
        super(5572, _idResultType.getWordCount() + _idResult.getWordCount() + _current.getWordCount() + _next.getWordCount() + _delta.getWordCount() + 1, "OpSubgroupShuffleDownINTEL", SPIRVCapability.SubgroupShuffleINTEL());
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._current = _current;
        this._next = _next;
        this._delta = _delta;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _current.write(output);
        _next.write(output);
        _delta.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _current.print(output, options);
        output.print(" ");
 
        _next.print(output, options);
        output.print(" ");
 
        _delta.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _current.getCapabilities().length + _next.getCapabilities().length + _delta.getCapabilities().length];
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
        for (SPIRVCapability capability : _current.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _next.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _delta.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpSubgroupShuffleDownINTEL) {
            SPIRVOpSubgroupShuffleDownINTEL otherInst = (SPIRVOpSubgroupShuffleDownINTEL) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._current.equals(otherInst._current)) return false;
            if (!this._next.equals(otherInst._next)) return false;
            if (!this._delta.equals(otherInst._delta)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
