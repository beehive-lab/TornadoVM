package uk.ac.manchester.tornado.api;

public interface TornadoTargetDevice {

    public String getName();

    public long getGlobalMemorySize();

    public long getLocalMemorySize();

    public int getMaxComputeUnits();

}
