package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpMatrixTimesVector extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _matrix;
    public final SPIRVId _vector;

    public SPIRVOpMatrixTimesVector(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _matrix, SPIRVId _vector) {
        super(145, _idResultType.getWordCount() + _idResult.getWordCount() + _matrix.getWordCount() + _vector.getWordCount() + 1, "OpMatrixTimesVector", SPIRVCapability.Matrix());
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._matrix = _matrix;
        this._vector = _vector;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _matrix.write(output);
        _vector.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _matrix.print(output, options);
        output.print(" ");
 
        _vector.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _matrix.getCapabilities().length + _vector.getCapabilities().length];
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
        for (SPIRVCapability capability : _vector.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpMatrixTimesVector) {
            SPIRVOpMatrixTimesVector otherInst = (SPIRVOpMatrixTimesVector) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._matrix.equals(otherInst._matrix)) return false;
            if (!this._vector.equals(otherInst._vector)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
