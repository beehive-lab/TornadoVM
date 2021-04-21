package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpMemberName extends SPIRVNameInst {
    public final SPIRVId _type;
    public final SPIRVLiteralInteger _member;
    public final SPIRVLiteralString _name;

    public SPIRVOpMemberName(SPIRVId _type, SPIRVLiteralInteger _member, SPIRVLiteralString _name) {
        super(6, _type.getWordCount() + _member.getWordCount() + _name.getWordCount() + 1, "OpMemberName");
        this._type = _type;
        this._member = _member;
        this._name = _name;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _type.write(output);
        _member.write(output);
        _name.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _type.print(output, options);
        output.print(" ");
 
        _member.print(output, options);
        output.print(" ");
 
        _name.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _type.getCapabilities().length + _member.getCapabilities().length + _name.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _type.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _member.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _name.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpMemberName) {
            SPIRVOpMemberName otherInst = (SPIRVOpMemberName) other;
            if (!this._type.equals(otherInst._type)) return false;
            if (!this._member.equals(otherInst._member)) return false;
            if (!this._name.equals(otherInst._name)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
