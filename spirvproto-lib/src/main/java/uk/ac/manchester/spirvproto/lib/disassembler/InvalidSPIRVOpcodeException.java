package uk.ac.manchester.spirvproto.lib.disassembler;

public class InvalidSPIRVOpcodeException extends Exception {
    public InvalidSPIRVOpcodeException(int opcode) {
        super("No operations exist with opcode: " + opcode);
    }
}
