package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpName extends SPIRVNameInst {
    public final SPIRVId _target;
    public final SPIRVLiteralString _name;

    public SPIRVOpName(SPIRVId _target, SPIRVLiteralString _name) {
        super(5, _target.getWordCount() + _name.getWordCount() + 1, "OpName");
        this._target = _target;
        this._name = _name;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _target.write(output);
        _name.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _target.print(output, options);
        output.print(" ");
 
        _name.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _target.getCapabilities().length + _name.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _target.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _name.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpName) {
            SPIRVOpName otherInst = (SPIRVOpName) other;
            if (!this._target.equals(otherInst._target)) return false;
            if (!this._name.equals(otherInst._name)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
