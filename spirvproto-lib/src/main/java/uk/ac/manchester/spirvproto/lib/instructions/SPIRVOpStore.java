package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpStore extends SPIRVInstruction {
    public final SPIRVId _pointer;
    public final SPIRVId _object;
    public final SPIRVOptionalOperand<SPIRVMemoryAccess> _memoryAccess;

    public SPIRVOpStore(SPIRVId _pointer, SPIRVId _object, SPIRVOptionalOperand<SPIRVMemoryAccess> _memoryAccess) {
        super(62, _pointer.getWordCount() + _object.getWordCount() + _memoryAccess.getWordCount() + 1, "OpStore");
        this._pointer = _pointer;
        this._object = _object;
        this._memoryAccess = _memoryAccess;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _pointer.write(output);
        _object.write(output);
        _memoryAccess.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _pointer.print(output, options);
        output.print(" ");
 
        _object.print(output, options);
        output.print(" ");
 
        _memoryAccess.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _pointer.getCapabilities().length + _object.getCapabilities().length + _memoryAccess.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _pointer.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _object.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _memoryAccess.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpStore) {
            SPIRVOpStore otherInst = (SPIRVOpStore) other;
            if (!this._pointer.equals(otherInst._pointer)) return false;
            if (!this._object.equals(otherInst._object)) return false;
            if (!this._memoryAccess.equals(otherInst._memoryAccess)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
