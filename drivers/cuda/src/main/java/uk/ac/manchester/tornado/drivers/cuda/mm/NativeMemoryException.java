package uk.ac.manchester.tornado.drivers.cuda.mm;

public class NativeMemoryException extends Exception {
    NativeMemoryException(String msg) {
        super(msg);
    }
}
