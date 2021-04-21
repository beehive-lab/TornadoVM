package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpGroupCommitReadPipe extends SPIRVInstruction {
    public final SPIRVId _execution;
    public final SPIRVId _pipe;
    public final SPIRVId _reserveId;
    public final SPIRVId _packetSize;
    public final SPIRVId _packetAlignment;

    public SPIRVOpGroupCommitReadPipe(SPIRVId _execution, SPIRVId _pipe, SPIRVId _reserveId, SPIRVId _packetSize, SPIRVId _packetAlignment) {
        super(287, _execution.getWordCount() + _pipe.getWordCount() + _reserveId.getWordCount() + _packetSize.getWordCount() + _packetAlignment.getWordCount() + 1, "OpGroupCommitReadPipe", SPIRVCapability.Pipes());
        this._execution = _execution;
        this._pipe = _pipe;
        this._reserveId = _reserveId;
        this._packetSize = _packetSize;
        this._packetAlignment = _packetAlignment;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _execution.write(output);
        _pipe.write(output);
        _reserveId.write(output);
        _packetSize.write(output);
        _packetAlignment.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _execution.print(output, options);
        output.print(" ");
 
        _pipe.print(output, options);
        output.print(" ");
 
        _reserveId.print(output, options);
        output.print(" ");
 
        _packetSize.print(output, options);
        output.print(" ");
 
        _packetAlignment.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _execution.getCapabilities().length + _pipe.getCapabilities().length + _reserveId.getCapabilities().length + _packetSize.getCapabilities().length + _packetAlignment.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _execution.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _pipe.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _reserveId.getCapabilities()) {
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
        if (other instanceof SPIRVOpGroupCommitReadPipe) {
            SPIRVOpGroupCommitReadPipe otherInst = (SPIRVOpGroupCommitReadPipe) other;
            if (!this._execution.equals(otherInst._execution)) return false;
            if (!this._pipe.equals(otherInst._pipe)) return false;
            if (!this._reserveId.equals(otherInst._reserveId)) return false;
            if (!this._packetSize.equals(otherInst._packetSize)) return false;
            if (!this._packetAlignment.equals(otherInst._packetAlignment)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
