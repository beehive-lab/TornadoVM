package uk.ac.manchester.tornado.runtime.profiler;

public abstract class AbstractProfiler {

    public abstract long start(ProfilerType type);

    public abstract long stop(ProfilerType type);

    public abstract long getTimer(ProfilerType type);

    public abstract void dump();

}
