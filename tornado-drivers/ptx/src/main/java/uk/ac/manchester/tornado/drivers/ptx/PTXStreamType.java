package uk.ac.manchester.tornado.drivers.ptx;

public enum PTXStreamType {
    DEFAULT,           // Backward compatibility
    DATA_TRANSFER_H2D, // Host-to-Device transfers
    COMPUTE,           // Kernel execution
    DATA_TRANSFER_D2H // Device-to-Host transfers
}
