package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpTypeStruct extends SPIRVGlobalInst {
    public final SPIRVId _idResult;
    public final SPIRVMultipleOperands<SPIRVId> _member0TypeMember1Type;

    public SPIRVOpTypeStruct(SPIRVId _idResult, SPIRVMultipleOperands<SPIRVId> _member0TypeMember1Type) {
        super(30, _idResult.getWordCount() + _member0TypeMember1Type.getWordCount() + 1, "OpTypeStruct");
        this._idResult = _idResult;
        this._member0TypeMember1Type = _member0TypeMember1Type;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResult.write(output);
        _member0TypeMember1Type.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
          
        _member0TypeMember1Type.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResult.getCapabilities().length + _member0TypeMember1Type.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResult.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _member0TypeMember1Type.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpTypeStruct) {
            SPIRVOpTypeStruct otherInst = (SPIRVOpTypeStruct) other;
            if (!this._member0TypeMember1Type.equals(otherInst._member0TypeMember1Type)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
