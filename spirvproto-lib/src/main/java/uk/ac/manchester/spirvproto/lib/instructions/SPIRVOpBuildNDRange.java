package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpBuildNDRange extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _globalWorkSize;
    public final SPIRVId _localWorkSize;
    public final SPIRVId _globalWorkOffset;

    public SPIRVOpBuildNDRange(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _globalWorkSize, SPIRVId _localWorkSize, SPIRVId _globalWorkOffset) {
        super(304, _idResultType.getWordCount() + _idResult.getWordCount() + _globalWorkSize.getWordCount() + _localWorkSize.getWordCount() + _globalWorkOffset.getWordCount() + 1, "OpBuildNDRange", SPIRVCapability.DeviceEnqueue());
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._globalWorkSize = _globalWorkSize;
        this._localWorkSize = _localWorkSize;
        this._globalWorkOffset = _globalWorkOffset;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _globalWorkSize.write(output);
        _localWorkSize.write(output);
        _globalWorkOffset.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _globalWorkSize.print(output, options);
        output.print(" ");
 
        _localWorkSize.print(output, options);
        output.print(" ");
 
        _globalWorkOffset.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _globalWorkSize.getCapabilities().length + _localWorkSize.getCapabilities().length + _globalWorkOffset.getCapabilities().length];
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
        for (SPIRVCapability capability : _globalWorkSize.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _localWorkSize.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _globalWorkOffset.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpBuildNDRange) {
            SPIRVOpBuildNDRange otherInst = (SPIRVOpBuildNDRange) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._globalWorkSize.equals(otherInst._globalWorkSize)) return false;
            if (!this._localWorkSize.equals(otherInst._localWorkSize)) return false;
            if (!this._globalWorkOffset.equals(otherInst._globalWorkOffset)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
