package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpArrayLength extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _structure;
    public final SPIRVLiteralInteger _arrayMember;

    public SPIRVOpArrayLength(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _structure, SPIRVLiteralInteger _arrayMember) {
        super(68, _idResultType.getWordCount() + _idResult.getWordCount() + _structure.getWordCount() + _arrayMember.getWordCount() + 1, "OpArrayLength", SPIRVCapability.Shader());
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._structure = _structure;
        this._arrayMember = _arrayMember;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _structure.write(output);
        _arrayMember.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _structure.print(output, options);
        output.print(" ");
 
        _arrayMember.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _structure.getCapabilities().length + _arrayMember.getCapabilities().length];
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
        for (SPIRVCapability capability : _structure.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _arrayMember.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpArrayLength) {
            SPIRVOpArrayLength otherInst = (SPIRVOpArrayLength) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._structure.equals(otherInst._structure)) return false;
            if (!this._arrayMember.equals(otherInst._arrayMember)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
