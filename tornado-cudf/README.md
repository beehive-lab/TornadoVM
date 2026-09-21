# tornado-cudf

RAPIDS cuDF relational primitives as TornadoVM library tasks: sort, grouped aggregation, running
sum and inner join.

## Why this one has a shim

Every other library TornadoVM binds — cuBLAS, cuFFT, cuDNN, cuSPARSE — exposes a C ABI, so
`java.lang.foreign` calls it directly and the binding is pure Java. cuDF is C++: it returns
`std::unique_ptr<column>` by value, takes `table_view` by value, and its symbols are mangled. None
of that is callable from FFM.

So this module binds `libtornado-cudf.so`, a small `extern "C"` shim whose source is in
`src/main/native`. The shim allocates nothing the caller can see: every operand is a device
pointer TornadoVM already owns, wrapped in a non-owning `column_view`. That is what lets a cuDF
task sit in the same task graph as a generated kernel and read what it wrote.

## Building the shim

The `cuda-backend` profile builds it, in `tornado-drivers/cudf-jni`, the same way it builds the
CUTLASS and cuDNN shims. It needs RAPIDS libcudf, and **the build skips it rather than failing
when libcudf is absent** -- which is most machines. You then get a warning, no
`libtornado-cudf.so`, and a module that loads, reports itself unavailable and costs nothing, the
same thing cuSPARSE does with no CUDA toolkit installed.

So the only thing to do is install libcudf and point `CUDF_HOME` at it. The build searches
`$CUDF_HOME`, `$CONDA_PREFIX` and `/usr/local`. The quickest way to get a libcudf without building
cuDF from source is the RAPIDS wheel, which ships headers and the library together:

```bash
python3 -m venv /tmp/cudfenv
/tmp/cudfenv/bin/pip install --extra-index-url=https://pypi.nvidia.com libcudf-cu12
export CUDF_HOME=$(echo /tmp/cudfenv/lib/python3.*/site-packages/libcudf)
make BACKEND=cuda
```

Confirm it was built, rather than skipped, from the CMake line in the build output:

```
-- TornadoVM cuDF: arch=75-real;80-real;90-virtual; nvcc=...; libcudf=...; rmm=...; cccl=...
```

(`70-real` in place of `75-real` on a CUDA 12 toolkit: CUDA 13 dropped Volta and
rejects `compute_70`. Override the whole list with `CUDA_ARCH`.)

RMM's headers are searched for separately, because librmm installs as its own wheel beside
libcudf rather than under it; set `RMM_HOME` if they are somewhere else again. RMM, CCCL and
libcudf are versioned together by RAPIDS, which is why none of them is fetched from git the way
`cutlass-jni` fetches CUTLASS -- a CCCL that does not match the installed libcudf does not
compile.

The SDK assembly unpacks `libtornado-cudf.so` into its own `lib/`, so nothing needs to go on
`LD_LIBRARY_PATH` except libcudf itself.

## Using it

```java
TaskGraph graph = new TaskGraph("q")
        .transferToDevice(DataTransferMode.EVERY_EXECUTION, keys, values)
        .task("project", MyKernels::scale, values, scaled)          // generated
        .libraryTask("agg", Cudf::groupSum, n, keys, scaled, outKeys, outSums, outGroups)
        .transferToHost(DataTransferMode.EVERY_EXECUTION, outKeys, outSums, outGroups);
```

The projection is a generated kernel and the aggregation is cuDF, in one task graph, with the
intermediate never leaving the device. That composition is the reason for the module: a sort, a
grouped aggregation and a join all need cross-row cooperation that no `@Parallel` loop states,
while the per-row work around them is exactly what TornadoVM compiles well.

## What is bound

| function | cuDF | shape |
|---|---|---|
| `sortPairs` | `stable_sort_by_key` | `ORDER BY` |
| `groupSum` | `groupby::aggregate` with `SUM` | `GROUP BY k` |
| `runningSum` | `scan` inclusive | `SUM(x) OVER (...)` |
| `innerJoin` | `inner_join` | equi-join, returns index pairs |

Keys are 32-bit and values FP64, which is what a SQL planner produces. Widening that is more
entry points rather than a different design.
