package uk.ac.manchester.spirvproto.lib.disassembler;

public class InvalidBinarySPIRVInputException extends Exception {
    public InvalidBinarySPIRVInputException(int magicNumber) {
        super(String.format("Invalid SPIR-V file (magic number is 0x%x instead of 0x7230203)", magicNumber));
    }
}
