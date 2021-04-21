package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpSpecConstantOp extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVLiteralSpecConstantOpInteger _opcode;

    public SPIRVOpSpecConstantOp(SPIRVId _idResultType, SPIRVId _idResult, SPIRVLiteralSpecConstantOpInteger _opcode) {
        super(52, _idResultType.getWordCount() + _idResult.getWordCount() + _opcode.getWordCount() + 1, "OpSpecConstantOp");
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._opcode = _opcode;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _opcode.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _opcode.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _opcode.getCapabilities().length];
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
        for (SPIRVCapability capability : _opcode.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpSpecConstantOp) {
            SPIRVOpSpecConstantOp otherInst = (SPIRVOpSpecConstantOp) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._opcode.equals(otherInst._opcode)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
