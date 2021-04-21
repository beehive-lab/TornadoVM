package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpConstantPipeStorage extends SPIRVGlobalInst {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVLiteralInteger _packetSize;
    public final SPIRVLiteralInteger _packetAlignment;
    public final SPIRVLiteralInteger _capacity;

    public SPIRVOpConstantPipeStorage(SPIRVId _idResultType, SPIRVId _idResult, SPIRVLiteralInteger _packetSize, SPIRVLiteralInteger _packetAlignment, SPIRVLiteralInteger _capacity) {
        super(323, _idResultType.getWordCount() + _idResult.getWordCount() + _packetSize.getWordCount() + _packetAlignment.getWordCount() + _capacity.getWordCount() + 1, "OpConstantPipeStorage", SPIRVCapability.PipeStorage());
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._packetSize = _packetSize;
        this._packetAlignment = _packetAlignment;
        this._capacity = _capacity;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _packetSize.write(output);
        _packetAlignment.write(output);
        _capacity.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _packetSize.print(output, options);
        output.print(" ");
 
        _packetAlignment.print(output, options);
        output.print(" ");
 
        _capacity.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _packetSize.getCapabilities().length + _packetAlignment.getCapabilities().length + _capacity.getCapabilities().length];
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
        for (SPIRVCapability capability : _packetSize.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _packetAlignment.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _capacity.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpConstantPipeStorage) {
            SPIRVOpConstantPipeStorage otherInst = (SPIRVOpConstantPipeStorage) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._packetSize.equals(otherInst._packetSize)) return false;
            if (!this._packetAlignment.equals(otherInst._packetAlignment)) return false;
            if (!this._capacity.equals(otherInst._capacity)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
