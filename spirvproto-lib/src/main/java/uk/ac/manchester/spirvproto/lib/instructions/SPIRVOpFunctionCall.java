package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpFunctionCall extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _function;
    public final SPIRVMultipleOperands<SPIRVId> _argument0Argument1;

    public SPIRVOpFunctionCall(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _function, SPIRVMultipleOperands<SPIRVId> _argument0Argument1) {
        super(57, _idResultType.getWordCount() + _idResult.getWordCount() + _function.getWordCount() + _argument0Argument1.getWordCount() + 1, "OpFunctionCall");
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._function = _function;
        this._argument0Argument1 = _argument0Argument1;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _function.write(output);
        _argument0Argument1.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _function.print(output, options);
        output.print(" ");
 
        _argument0Argument1.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _function.getCapabilities().length + _argument0Argument1.getCapabilities().length];
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
        for (SPIRVCapability capability : _function.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _argument0Argument1.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpFunctionCall) {
            SPIRVOpFunctionCall otherInst = (SPIRVOpFunctionCall) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._function.equals(otherInst._function)) return false;
            if (!this._argument0Argument1.equals(otherInst._argument0Argument1)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
