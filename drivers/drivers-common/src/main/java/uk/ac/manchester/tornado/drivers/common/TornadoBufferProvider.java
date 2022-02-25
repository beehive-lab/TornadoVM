package uk.ac.manchester.tornado.drivers.common;

public interface TornadoBufferProvider {

    long getBuffer(long size);

    void markBufferReleased(long buffer, long size);

    boolean canAllocate(int allocationsRequired);

    void resetBuffers();
}
