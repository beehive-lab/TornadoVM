package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpGetKernelNDrangeMaxSubGroupSize extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _nDRange;
    public final SPIRVId _invoke;
    public final SPIRVId _param;
    public final SPIRVId _paramSize;
    public final SPIRVId _paramAlign;

    public SPIRVOpGetKernelNDrangeMaxSubGroupSize(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _nDRange, SPIRVId _invoke, SPIRVId _param, SPIRVId _paramSize, SPIRVId _paramAlign) {
        super(294, _idResultType.getWordCount() + _idResult.getWordCount() + _nDRange.getWordCount() + _invoke.getWordCount() + _param.getWordCount() + _paramSize.getWordCount() + _paramAlign.getWordCount() + 1, "OpGetKernelNDrangeMaxSubGroupSize", SPIRVCapability.DeviceEnqueue());
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._nDRange = _nDRange;
        this._invoke = _invoke;
        this._param = _param;
        this._paramSize = _paramSize;
        this._paramAlign = _paramAlign;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _nDRange.write(output);
        _invoke.write(output);
        _param.write(output);
        _paramSize.write(output);
        _paramAlign.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _nDRange.print(output, options);
        output.print(" ");
 
        _invoke.print(output, options);
        output.print(" ");
 
        _param.print(output, options);
        output.print(" ");
 
        _paramSize.print(output, options);
        output.print(" ");
 
        _paramAlign.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _nDRange.getCapabilities().length + _invoke.getCapabilities().length + _param.getCapabilities().length + _paramSize.getCapabilities().length + _paramAlign.getCapabilities().length];
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
        for (SPIRVCapability capability : _nDRange.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _invoke.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _param.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _paramSize.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _paramAlign.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpGetKernelNDrangeMaxSubGroupSize) {
            SPIRVOpGetKernelNDrangeMaxSubGroupSize otherInst = (SPIRVOpGetKernelNDrangeMaxSubGroupSize) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._nDRange.equals(otherInst._nDRange)) return false;
            if (!this._invoke.equals(otherInst._invoke)) return false;
            if (!this._param.equals(otherInst._param)) return false;
            if (!this._paramSize.equals(otherInst._paramSize)) return false;
            if (!this._paramAlign.equals(otherInst._paramAlign)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
