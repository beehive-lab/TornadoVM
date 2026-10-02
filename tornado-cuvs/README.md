# TornadoVM Hybrid API — cuVS Library Tasks

This module lets a TornadoVM `TaskGraph` call NVIDIA [cuVS](https://github.com/rapidsai/cuvs) vector-search
routines next to JIT-compiled Java tasks. The cuVS calls:
* read and write TornadoVM-managed device buffers, passed zero-copy as DLPack tensors;
* run on the execution plan's CUDA stream.

So a Java kernel can prepare vectors for cuVS, or consume its neighbour lists, with no extra copies and no
host synchronisation.

```java
TaskGraph taskGraph = new TaskGraph("knn")
    .transferToDevice(DataTransferMode.FIRST_EXECUTION, vectors)
    .task("normalise", MyKernels::normaliseRows, vectors, n, dim)          // JIT-compiled kernel
    .libraryTask("graph", CuVS::allNeighbors, vectors, n, dim, k,          // cuVS k-NN graph
            CuVSAllNeighborsAlgo.BRUTE_FORCE.value(), CuVSDistance.INNER_PRODUCT.value(),
            neighbors, distances)
    .transferToHost(DataTransferMode.EVERY_EXECUTION, neighbors, distances);

try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
    plan.execute();
}
```

## Operations

| Factory | cuVS C API | Operation |
|---|---|---|
| `CuVS.bruteForceKnn` | `cuvsBruteForceBuild` + `cuvsBruteForceSearch` | exact k nearest dataset rows of every query |
| `CuVS.allNeighbors` | `cuvsAllNeighborsBuild` | k-NN graph of the whole dataset: `BRUTE_FORCE` (exact) or `NN_DESCENT` (approximate) |
| `CuVS.allNeighborsOnHost` | `cuvsAllNeighborsBuild` (host dataset) | k-NN graph of a host-resident dataset larger than device memory, built in batches (direct call, not a task) |
| `CuVS.kmeansFit` | `cuvsKMeansFit` | k-means (k-means++ initialisation, Lloyd iterations) |
| `CuVS.kmeansPredict` | `cuvsKMeansPredict` | nearest centroid of every row |

* Inputs are row-major FP32 `FloatArray`s.
* Neighbour ids are `LongArray` (int64, as cuVS returns them); k-means labels are `IntArray`.
* Distances use `CuVSDistance`: `L2_EXPANDED` is squared L2, `COSINE_EXPANDED` is `1 - cos`, and
  `INNER_PRODUCT` is a similarity (larger is closer).
* NN-Descent can be tuned with a `CuVSAllNeighborsOptions` argument to `CuVS.allNeighbors`: intermediate graph
  degree, maximum iterations and termination threshold. On 982,790 x 1536 vectors (RTX 4090), lowering the
  iterations from the default to 8 cut the NN-Descent time by ~40% at the same downstream recall.

## Datasets larger than device memory

`CuVS.allNeighborsOnHost` takes the dataset as a `MemorySegment` in host memory, for example a memory-mapped
file. It is a direct, synchronous call, not a task: a TornadoVM array holds at most `Integer.MAX_VALUE` elements
(#1156). With `new CuVSAllNeighborsOptions().withClusters(nClusters, overlapFactor)`, cuVS splits the rows into
`nClusters` clusters, puts every row in its `overlapFactor` nearest ones, builds each cluster on the device, and
merges the results. The outputs are host segments too.

```java
try (Arena arena = Arena.ofShared(); FileChannel ch = FileChannel.open(path)) {
    MemorySegment data = ch.map(FileChannel.MapMode.READ_ONLY, 0, rows * dim * 4L, arena);
    MemorySegment ids = arena.allocate(rows * k * 8L, 64), dists = arena.allocate(rows * k * 4L, 64);
    CuVS.allNeighborsOnHost(data, rows, dim, k, CuVSAllNeighborsAlgo.NN_DESCENT, CuVSDistance.INNER_PRODUCT,
            ids, dists, new CuVSAllNeighborsOptions().withClusters(10, 2));
}
```

## Requirements

* An NVIDIA GPU with the CUDA backend.
* The cuVS C library, `libcuvs_c.so` (bindings written against cuVS 26.08). For example:

  ```bash
  pip install --extra-index-url https://pypi.nvidia.com libcuvs-cu12
  export LD_LIBRARY_PATH=$(python3 -c 'import libcuvs, os; print(os.path.dirname(libcuvs.__file__))')/lib64:$LD_LIBRARY_PATH
  ```

  or `conda install -c rapidsai -c conda-forge libcuvs`.

The module binds to `libcuvs_c` through `java.lang.foreign`; there is no JNI shim. Without the library,
`CuVSLibraryProvider.isAvailable()` returns `false`, and the unit tests report `UNSUPPORTED`.

## Notes

* **Memory:** cuVS allocates its scratch memory itself (through RMM), so cuVS tasks cannot be captured in a
  CUDA graph. The provider throws if asked to.
* **Parameters:** for device-resident data, `allNeighbors` builds the graph in a single batch (cuVS's
  `n_clusters = 1`). For datasets that do not fit in device memory, use `allNeighborsOnHost`.

## Tests and benchmark

```bash
tornado-test -V uk.ac.manchester.tornado.unittests.cuvs.TestCuVS
tornado -m tornado.cuvs/uk.ac.manchester.tornado.cuvs.tests.BenchmarkCuVSKnn 100000 128 32
```
