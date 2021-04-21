package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpLifetimeStop extends SPIRVInstruction {
    public final SPIRVId _pointer;
    public final SPIRVLiteralInteger _size;

    public SPIRVOpLifetimeStop(SPIRVId _pointer, SPIRVLiteralInteger _size) {
        super(257, _pointer.getWordCount() + _size.getWordCount() + 1, "OpLifetimeStop", SPIRVCapability.Kernel());
        this._pointer = _pointer;
        this._size = _size;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _pointer.write(output);
        _size.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _pointer.print(output, options);
        output.print(" ");
 
        _size.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _pointer.getCapabilities().length + _size.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _pointer.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _size.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpLifetimeStop) {
            SPIRVOpLifetimeStop otherInst = (SPIRVOpLifetimeStop) other;
            if (!this._pointer.equals(otherInst._pointer)) return false;
            if (!this._size.equals(otherInst._size)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
