package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpSelect extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _condition;
    public final SPIRVId _object1;
    public final SPIRVId _object2;

    public SPIRVOpSelect(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _condition, SPIRVId _object1, SPIRVId _object2) {
        super(169, _idResultType.getWordCount() + _idResult.getWordCount() + _condition.getWordCount() + _object1.getWordCount() + _object2.getWordCount() + 1, "OpSelect");
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._condition = _condition;
        this._object1 = _object1;
        this._object2 = _object2;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _condition.write(output);
        _object1.write(output);
        _object2.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _condition.print(output, options);
        output.print(" ");
 
        _object1.print(output, options);
        output.print(" ");
 
        _object2.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _condition.getCapabilities().length + _object1.getCapabilities().length + _object2.getCapabilities().length];
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
        for (SPIRVCapability capability : _condition.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _object1.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _object2.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpSelect) {
            SPIRVOpSelect otherInst = (SPIRVOpSelect) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._condition.equals(otherInst._condition)) return false;
            if (!this._object1.equals(otherInst._object1)) return false;
            if (!this._object2.equals(otherInst._object2)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
