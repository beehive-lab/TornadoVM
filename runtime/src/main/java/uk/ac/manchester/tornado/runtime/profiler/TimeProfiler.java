package uk.ac.manchester.tornado.runtime.profiler;

import java.util.HashMap;

public class TimeProfiler extends AbstractProfiler {

    private HashMap<ProfilerType, Long> profilerTime;

    public TimeProfiler() {
        profilerTime = new HashMap<>();
    }

    @Override
    public long start(ProfilerType type) {
        long start = System.nanoTime();
        profilerTime.put(type, start);
        return start;
    }

    @Override
    public long stop(ProfilerType type) {
        long end = System.nanoTime();
        long start = profilerTime.get(type);
        long total = end - start;
        profilerTime.put(type, total);
        return end;
    }

    @Override
    public long getTimer(ProfilerType type) {
        return profilerTime.get(type);
    }

    @Override
    public void dump() {
        for (ProfilerType p : profilerTime.keySet()) {
            System.out.println("[PROFILER] " + p.getDescription() + ": " + profilerTime.get(p));
        }
    }
}
