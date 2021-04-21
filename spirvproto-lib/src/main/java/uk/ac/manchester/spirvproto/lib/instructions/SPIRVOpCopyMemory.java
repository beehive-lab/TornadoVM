package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpCopyMemory extends SPIRVInstruction {
    public final SPIRVId _target;
    public final SPIRVId _source;
    public final SPIRVOptionalOperand<SPIRVMemoryAccess> _memoryAccess;

    public SPIRVOpCopyMemory(SPIRVId _target, SPIRVId _source, SPIRVOptionalOperand<SPIRVMemoryAccess> _memoryAccess) {
        super(63, _target.getWordCount() + _source.getWordCount() + _memoryAccess.getWordCount() + 1, "OpCopyMemory");
        this._target = _target;
        this._source = _source;
        this._memoryAccess = _memoryAccess;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _target.write(output);
        _source.write(output);
        _memoryAccess.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _target.print(output, options);
        output.print(" ");
 
        _source.print(output, options);
        output.print(" ");
 
        _memoryAccess.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _target.getCapabilities().length + _source.getCapabilities().length + _memoryAccess.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _target.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _source.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _memoryAccess.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpCopyMemory) {
            SPIRVOpCopyMemory otherInst = (SPIRVOpCopyMemory) other;
            if (!this._target.equals(otherInst._target)) return false;
            if (!this._source.equals(otherInst._source)) return false;
            if (!this._memoryAccess.equals(otherInst._memoryAccess)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
