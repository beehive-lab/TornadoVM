package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpExtInstImport extends SPIRVInstruction {
    public final SPIRVId _idResult;
    public final SPIRVLiteralString _name;

    public SPIRVOpExtInstImport(SPIRVId _idResult, SPIRVLiteralString _name) {
        super(11, _idResult.getWordCount() + _name.getWordCount() + 1, "OpExtInstImport");
        this._idResult = _idResult;
        this._name = _name;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResult.write(output);
        _name.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
          
        _name.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResult.getCapabilities().length + _name.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResult.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _name.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpExtInstImport) {
            SPIRVOpExtInstImport otherInst = (SPIRVOpExtInstImport) other;
            if (!this._name.equals(otherInst._name)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
