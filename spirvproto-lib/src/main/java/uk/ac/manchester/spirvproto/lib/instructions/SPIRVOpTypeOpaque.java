package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpTypeOpaque extends SPIRVGlobalInst {
    public final SPIRVId _idResult;
    public final SPIRVLiteralString _theNameOfTheOpaqueType;

    public SPIRVOpTypeOpaque(SPIRVId _idResult, SPIRVLiteralString _theNameOfTheOpaqueType) {
        super(31, _idResult.getWordCount() + _theNameOfTheOpaqueType.getWordCount() + 1, "OpTypeOpaque", SPIRVCapability.Kernel());
        this._idResult = _idResult;
        this._theNameOfTheOpaqueType = _theNameOfTheOpaqueType;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResult.write(output);
        _theNameOfTheOpaqueType.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
          
        _theNameOfTheOpaqueType.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResult.getCapabilities().length + _theNameOfTheOpaqueType.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResult.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _theNameOfTheOpaqueType.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpTypeOpaque) {
            SPIRVOpTypeOpaque otherInst = (SPIRVOpTypeOpaque) other;
            if (!this._theNameOfTheOpaqueType.equals(otherInst._theNameOfTheOpaqueType)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
