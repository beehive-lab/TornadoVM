package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpExtInst extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _set;
    public final SPIRVLiteralExtInstInteger _instruction;
    public final SPIRVMultipleOperands<SPIRVId> _operand1Operand2;

    public SPIRVOpExtInst(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _set, SPIRVLiteralExtInstInteger _instruction, SPIRVMultipleOperands<SPIRVId> _operand1Operand2) {
        super(12, _idResultType.getWordCount() + _idResult.getWordCount() + _set.getWordCount() + _instruction.getWordCount() + _operand1Operand2.getWordCount() + 1, "OpExtInst");
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._set = _set;
        this._instruction = _instruction;
        this._operand1Operand2 = _operand1Operand2;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _set.write(output);
        _instruction.write(output);
        _operand1Operand2.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _set.print(output, options);
        output.print(" ");
 
        _instruction.print(output, options);
        output.print(" ");
 
        _operand1Operand2.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _set.getCapabilities().length + _instruction.getCapabilities().length + _operand1Operand2.getCapabilities().length];
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
        for (SPIRVCapability capability : _set.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _instruction.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _operand1Operand2.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpExtInst) {
            SPIRVOpExtInst otherInst = (SPIRVOpExtInst) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._set.equals(otherInst._set)) return false;
            if (!this._instruction.equals(otherInst._instruction)) return false;
            if (!this._operand1Operand2.equals(otherInst._operand1Operand2)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
