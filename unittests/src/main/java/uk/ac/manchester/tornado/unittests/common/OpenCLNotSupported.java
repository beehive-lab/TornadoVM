package uk.ac.manchester.tornado.unittests.common;

public class OpenCLNotSupported extends RuntimeException {

    public OpenCLNotSupported(String message) {
        super(message);
    }

    public OpenCLNotSupported(String message, Throwable cause) {
        super(message, cause);
    }
}
