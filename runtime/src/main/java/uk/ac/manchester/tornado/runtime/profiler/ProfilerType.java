package uk.ac.manchester.tornado.runtime.profiler;

public enum ProfilerType {

    // @formatter:off
    TOTAL_TIME("Total Time"),
    COMPILE_TIME("Compilation Time"),
    KERNEL_TIME("Kernel Time"),
    COPY_IN_TIME("CopyIn Time"),
    COPY_OUT_TIME("CopyOut Time");
    // @formatter:on

    String description;

    ProfilerType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.description;
    }
}
