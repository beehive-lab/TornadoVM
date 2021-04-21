package uk.ac.manchester.spirvproto.lib.instructions;

import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import javax.annotation.Generated;
import java.io.PrintStream;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVOpSource extends SPIRVSourceInst {
    public final SPIRVSourceLanguage _sourceLanguage;
    public final SPIRVLiteralInteger _version;
    public final SPIRVOptionalOperand<SPIRVId> _file;
    public final SPIRVOptionalOperand<SPIRVLiteralString> _source;

    public SPIRVOpSource(SPIRVSourceLanguage _sourceLanguage, SPIRVLiteralInteger _version, SPIRVOptionalOperand<SPIRVId> _file, SPIRVOptionalOperand<SPIRVLiteralString> _source) {
        super(3, _sourceLanguage.getWordCount() + _version.getWordCount() + _file.getWordCount() + _source.getWordCount() + 1, "OpSource");
        this._sourceLanguage = _sourceLanguage;
        this._version = _version;
        this._file = _file;
        this._source = _source;
    }

    @Override
    protected void writeOperands(ByteBuffer output) {
        _sourceLanguage.write(output);
        _version.write(output);
        _file.write(output);
        _source.write(output);
    }

    @Override
    protected void printOperands(PrintStream output, SPIRVPrintingOptions options) {
         
        _sourceLanguage.print(output, options);
        output.print(" ");
 
        _version.print(output, options);
        output.print(" ");
 
        _file.print(output, options);
        output.print(" ");
 
        _source.print(output, options);
    }

    @Override
    public SPIRVId getResultId() {
        return null;
    }

    @Override
    public SPIRVCapability[] getAllCapabilities() {
        SPIRVCapability[] allCapabilities = new SPIRVCapability[this.capabilities.length + _sourceLanguage.getCapabilities().length + _version.getCapabilities().length + _file.getCapabilities().length + _source.getCapabilities().length];
        int capPos = 0;
        for (SPIRVCapability capability : this.capabilities) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _sourceLanguage.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _version.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _file.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : _source.getCapabilities()) {
            allCapabilities[capPos++] = capability;
        }

        return allCapabilities;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SPIRVOpSource) {
            SPIRVOpSource otherInst = (SPIRVOpSource) other;
            if (!this._sourceLanguage.equals(otherInst._sourceLanguage)) return false;
            if (!this._version.equals(otherInst._version)) return false;
            if (!this._file.equals(otherInst._file)) return false;
            if (!this._source.equals(otherInst._source)) return false;
            return true;
        }
        else return super.equals(other);
    }
}
