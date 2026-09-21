# tornado-cudf

RAPIDS cuDF relational primitives as TornadoVM library tasks: ordering, grouping, scanning,
joining and filtering.

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
-- TornadoVM cuDF: libcudf=...; rmm=...; cccl=...; cudart=...
```

No `nvcc` in that line, and none needed: the shim declares no kernel of its own -- every
kernel it runs is already compiled inside libcudf -- and touches the CUDA runtime only for
stream copies and a synchronise. A host C++ compiler builds it, like `cudnn-jni`'s.

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
| `sortedOrder` | `stable_sorted_order` | `ORDER BY`, returns the permutation |
| `sortedOrderMulti` | `stable_sorted_order` | `ORDER BY a, b DESC` -- several keys, either direction |
| `groupSum` | `groupby::aggregate` with `SUM` | `GROUP BY k` |
| `groupAggregate` | `groupby::aggregate` | `GROUP BY k` -- `SUM`/`MIN`/`MAX`/`MEAN`/`COUNT` over several value columns at once |
| `reduce` | `reduce` | `SELECT MIN(x)` -- one aggregation over a whole column |
| `runningSum` | `scan` inclusive | `SUM(x) OVER (...)` |
| `innerJoin` | `inner_join` | equi-join, returns index pairs |
| `selectedIndices` | `apply_boolean_mask` | `WHERE` -- stream compaction, returns the surviving positions |

`sortedOrder` and `groupSum` are the narrow cases of `sortedOrderMulti` and `groupAggregate`; they
stay because they are what most callers want to read.

Three of these return *positions* rather than rows -- the permutation, the join's index pairs, the
filter's survivors -- so the caller gathers whatever columns it holds with a generated kernel and no
payload column has to be expressible on a device. Where the output size cannot be known in advance
the count comes back in element 0 of an output buffer, and a result larger than the `capacity` the
caller gave fails rather than truncating.

Keys are 32-bit and values FP64, which is what a SQL planner produces, and no column carries a
validity mask, so every operand is dense and non-null. Widening either is more entry points rather
than a different design; null support is the one that changes signatures, since it has to carry a
mask per column, and it is deliberately left out rather than half-offered.
