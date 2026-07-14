# tornado-nccl

NVIDIA **NCCL** multi-GPU collectives from Java, on TornadoVM `FloatArray`
buffers. Unlike the single-device hybrid library-task providers (cuBLAS, cuDNN,
CUTLASS, cuTENSOR, cuSPARSE), NCCL is **multi-device**, so it is exposed as a
standalone `TornadoNccl` communicator rather than a `libraryTask` — one rank per
CUDA device — and is designed to combine with per-device TornadoVM task graphs.

## API

```java
import uk.ac.manchester.tornado.nccl.TornadoNccl;
import uk.ac.manchester.tornado.nccl.NcclRedOp;

try (TornadoNccl comm = new TornadoNccl(0, 1, 2, 3)) {   // one rank per GPU
    comm.allReduce(send, recv, NcclRedOp.SUM);           // recv[r] = sum over ranks
    comm.broadcast(buffers, /* root */ 0);
    comm.reduce(send, recv, /* root */ 0, NcclRedOp.MAX);
    comm.allGather(send, recv, count);                   // recv[r] length = nRanks*count
    comm.reduceScatter(send, recv, count, NcclRedOp.SUM);
}
```

Each collective takes **one `FloatArray` per rank**. Reduction operators:
`SUM`, `PROD`, `MAX`, `MIN`. On a single-GPU host, `new TornadoNccl(0)` builds a
one-rank communicator (the collective is a loopback but exercises the full path).

## Combining with TornadoVM task graphs

The intended pattern: each rank runs a `TaskGraph` on its device to produce that
rank's contribution, then a collective combines them. Distributed sum of
squares:

```java
FloatArray[] sq = /* one per rank */;
for (int r = 0; r < nRanks; r++) {
    new TaskGraph("sq" + r)
        .transferToDevice(DataTransferMode.EVERY_EXECUTION, x[r])
        .task("square", MyKernels::square, x[r], sq[r])   // JIT kernel on device r
        .transferToHost(DataTransferMode.EVERY_EXECUTION, sq[r])
        .snapshot() /* ...execute... */;
}
try (TornadoNccl comm = new TornadoNccl(devices)) {
    comm.allReduce(sq, global, NcclRedOp.SUM);            // global sum of squares across GPUs
}
```

## Build & run

Requires the CUDA backend and NCCL:

```bash
# NCCL via the pip wheel (ships headers + libnccl.so) or NVIDIA's package:
python3 -m pip install nvidia-nccl-cu12
export NCCL_ROOT=$(python3 -c "import nvidia.nccl,os;print(os.path.dirname(nvidia.nccl.__file__))")
make BACKEND=cuda

tornado-test -V uk.ac.manchester.tornado.unittests.nccl.TestNccl
tornado -m tornado.nccl/uk.ac.manchester.tornado.nccl.tests.BenchmarkNccl --params="0,1"   # device list
```

`nccl-jni` locates NCCL via `NCCL_ROOT`, `~/.local/nccl`, or the CUDA toolkit,
and embeds an rpath so `libnccl.so` resolves at load. If NCCL is not found the
native library is skipped (the build still succeeds) and `TornadoNccl` reports
UNSUPPORTED.

## Notes / scope

- FP32; collectives: allReduce, broadcast, reduce, allGather, reduceScatter.
- **First version is host-staged**: each collective copies the per-rank arrays
  to/from device buffers around the NCCL call. Sharing TornadoVM's own on-device
  buffers zero-copy (via the device-pointer path used by the hybrid API) is a
  planned optimization — it removes the host round trip for the
  compute-then-collective pattern.
- Real inter-GPU bandwidth is measured on multi-GPU hosts; on a single GPU the
  communicator has one rank (a correctness/loopback vehicle).
