package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpShiftLeftLogical extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _base;
    public final SPIRVId _shift;

    public SPIRVOpShiftLeftLogical(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _base, SPIRVId _shift) {
        super(196, _idResultType.getWordCount() + _idResult.getWordCount() + _base.getWordCount() + _shift.getWordCount() + 1, "OpShiftLeftLogical");
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._base = _base;
        this._shift = _shift;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _base.write(output);
        _shift.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _base.print(output, options);
        output.print(" ");
 
        _shift.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _base.getCapabilities().length + _shift.getCapabilities().length];
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
        for (SPIRVCapability capability : _base.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _shift.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpShiftLeftLogical) {
            SPIRVOpShiftLeftLogical otherInst = (SPIRVOpShiftLeftLogical) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._base.equals(otherInst._base)) return false;
            if (!this._shift.equals(otherInst._shift)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
