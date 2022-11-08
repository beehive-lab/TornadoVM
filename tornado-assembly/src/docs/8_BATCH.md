## Batch Computing Processing


We currently enable batch processing through the following API call of the `TaskSchedule`.

```java
int size = 2110000000;
float[] arrayA = new float[size]; // Array of 8440MB
float[] arrayB = new float[size]; // Array of 8440MB

ts.batch("512MB") // Run with a block of 512MB
  .task("t0", InitBatch::compute, arrayA, arrayB)
  .streamOut(arrayB)
  .execute();
```

The batch method-call receives a Java string representing the batch to be allocated, copied-in and copied-out to/from the heap of the target device.

Examples of allowed sizes are:



```java
batch("XMB");   // Express in MB (X is an int number)
batch("ZGB");   // Express in GB (Z is an int number)
```

### TornadoVM Batch Processing Internals

Internally, if we detect that a specific task is invoked with the batch-call, we generate new bytecodes. Those new bytecodes are a variant of the existing ones but passing the offset and the size to be executed. For instance, we generate the following sequence of bytecodes:


```bash
vm: BEGIN
vm: COPY_IN_BATCH size=512000000, offset=0
vm: ALLOCATE_BATCH, size=512000000
vm: LAUNCH_BATCH size=128000000, offset=0
vm: STREAM_OUT_BATCH size=512000000, offset=0
vm: COPY_IN_BATCH size=512000000, offset=512000000
vm: ALLOCATE_BATCH size=512000000
vm: LAUNCH_BATCH offset=512000000
vm: STREAM_OUT_BATCH size=512000000, offset=512000000
vm: COPY_IN_BATCH size=176000000, offset=1024000000
vm: ALLOCATE_BATCH size=176000000
vm: LAUNCH_BATCH size=44000000, offset=1024000000
vm: STREAM_OUT_BLOCKING_BATCH, offset=1024000000
vm: END
```

Notice that we repeat the sequence `COPY_IN`, `ALLOCATE`, `LAUNCH` and `STREAM_OUT` but passing different sizes and offsets.

All copies and launches are executed asynchronously between the host and the target device. Only the last copy is synchronous. We also use the same OpenCL command queue / CUDA stream for running all the commands. Future work might include here support for multiple command queues.

### Current Limitations

There is a set of limitations with the current implementation of batch processing.

1. All arrays passed to the input methods to be compiled to the target device have to have the same data type and size.
1. We only support arrays of primitives that are passed as arguments. This means that scope arrays in batches are not currently supported.
1. All bytecodes make use of the same OpenCL command queue / CUDA stream.
1. Matrix or non-regular batch distributions. (E.g., MxM would need to be split by rows in matrix-A and columns in matrix-B).
