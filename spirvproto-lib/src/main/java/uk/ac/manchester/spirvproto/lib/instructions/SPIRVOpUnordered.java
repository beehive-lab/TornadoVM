package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpUnordered extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _x;
    public final SPIRVId _y;

    public SPIRVOpUnordered(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _x, SPIRVId _y) {
        super(163, _idResultType.getWordCount() + _idResult.getWordCount() + _x.getWordCount() + _y.getWordCount() + 1, "OpUnordered", SPIRVCapability.Kernel());
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._x = _x;
        this._y = _y;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _x.write(output);
        _y.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _x.print(output, options);
        output.print(" ");
 
        _y.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _x.getCapabilities().length + _y.getCapabilities().length];
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
        for (SPIRVCapability capability : _x.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _y.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpUnordered) {
            SPIRVOpUnordered otherInst = (SPIRVOpUnordered) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._x.equals(otherInst._x)) return false;
            if (!this._y.equals(otherInst._y)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
