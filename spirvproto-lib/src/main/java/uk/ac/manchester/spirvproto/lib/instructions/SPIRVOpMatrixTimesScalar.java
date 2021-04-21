package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpMatrixTimesScalar extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _matrix;
    public final SPIRVId _scalar;

    public SPIRVOpMatrixTimesScalar(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _matrix, SPIRVId _scalar) {
        super(143, _idResultType.getWordCount() + _idResult.getWordCount() + _matrix.getWordCount() + _scalar.getWordCount() + 1, "OpMatrixTimesScalar", SPIRVCapability.Matrix());
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._matrix = _matrix;
        this._scalar = _scalar;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _matrix.write(output);
        _scalar.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _matrix.print(output, options);
        output.print(" ");
 
        _scalar.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _matrix.getCapabilities().length + _scalar.getCapabilities().length];
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
        for (SPIRVCapability capability : _matrix.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _scalar.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpMatrixTimesScalar) {
            SPIRVOpMatrixTimesScalar otherInst = (SPIRVOpMatrixTimesScalar) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._matrix.equals(otherInst._matrix)) return false;
            if (!this._scalar.equals(otherInst._scalar)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
