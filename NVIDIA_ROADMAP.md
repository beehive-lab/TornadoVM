# TornadoVM × NVIDIA — Roadmap to Get NVIDIA's Attention

Goal: turn TornadoVM's hybrid API + Tensor Core work into something NVIDIA
notices and wants to amplify. NVIDIA reacts to **adoption of their GPUs from a
new ecosystem (the JVM), using their library stack, on their strategic
workloads (GenAI/LLM), visible in their tools.** Isolated GEMM benchmarks do not
move them; a real model does.

## The one move that matters

**Ship an end-to-end LLM inference demo on the JVM that runs entirely through the
hybrid API on Tensor Cores, and publish tokens/sec vs llama.cpp / TensorRT-LLM.**

- GPULlama3-class model wired through existing providers:
  - `cuBLASLt` fused GEMM+bias+GELU for MLP blocks
  - `cuDNN` fused flash-attention (SDPA) for attention
  - FP16 Tensor Cores throughout, CUDA-Graph-captured per decode step
  - Q8/Q4 weights via the `ByteArray.getHalfFloat` quantized path
- Deliverable: a table of **JVM tokens/sec vs llama.cpp vs TensorRT-LLM** on the
  same GPU. Landing within ~1.5–2× of native from Java is a legitimate NVIDIA
  Developer Blog / GTC story: *managed language, their libraries, their silicon.*

## Technical gaps to implement (ranked by NVIDIA-relevance)

1. **FP8 (E4M3/E5M2) + Tensor Core FP8.** Today: FP16 and Int8 MMA only. FP8 is
   *the* current LLM-inference story on Ada/Hopper/Blackwell. Needs an FP8 array
   type in `tornado-api` + cuBLASLt/CUTLASS FP8 GEMM with per-tensor scaling.
   **Highest signal.**
2. **Hopper `wgmma` + TMA (sm_90) path.** JIT MMA currently emits Ampere
   `mma.sync.m16n8k16`. Hopper warpgroup-MMA + async copy is what NVIDIA
   showcases now — most cheaply reached via a CUTLASS 3.x CollectiveBuilder
   (sm_90) path in the hybrid API.
3. **NCCL provider** (multi-GPU collectives). The SPI already anticipates it;
   scale-out is strategic, and a JVM NCCL binding is novel.
4. **NVTX instrumentation.** Wrap each task / library call in NVTX ranges so
   TornadoVM graphs show up labeled in **Nsight Systems / Compute**. Cheap; makes
   the framework first-class in *their* tools. — *in progress on this branch.*
5. **TensorRT / TensorRT-LLM interop** as a provider (hand a subgraph to TRT).
   Bigger lift; their flagship inference engine.
6. **cuSPARSE / cuSOLVER** providers — completes the "full CUDA-X from Java"
   claim. — *cuSPARSE next on this branch.*
7. **Grace-Hopper unified-memory path** end-to-end (`withCudaUM` exists,
   hardware-gated). NVIDIA pushes GH200 hard; a zero-copy JVM demo is distinctive.

## Outreach (parallel to the code)

- Upstream the CUTLASS (#912) + cuTENSOR (#913) provider PRs so it is real, not a
  fork.
- One **NVIDIA Developer Blog** guest post: the LLM demo + numbers + Nsight
  screenshots.
- Submit a **GTC** session; engage NVIDIA DevRel and the CUTLASS/cuTENSOR
  maintainers on GitHub once the provider PRs are visible.
- File FP8/wgmma gaps as issues referencing their libraries — engagement on their
  turf.

## Suggested build order

1. **NVTX ranges** (this branch) — a day's work, makes everything visible in
   Nsight.
2. **cuSPARSE provider** (this branch) — rounds out CUDA-X coverage.
3. **FP8 cuBLASLt/CUTLASS GEMM** — the capability NVIDIA cares about now.
4. **LLM demo** stitching the providers together with published tokens/sec — the
   actual attention-getter.

*Status: NVTX + cuSPARSE are being implemented on branch `feat/nvtx-cusparse`.
Existing hybrid providers (cuBLAS, cuBLASLt, cuFFT, cuDNN, CUTLASS, cuTENSOR) and
the JIT WMMA / Metal-simdgroup Tensor Core paths already ship — see
`HYBRID_API_GUIDE.md` and `docs/blog/reproducing-hat-tensors-in-tornadovm.md`.*
