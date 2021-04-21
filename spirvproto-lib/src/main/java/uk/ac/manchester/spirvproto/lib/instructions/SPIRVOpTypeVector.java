package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpTypeVector extends SPIRVGlobalInst {
    public final SPIRVId _idResult;
    public final SPIRVId _componentType;
    public final SPIRVLiteralInteger _componentCount;

    public SPIRVOpTypeVector(SPIRVId _idResult, SPIRVId _componentType, SPIRVLiteralInteger _componentCount) {
        super(23, _idResult.getWordCount() + _componentType.getWordCount() + _componentCount.getWordCount() + 1, "OpTypeVector");
        this._idResult = _idResult;
        this._componentType = _componentType;
        this._componentCount = _componentCount;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _idResult.write(output);
        _componentType.write(output);
        _componentCount.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
          
        _componentType.print(output, options);
        output.print(" ");
 
        _componentCount.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return _idResult;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _idResult.getCapabilities().length + _componentType.getCapabilities().length + _componentCount.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _idResult.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _componentType.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _componentCount.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpTypeVector) {
            SPIRVOpTypeVector otherInst = (SPIRVOpTypeVector) other;
            if (!this._componentType.equals(otherInst._componentType)) return false;
            if (!this._componentCount.equals(otherInst._componentCount)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
