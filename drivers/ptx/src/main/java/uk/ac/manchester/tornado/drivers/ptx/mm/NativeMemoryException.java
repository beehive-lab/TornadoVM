package uk.ac.manchester.tornado.drivers.ptx.mm;

public class NativeMemoryException extends Exception {
    NativeMemoryException(String msg) {
        super(msg);
    }
}
