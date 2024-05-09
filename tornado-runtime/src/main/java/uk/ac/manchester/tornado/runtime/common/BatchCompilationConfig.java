package uk.ac.manchester.tornado.runtime.common;

/**
 * This class encapsulates all the information related to batch processing
 * that is necessary during compilation.
 */
public class BatchCompilationConfig {

    private long batchThreads;
    private int batchNumber;
    private long batchSize;

    public BatchCompilationConfig(long batchThreads, int batchNumber, long batchSize) {
        this.batchThreads = batchThreads;
        this.batchNumber = batchNumber;
        this.batchSize = batchSize;
    }

    public long getBatchThreads() {
        return batchThreads;
    }

    public int getBatchNumber() {
        return batchNumber;
    }

    public long getBatchSize() {
        return batchSize;
    }
}
