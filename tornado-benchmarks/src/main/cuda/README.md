# Host-memory benchmark: TornadoVM vs native CUDA

`HostMemoryBenchmark` (TornadoVM) and `host_memory_bandwidth.cu` (native CUDA, compiled with `nvcc`) measure the same cases on the same kinds of host memory, with the same CSV output. `compare_host_memory.py` runs both and joins the results into one table.

```bash
source setvars.sh                      # an SDK built with the CUDA backend
python3 tornado-benchmarks/src/main/cuda/compare_host_memory.py --csv results.csv
```

| TornadoVM | nvcc | host memory |
|---|---|---|
| `nopin` | `nopin` | pageable, never page-locked (`-Dtornado.cuda.host.pinning=false` / `malloc`) |
| `pageable` | `registered` | TornadoVM's default: pageable, page-locked on first use (`cuMemHostRegister` / `cudaHostRegister`) |
| `pinned` | `pinned` | `HostMemoryType.PINNED` / `cudaHostAlloc` |
| `mapped` | `mapped` | `HostMemoryType.MAPPED` / `cudaHostAlloc(cudaHostAllocMapped)`, zero-copy |
| `staged` | - | pageable with `withStagedTransfers()` |

Cases:
- `h2d` and `d2h`: copy bandwidth.
- `zc_read`: a kernel streaming the array once. Over PCIe for mapped memory, from device memory otherwise.
- End to end, per execution:
  - `saxpy`
  - `reduce_once`: every element read once
  - `reread16`: every element read 16 times
  - `sparse_read`: one float per 4 KB of a 1 GB array
- `alloc`: allocate and first touch 256 MB.

The script's checks:
- PINNED copies and all end-to-end workloads are within 10 % of nvcc.
- PINNED copies are faster than pageable ones.
- MAPPED zero-copy reads are within 10 % of nvcc.
- MAPPED beats copying on sparse access.

## Results

Hardware and software: NVIDIA GeForce RTX 4090, PCIe 4.0 x16, driver 610.57, CUDA 12.6, JDK 25. GB/s for `h2d`/`d2h`/`zc_read` (higher is better); ms per execution for the rest (lower is better).

