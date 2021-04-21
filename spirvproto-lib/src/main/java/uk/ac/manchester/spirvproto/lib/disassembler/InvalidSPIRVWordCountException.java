package uk.ac.manchester.spirvproto.lib.disassembler;

public class InvalidSPIRVWordCountException extends Exception {
    public InvalidSPIRVWordCountException(int opcode, int wordcount) {
        super("Instruction with opcode: " + opcode + " had invalid wordcount: " + wordcount);
    }
}
