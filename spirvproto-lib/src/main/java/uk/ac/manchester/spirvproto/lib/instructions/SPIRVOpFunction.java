package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpFunction extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVFunctionControl _functionControl;
    public final SPIRVId _functionType;

    public SPIRVOpFunction(SPIRVId _idResultType, SPIRVId _idResult, SPIRVFunctionControl _functionControl, SPIRVId _functionType) {
        super(54, _idResultType.getWordCount() + _idResult.getWordCount() + _functionControl.getWordCount() + _functionType.getWordCount() + 1, "OpFunction");
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._functionControl = _functionControl;
        this._functionType = _functionType;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _functionControl.write(output);
        _functionType.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _functionControl.print(output, options);
        output.print(" ");
 
        _functionType.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _functionControl.getCapabilities().length + _functionType.getCapabilities().length];
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
        for (SPIRVCapability capability : _functionControl.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _functionType.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpFunction) {
            SPIRVOpFunction otherInst = (SPIRVOpFunction) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._functionControl.equals(otherInst._functionControl)) return false;
            if (!this._functionType.equals(otherInst._functionType)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
