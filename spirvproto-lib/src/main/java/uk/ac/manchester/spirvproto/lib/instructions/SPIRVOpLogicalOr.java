package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpLogicalOr extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _operand1;
    public final SPIRVId _operand2;

    public SPIRVOpLogicalOr(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _operand1, SPIRVId _operand2) {
        super(166, _idResultType.getWordCount() + _idResult.getWordCount() + _operand1.getWordCount() + _operand2.getWordCount() + 1, "OpLogicalOr");
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._operand1 = _operand1;
        this._operand2 = _operand2;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _operand1.write(output);
        _operand2.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _operand1.print(output, options);
        output.print(" ");
 
        _operand2.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _operand1.getCapabilities().length + _operand2.getCapabilities().length];
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
        for (SPIRVCapability capability : _operand1.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _operand2.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpLogicalOr) {
            SPIRVOpLogicalOr otherInst = (SPIRVOpLogicalOr) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._operand1.equals(otherInst._operand1)) return false;
            if (!this._operand2.equals(otherInst._operand2)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
