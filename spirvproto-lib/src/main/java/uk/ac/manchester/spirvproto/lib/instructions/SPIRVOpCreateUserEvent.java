package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpCreateUserEvent extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;

    public SPIRVOpCreateUserEvent(SPIRVId _idResultType, SPIRVId _idResult) {
        super(299, _idResultType.getWordCount() + _idResult.getWordCount() + 1, "OpCreateUserEvent", SPIRVCapability.DeviceEnqueue());
        this._idResultType = _idResultType;
        this._idResult = _idResult;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
     }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length];
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

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpCreateUserEvent) {
            SPIRVOpCreateUserEvent otherInst = (SPIRVOpCreateUserEvent) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
