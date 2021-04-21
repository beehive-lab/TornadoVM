package uk.ac.manchester.spirvproto.lib.disassembler;

public class InvalidSPIRVEnumerantException extends RuntimeException {
    public InvalidSPIRVEnumerantException(String kind, String value) {
        super("Enumerant " + kind + " cannot have value: " + value);
    }
}