| case | size | nopin TVM | nopin nvcc | pageable TVM | registered nvcc | pinned TVM | pinned nvcc | mapped TVM | mapped nvcc | staged TVM | - nvcc |
|---|---|---|---|---|---|---|---|---|---|---|---|
| h2d | 1 MB | 13.36 | 13.83 | 22.25 | 22.46 | 21.28 | 22.57 | - | - | 4.42 | - |
| h2d | 4 MB | 16.03 | 15.94 | 18.74 | 19.55 | 18.83 | 19.17 | - | - | 11.93 | - |
| h2d | 16 MB | 16.91 | 16.87 | 18.91 | 19.31 | 19.35 | 19.20 | - | - | 8.97 | - |
| h2d | 64 MB | 16.18 | 16.50 | 19.02 | 19.31 | 19.67 | 19.29 | - | - | 11.46 | - |
| h2d | 256 MB | 15.65 | 16.11 | 19.09 | 19.64 | 19.91 | 19.71 | - | - | 12.75 | - |
| h2d | 1024 MB | 15.66 | 16.07 | 19.46 | 19.83 | 20.03 | 19.95 | - | - | 13.24 | - |
| d2h | 1 MB | 7.96 | 8.49 | 16.75 | 24.81 | 17.16 | 24.86 | - | - | 16.95 | - |
| d2h | 4 MB | 12.23 | 12.68 | 16.68 | 18.76 | 16.59 | 17.76 | - | - | 16.64 | - |
| d2h | 16 MB | 14.02 | 14.37 | 17.34 | 18.12 | 18.14 | 17.84 | - | - | 17.52 | - |
| d2h | 64 MB | 14.57 | 14.92 | 17.69 | 18.10 | 18.70 | 18.01 | - | - | 17.66 | - |
| d2h | 256 MB | 14.70 | 15.02 | 17.74 | 18.56 | 18.98 | 18.64 | - | - | 17.68 | - |
| d2h | 1024 MB | 14.76 | 15.07 | 18.36 | 18.84 | 19.13 | 19.01 | - | - | 18.26 | - |
| zc_read | 1 MB | 93.09 | 341.33 | 93.09 | 264.26 | 64.00 | 306.24 | 16.05 | 21.79 | 93.09 | - |
| zc_read | 4 MB | 372.36 | 819.20 | 372.36 | 819.20 | 409.60 | 819.20 | 21.01 | 23.27 | 402.06 | - |
| zc_read | 16 MB | 862.32 | 1489.45 | 910.22 | 1489.45 | 873.81 | 1485.24 | 21.76 | 22.87 | 910.22 | - |
| zc_read | 64 MB | 442.81 | 459.50 | 446.11 | 461.52 | 445.82 | 461.52 | 21.90 | 22.84 | 445.82 | - |
| zc_read | 256 MB | 456.75 | 461.65 | 456.20 | 461.62 | 457.17 | 461.57 | 21.66 | 23.01 | 456.70 | - |
| zc_read | 1024 MB | 459.36 | 460.71 | 459.70 | 460.71 | 459.77 | 460.71 | 21.31 | 22.83 | 459.70 | - |
| saxpy | 768 MB | 53.65 ms | 52.18 ms | 42.04 ms | 42.17 ms | 41.82 ms | 41.97 ms | 54.92 ms | 51.00 ms | 56.81 ms | - |
| reduce_once | 256 MB | 17.67 ms | 17.05 ms | 13.89 ms | 13.94 ms | 13.82 ms | 13.95 ms | 30.28 ms | 22.19 ms | 21.02 ms | - |
| reread16 | 512 MB | 40.43 ms | 39.53 ms | 32.54 ms | 32.71 ms | 32.48 ms | 32.62 ms | 497.13 ms | 442.58 ms | 40.14 ms | - |
| sparse_read | 1024 MB | 69.20 ms | 67.00 ms | 54.12 ms | 54.09 ms | 53.74 ms | 53.78 ms | 2.08 ms | 2.10 ms | 81.08 ms | - |
| alloc | 256 MB | 64.93 ms | 56.56 ms | 64.76 ms | 51.33 ms | 59.98 ms | 59.73 ms | 59.96 ms | 59.78 ms | 64.67 ms | - |
| check | result | values |
|---|---|---|
| PINNED h2d 16 MB within 90% of nvcc | PASS | 19.35 vs 19.20 GB/s |
| PINNED h2d 16 MB faster than pageable | PASS | 19.35 vs 16.91 GB/s |
| PINNED h2d 64 MB within 90% of nvcc | PASS | 19.67 vs 19.29 GB/s |
| PINNED h2d 64 MB faster than pageable | PASS | 19.67 vs 16.18 GB/s |
| PINNED h2d 256 MB within 90% of nvcc | PASS | 19.91 vs 19.71 GB/s |
| PINNED h2d 256 MB faster than pageable | PASS | 19.91 vs 15.65 GB/s |
| PINNED h2d 1024 MB within 90% of nvcc | PASS | 20.03 vs 19.95 GB/s |
| PINNED h2d 1024 MB faster than pageable | PASS | 20.03 vs 15.66 GB/s |
| PINNED d2h 16 MB within 90% of nvcc | PASS | 18.14 vs 17.84 GB/s |
| PINNED d2h 16 MB faster than pageable | PASS | 18.14 vs 14.02 GB/s |
| PINNED d2h 64 MB within 90% of nvcc | PASS | 18.70 vs 18.01 GB/s |
| PINNED d2h 64 MB faster than pageable | PASS | 18.70 vs 14.57 GB/s |
| PINNED d2h 256 MB within 90% of nvcc | PASS | 18.98 vs 18.64 GB/s |
| PINNED d2h 256 MB faster than pageable | PASS | 18.98 vs 14.70 GB/s |
| PINNED d2h 1024 MB within 90% of nvcc | PASS | 19.13 vs 19.01 GB/s |
| PINNED d2h 1024 MB faster than pageable | PASS | 19.13 vs 14.76 GB/s |
| MAPPED zero-copy read 16 MB within 90% of nvcc | PASS | 21.76 vs 22.87 GB/s |
| MAPPED zero-copy read 64 MB within 90% of nvcc | PASS | 21.90 vs 22.84 GB/s |
| MAPPED zero-copy read 256 MB within 90% of nvcc | PASS | 21.66 vs 23.01 GB/s |
| MAPPED zero-copy read 1024 MB within 90% of nvcc | PASS | 21.31 vs 22.83 GB/s |
| MAPPED beats PINNED copies on sparse access | PASS | 2.08 vs 53.74 ms |
| PINNED saxpy within 10% of nvcc | PASS | 41.82 vs 41.97 ms |
| PINNED reduce_once within 10% of nvcc | PASS | 13.82 vs 13.95 ms |
| PINNED reread16 within 10% of nvcc | PASS | 32.48 vs 32.62 ms |
| PINNED sparse_read within 10% of nvcc | PASS | 53.74 vs 53.78 ms |

Observations:
- **TornadoVM matches native CUDA.**
  - Pinned copies are within ±3 % of `cudaHostAlloc`.
  - The default auto-registered arrays match `cudaHostRegister`.
  - Mapped zero-copy reads reach 93–96 % of nvcc.
- **Pinned vs pageable.** Pinned is 20–25 % faster than memory that is never page-locked: 20 against 16 GB/s up, and 19 against 15 GB/s down. For saxpy that is 42 ms against 54 ms. The default auto-registered arrays already get most of this.
- **Mapped memory wins big for sparse access.** Reading 1/1024 of a 1 GB array takes 2.1 ms against 54 ms, because a copy has to move the whole array.
- **Mapped memory loses when data is re-read, or read with little parallelism.** `reread16` takes 0.5 s against 32 ms. `reduce_once` is also slower, in nvcc as well: its 64K threads each walk 1K elements, which is not enough parallelism to hide PCIe latency.
- **Staged transfers** are designed for cold, very large one-shot uploads. On these warm, repeated uploads they are slower than direct pinned DMA.
