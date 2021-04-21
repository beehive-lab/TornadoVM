package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpReserveReadPipePackets extends SPIRVInstruction {
    public final SPIRVId _idResultType;
    public final SPIRVId _idResult;
    public final SPIRVId _pipe;
    public final SPIRVId _numPackets;
    public final SPIRVId _packetSize;
    public final SPIRVId _packetAlignment;

    public SPIRVOpReserveReadPipePackets(SPIRVId _idResultType, SPIRVId _idResult, SPIRVId _pipe, SPIRVId _numPackets, SPIRVId _packetSize, SPIRVId _packetAlignment) {
        super(278, _idResultType.getWordCount() + _idResult.getWordCount() + _pipe.getWordCount() + _numPackets.getWordCount() + _packetSize.getWordCount() + _packetAlignment.getWordCount() + 1, "OpReserveReadPipePackets", SPIRVCapability.Pipes());
        this._idResultType = _idResultType;
        this._idResult = _idResult;
        this._pipe = _pipe;
        this._numPackets = _numPackets;
        this._packetSize = _packetSize;
        this._packetAlignment = _packetAlignment;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResultType.write(output);
        _idResult.write(output);
        _pipe.write(output);
        _numPackets.write(output);
        _packetSize.write(output);
        _packetAlignment.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _idResultType.print(output, options);
        output.print(" ");
  
        _pipe.print(output, options);
        output.print(" ");
 
        _numPackets.print(output, options);
        output.print(" ");
 
        _packetSize.print(output, options);
        output.print(" ");
 
        _packetAlignment.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResultType.getCapabilities().length + _idResult.getCapabilities().length + _pipe.getCapabilities().length + _numPackets.getCapabilities().length + _packetSize.getCapabilities().length + _packetAlignment.getCapabilities().length];
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
        for (SPIRVCapability capability : _pipe.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _numPackets.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _packetSize.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _packetAlignment.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpReserveReadPipePackets) {
            SPIRVOpReserveReadPipePackets otherInst = (SPIRVOpReserveReadPipePackets) other;
            if (!this._idResultType.equals(otherInst._idResultType)) return false;
            if (!this._pipe.equals(otherInst._pipe)) return false;
            if (!this._numPackets.equals(otherInst._numPackets)) return false;
            if (!this._packetSize.equals(otherInst._packetSize)) return false;
            if (!this._packetAlignment.equals(otherInst._packetAlignment)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
