package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpLine extends SPIRVInstruction {
    public final SPIRVId _file;
    public final SPIRVLiteralInteger _line;
    public final SPIRVLiteralInteger _column;

    public SPIRVOpLine(SPIRVId _file, SPIRVLiteralInteger _line, SPIRVLiteralInteger _column) {
        super(8, _file.getWordCount() + _line.getWordCount() + _column.getWordCount() + 1, "OpLine");
        this._file = _file;
        this._line = _line;
        this._column = _column;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _file.write(output);
        _line.write(output);
        _column.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _file.print(output, options);
        output.print(" ");
 
        _line.print(output, options);
        output.print(" ");
 
        _column.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _file.getCapabilities().length + _line.getCapabilities().length + _column.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _file.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _line.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _column.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpLine) {
            SPIRVOpLine otherInst = (SPIRVOpLine) other;
            if (!this._file.equals(otherInst._file)) return false;
            if (!this._line.equals(otherInst._line)) return false;
            if (!this._column.equals(otherInst._column)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
