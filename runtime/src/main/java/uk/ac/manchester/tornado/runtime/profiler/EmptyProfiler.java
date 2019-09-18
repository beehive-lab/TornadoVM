package uk.ac.manchester.tornado.runtime.profiler;

public class EmptyProfiler extends AbstractProfiler {

    @Override
    public long start(ProfilerType type) {
        return 0;
    }

    @Override
    public long stop(ProfilerType type) {
        return 0;
    }

    @Override
    public long getTimer(ProfilerType type) {
        System.out.println("Enable the profiler with: -Dtornado.profiler=True");
        return 0;
    }

    @Override
    public void dump() {

    }
}
