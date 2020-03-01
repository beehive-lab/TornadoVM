package uk.ac.manchester.tornado.drivers.cuda;

import java.util.Arrays;

public class CUDAOccupancyCalculator {
    // This is a utility class to calculate CUDA occupancy values to determine
    // maximal block sizes with minimal resource waste.
    // Based on: https://docs.nvidia.com/cuda/cuda-occupancy-calculator/index.html

    private static double SUB_OPTIMAL_MAX_DISTANCE = 0.1;

    private int warpSize;
    private int maxRegisters;
    private int allocGranularity;
    private int warpsPerProcessor;
    private int allocationUnitSize;
    private int[] maxWorkGroupsForDevice;

    public CUDAOccupancyCalculator(CUDADevice device) {
        warpSize = device.getWarpSize();
        maxRegisters = device.getMaxRegisters();
        warpsPerProcessor = device.getWarpsPerProcessor();
        allocGranularity = device.getWarpAllocationGranularity();
        allocationUnitSize = device.getRegisterAllocationUnitSize();
        maxWorkGroupsForDevice = Arrays.stream(device.getDeviceMaxWorkItemSizes()).mapToInt(i -> (int) i).toArray();
    }

    public int getMaximalBlockSize(int registersUsed, int maxBlockSize, int dimension) {
        if (maxBlockSize <= warpSize) {
            return maxBlockSize;
        }

        double processorRegisters = multiprocessorRegisters(registersUsed);

        int currentBlocks = warpSize;
        int blocks = 0;
        int maximumWarpsUsed = 0;
        int maxBlocks = Math.min(maxBlockSize, maxWorkGroupsForDevice[dimension]);

        int warpsUsed;
        // This loop will execute maximum maxThreadBlockSize/warpSize times. (currently max 1024/32 = 32)
        while (currentBlocks <= maxBlocks) {
            warpsUsed = activeWarps(currentBlocks, processorRegisters, registersUsed);
            if (warpsUsed >= maximumWarpsUsed - (maximumWarpsUsed * SUB_OPTIMAL_MAX_DISTANCE)) {
                if (warpsUsed >= maximumWarpsUsed) maximumWarpsUsed = warpsUsed;
                blocks = currentBlocks;
            }
            currentBlocks += warpSize;
        }
        return blocks;
    }


    private int activeWarps(int threadsPerBlock, double processorRegisters, int registersUsed) {
        return activeThreadBlocksPerMultiprocessor(threadsPerBlock, processorRegisters, registersUsed) * blockWarps(threadsPerBlock);
    }

    private int activeThreadBlocksPerMultiprocessor(int blockSize, double processorRegisters, int registersUsed) {
        return Math.min(
                threadBlocksPerMultiprocessorLimitedByWarpsOrBlocksPerMultiprocessor(blockSize),
                threadBlocksPerMultiprocessorLimitedByRegistersPerMultiprocessor(processorRegisters, registersUsed, blockSize)
        );
    }

    private int threadBlocksPerMultiprocessorLimitedByRegistersPerMultiprocessor(double processorRegisters, int registersUsed, int blockSize) {
        return (int) Math.floor(processorRegisters / blockRegisters(registersUsed, blockSize));
    }

    private int threadBlocksPerMultiprocessorLimitedByWarpsOrBlocksPerMultiprocessor(int blockSize) {
        return (int) Math.min(warpSize, Math.floor((double) warpsPerProcessor / blockWarps(blockSize)));
    }

    private int blockWarps(int blockSize) {
        return (int) Math.ceil((double) blockSize / warpSize);
    }

    private double blockRegisters(int registersUsed, int blockSize) {
        return ceil(registersUsed * warpSize, allocationUnitSize) * Math.ceil((double) blockSize / warpSize);
    }

    private double ceil(double a, double b) {
        return Math.ceil(a / b) * b;
    }

    private double floor (double a, double b) {
        return Math.floor(a / b) * b;
    }

    private double multiprocessorRegisters(int registersPerThread) {
        return floor( maxRegisters / ceil(registersPerThread * warpSize, allocationUnitSize), allocGranularity) * ceil(registersPerThread * warpSize, allocationUnitSize);
    }
}
