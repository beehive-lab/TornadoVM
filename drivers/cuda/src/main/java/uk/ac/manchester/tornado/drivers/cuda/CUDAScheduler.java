package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

import java.util.Arrays;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.runtime.common.Tornado.DEBUG;
import static uk.ac.manchester.tornado.runtime.common.Tornado.FULL_DEBUG;
import static uk.ac.manchester.tornado.runtime.common.Tornado.warn;

public class CUDAScheduler {

    private final CUDADevice device;

    public CUDAScheduler(final CUDADevice device) {
        this.device = device;
    }

    public void calculateGlobalWork(final TaskMetaData meta, long batchThreads) {
        if (meta.isGlobalWorkDefined()) return;

        final long[] globalWork = meta.getGlobalWork();
        for (int i = 0; i < meta.getDims(); i++) {
            long value = (batchThreads <= 0) ? (long) (meta.getDomain().get(i).cardinality()) : batchThreads;
            globalWork[i] = value;
        }
    }

    public int[] calculateBlocks(CUDAModule module) {
        if (module.metaData.isLocalWorkDefined()) {
            return Arrays.stream(module.metaData.getLocalWork()).mapToInt(l -> (int) l).toArray();
        }

        int[] defaultBlocks = {1, 1, 1};
        try {
            int maxBlockThreads = module.getMaximalBlocks();
            for (int i = 0; i < module.metaData.getDims(); i++) {
//                defaultBlocks[i] = (int) Math.pow(blocks, 1 / (double) dims);
                defaultBlocks[i] = calculateBlockSize(calculateEffectiveMaxWorkItemSize(module.metaData, maxBlockThreads), module.metaData.getGlobalWork()[i]);
            }
        }
        catch (Exception e) {
            warn("[CUDA-PTX] Failed to calculate blocks for " + module.javaName);
            warn("[CUDA-PTX] Falling back to blocks: " + Arrays.toString(defaultBlocks));
            if (DEBUG || FULL_DEBUG) {
                e.printStackTrace();
            }
        }
        return defaultBlocks;
    }

    private long calculateEffectiveMaxWorkItemSize(TaskMetaData metaData, int threads) {
        if (metaData.getDims() == 0) shouldNotReachHere();
        return (long) Math.pow(threads, (double) 1 / metaData.getDims());
    }

    private int calculateBlockSize(long maxBlockSize, long globalWorkSize) {
        if (maxBlockSize == globalWorkSize) {
            maxBlockSize /= 4;
        }

        int value = (int) Math.min(maxBlockSize, globalWorkSize);
        while (globalWorkSize % value != 0) {
            value--;
        }
        return value;
    }

    public int[] calculateGrids(CUDAModule module, int[] blocks) {
        int[] defaultGrids = {1, 1, 1};

        try {
            int dims = module.metaData.getDims();
            int[] maxGridSizes = device.getDeviceMaxGridSizes();

            for (int i = 0; i < dims; i++) {
                int workSize = (int) module.metaData.getGlobalWork()[i];
                defaultGrids[i] = Math.max(Math.min(workSize / blocks[i], maxGridSizes[i]), 1);
            }
        }
        catch (Exception e) {
            warn("[CUDA-PTX] Failed to calculate grids for " + module.javaName);
            warn("[CUDA-PTX] Falling back to grid: " + Arrays.toString(defaultGrids));
            if (DEBUG || FULL_DEBUG) {
                e.printStackTrace();
            }
        }

        return defaultGrids;
    }
}
