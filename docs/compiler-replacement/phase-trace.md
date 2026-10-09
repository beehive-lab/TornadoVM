# Phase 2 dynamic trace: what the Graal path actually runs

Full CUDA `tornado-test` suite on the RTX 4090 with a counting hook (`PhaseTrace`, branch `scratch/phase-trace`), 2026-10-09. Suite result unchanged by the hook (1520 pass, same 7 pre-existing failures).

| Tier | Compilations |
|---|---:|
| 0-parse(sketch) | 1,242 |
| 1-sketch | 1,239 |
| 2-high | 96,121 |
| 3-mid | 96,117 |
| 4-low | 96,117 |

The suite compiles 1,242 sketches but runs the backend tiers 96,121 times: kernels are recompiled per execution plan, device and specialisation, so **compile time per kernel matters** (C7) and a compilation cache keyed by specialisation values (architecture §3) pays off directly.

## Top-level phases by runs

| Tier | Phase | Runs | Runs that changed the graph |
|---|---|---:|---:|
| 0-parse(sketch) | `GraphBuilderPhase` | 1,239 | 1,239 |
| 1-sketch | `CanonicalizerPhase` | 2,477 | 1,122 |
| 1-sketch | `TornadoNumericPromotionPhase` | 1,239 | 0 |
| 1-sketch | `DeadCodeEliminationPhase` | 1,238 | 0 |
| 1-sketch | `IterativeConditionalEliminationPhase` | 1,238 | 1,017 |
| 1-sketch | `InliningPhase` | 1,238 | 197 |
| 1-sketch | `TornadoHalfFloatFixedGuardElimination` | 1,238 | 0 |
| 1-sketch | `TornadoNativeTypeElimination` | 1,238 | 925 |
| 1-sketch | `TornadoReduceReplacement` | 1,238 | 57 |
| 1-sketch | `TornadoStampResolver` | 1,238 | 0 |
| 1-sketch | `TornadoApiReplacement` | 1,237 | 730 |
| 1-sketch | `TornadoAutoParalleliser` | 1,237 | 0 |
| 1-sketch | `TornadoBatchFunctionAnalysis` | 1,237 | 0 |
| 1-sketch | `TornadoDataflowAnalysis` | 1,237 | 0 |
| 1-sketch | `TornadoKernelContextReplacement` | 1,237 | 277 |
| 1-sketch | `TornadoPanamaPrivateMemory` | 1,237 | 10 |
| 2-high | `CanonicalizerPhase` | 576,726 | 168,937 |
| 2-high | `DeadCodeEliminationPhase` | 288,363 | 0 |
| 2-high | `SchedulePhase` | 192,242 | 94,298 |
| 2-high | `ConvertDeoptimizeToGuardPhase` | 96,121 | 0 |
| 2-high | `LoopFullUnrollPhase` | 96,121 | 20 |
| 2-high | `IterativeConditionalEliminationPhase` | 96,121 | 0 |
| 2-high | `InliningPhase` | 96,121 | 0 |
| 2-high | `PartialEscapePhase` | 96,121 | 83 |
| 2-high | `TornadoShapeAnalysis` | 96,121 | 21,196 |
| 2-high | `TornadoValueTypeCleanup` | 96,121 | 0 |
| 2-high | `TornadoFieldAccessFixup` | 96,121 | 142 |
| 2-high | `TornadoNewArrayDevirtualizationReplacement` | 96,121 | 31 |
| 2-high | `TornadoPrivateArrayPiRemoval` | 96,121 | 10 |
| 2-high | `CUDAKernelContextNullCheckElimination` | 96,121 | 0 |
| 2-high | `TornadoBatchGlobalIndexOffset` | 96,121 | 14 |
| 2-high | `TornadoHalfFloatReplacement` | 96,121 | 108 |
| 2-high | `TornadoParallelScheduler` | 96,121 | 94,298 |
| 2-high | `TornadoTaskSpecialisation` | 96,121 | 96,111 |
| 2-high | `HighTierLoweringPhase` | 96,117 | 96,090 |
| 2-high | `ExceptionSuppression` | 96,117 | 339 |
| 2-high | `TornadoLocalMemoryAllocation` | 96,117 | 78 |
| 2-high | `TornadoCUDAIntrinsicsReplacements` | 96,117 | 78 |
| 3-mid | `CanonicalizerPhase` | 384,468 | 82 |
| 3-mid | `ExceptionCheckingElimination` | 192,234 | 0 |
| 3-mid | `FrameStateAssignmentPhase` | 96,117 | 96,117 |
| 3-mid | `GuardLoweringPhase` | 96,117 | 0 |
| 3-mid | `IterativeConditionalEliminationPhase` | 96,117 | 83 |
| 3-mid | `MidTierLoweringPhase` | 96,117 | 79 |
| 3-mid | `ReassociationPhase` | 96,117 | 103 |
| 3-mid | `RemoveValueProxyPhase` | 96,117 | 348 |
| 3-mid | `BoundCheckEliminationPhase` | 96,117 | 0 |
| 3-mid | `TornadoPanamaSegmentsHeaderPhase` | 96,117 | 0 |
| 3-mid | `TornadoFloatingReadReplacement` | 96,117 | 42,601 |
| 4-low | `CUDADynamicParallelismSupportPhase` | 96,117 | 0 |
| 4-low | `CUDATensorCoreSupportPhase` | 96,117 | 0 |
| 4-low | `AddressLoweringByNodePhase` | 96,106 | 95,826 |
| 4-low | `DeadCodeEliminationPhase` | 96,106 | 7 |
| 4-low | `FixReadsPhase` | 96,106 | 42,881 |
| 4-low | `IterativeConditionalEliminationPhase` | 96,106 | 1 |
| 4-low | `LowTierLoweringPhase` | 96,106 | 80 |
| 4-low | `UseTrappingNullChecksPhase` | 96,106 | 0 |
| 4-low | `SchedulePhase` | 96,106 | 24 |
| 4-low | `TornadoLoopCanonicalization` | 96,106 | 179 |
| 4-low | `CUDAFMAPhase` | 96,106 | 85 |
| 4-low | `CUDAFP16SupportPhase` | 96,106 | 0 |
| 4-low | `CUDAFP64SupportPhase` | 96,106 | 0 |
| 4-low | `CUDAFieldCoopsAccessPhase` | 96,106 | 142 |
| 4-low | `CUDATileSupportPhase` | 96,106 | 0 |
| 4-low | `InfinityReplacementPhase` | 96,106 | 12 |
| 4-low | `InverseSquareRootPhase` | 96,106 | 10 |
| 4-low | `TornadoAtomicsParametersPhase` | 96,106 | 14 |
| 4-low | `TornadoAtomicsScheduling` | 96,106 | 0 |
| 4-low | `TornadoFixedArrayCopyPhase` | 96,106 | 4 |
| 4-low | `TornadoHalfFloatVectorOffset` | 96,106 | 22 |

## Node classes seen

198 distinct node classes in graphs after the tiers: 76 Graal classes, 122 TornadoVM classes.

| TornadoVM node class | Occurrences after a tier |
|---|---:|
| `CUDAAddressNode` (cuda.graal.lir) | 288,329 |
| `GlobalThreadIdNode` (cuda.graal.nodes) | 286,992 |
| `GlobalThreadSizeNode` (cuda.graal.nodes) | 283,194 |
| `CUDATilePartitionViewNode` (cuda.graal.nodes) | 3,182 |
| `CUDABarrierNode` (cuda.graal.nodes) | 2,016 |
| `CUDATileLoadNode` (cuda.graal.nodes) | 1,958 |
| `CUDAFPUnaryIntrinsicNode` (cuda.graal.nodes) | 1,608 |
| `CUDATileBinaryNode` (cuda.graal.nodes) | 1,423 |
| `CUDATileStoreNode` (cuda.graal.nodes) | 1,391 |
| `LocalArrayNode` (cuda.graal.nodes) | 1,377 |
| `CUDATileBlockIdNode` (cuda.graal.nodes) | 1,172 |
| `ReadHalfFloatNode` (cuda.graal.nodes) | 1,055 |
| `CUDATileUnaryNode` (cuda.graal.nodes) | 963 |
| `CUDADecompressedReadFieldNode` (cuda.graal.nodes) | 942 |
| `CUDATileCreateNode` (cuda.graal.nodes) | 941 |
| `CUDAMMAComputeNode` (cuda.graal.nodes) | 893 |
| `CUDAConvertFloatToHalf` (cuda.graal.nodes) | 786 |
| `GroupIdNode` (cuda.graal.nodes) | 774 |
| `LocalThreadIdNode` (cuda.graal.nodes) | 690 |
| `ParallelOffsetNode` (graal.nodes) | 686 |
| `ParallelRangeNode` (graal.nodes) | 686 |
| `ParallelStrideNode` (graal.nodes) | 686 |
| `VectorAddNode` (cuda.graal.nodes.vector) | 686 |
| `CUDAFMANode` (cuda.graal.nodes) | 677 |
| `VectorValueNode` (cuda.graal.nodes.vector) | 643 |
| `TornadoAddressArithmeticNode` (graal.nodes.calc) | 612 |
| `CUDAConvertHalfBitsToIntNode` (cuda.graal.nodes) | 606 |
| `VectorLoadElementNode` (cuda.graal.nodes.vector) | 568 |
| `CUDATileViewNode` (cuda.graal.nodes) | 540 |
| `TornadoDirectCallTargetNode` (graal.nodes) | 468 |
| `CUDAMMAFragmentNode` (cuda.graal.nodes) | 467 |
| `CUDAMMALoadBNode` (cuda.graal.nodes) | 461 |
| `CUDATileReduceNode` (cuda.graal.nodes) | 460 |
| `CUDAFPBinaryIntrinsicNode` (cuda.graal.nodes) | 432 |
| `CUDAMMAStoreNode` (cuda.graal.nodes) | 432 |
| `CastNode` (cuda.graal.nodes) | 414 |
| `CUDATileMmaNode` (cuda.graal.nodes) | 345 |
| `CUDAFieldAddressArithmeticNode` (cuda.graal.nodes) | 306 |
| `WriteHalfFloatNode` (cuda.graal.nodes) | 301 |
| `CUDATileScaleNode` (cuda.graal.nodes) | 266 |
| `CUDAIntBinaryIntrinsicNode` (cuda.graal.nodes) | 260 |
| `CUDAShuffleDownNode` (cuda.graal.nodes) | 260 |
| `CUDAMMALoadANode` (cuda.graal.nodes) | 255 |
| `CUDACpAsyncCopyNode` (cuda.graal.nodes) | 246 |
| `CUDAMMALoadGlobalNode` (cuda.graal.nodes) | 245 |
| `ThreadIdFixedWithNextNode` (graal.nodes) | 236 |
| `LocalGroupSizeNode` (cuda.graal.nodes) | 234 |
| `LocalThreadIDFixedNode` (cuda.graal.nodes) | 234 |
| `LocalThreadSizeNode` (cuda.graal.nodes) | 213 |
| `CUDATileShapeOpNode` (cuda.graal.nodes) | 204 |
| `CUDAMMALoadBInt8Node` (cuda.graal.nodes) | 195 |
| `VectorHalfRead` (graal.nodes) | 184 |
| `CUDATileBinaryCallNode` (cuda.graal.nodes) | 183 |
| `ThreadLocalIdFixedWithNextNode` (graal.nodes) | 174 |
| `CUDAWarpVoteNode` (cuda.graal.nodes) | 167 |
| `CUDATileTernaryNode` (cuda.graal.nodes) | 165 |
| `CUDADeviceLaunchNode` (cuda.graal.nodes) | 148 |
| `NewHalfFloatInstance` (graal.nodes) | 142 |
| `CUDAIntUnaryIntrinsicNode` (cuda.graal.nodes) | 135 |
| `LoadIndexedVectorNode` (cuda.graal.nodes.vector) | 132 |
| `HalfFloatConstantNode` (cuda.graal.nodes) | 123 |
| `HalfFloatPlaceholder` (graal.nodes) | 119 |
| `CUDAMMALoadAInt8Node` (cuda.graal.nodes) | 116 |
| `FixedArrayNode` (cuda.graal.nodes) | 113 |
| `CUDACpAsyncCommitGroupNode` (cuda.graal.nodes) | 107 |
| `CUDACpAsyncWaitGroupNode` (cuda.graal.nodes) | 107 |
| `CUDAConvertHalfToFloat` (cuda.graal.nodes) | 105 |
| `GetGroupIdFixedWithNextNode` (graal.nodes) | 102 |
| `CUDAMMAFragmentElementNode` (cuda.graal.nodes) | 96 |
| `AddHalfNode` (cuda.graal.nodes) | 93 |
| `IncAtomicNode` (cuda.graal.nodes) | 90 |
| `CUDATileAtomicNode` (cuda.graal.nodes) | 88 |
| `CUDATileScalarCompareNode` (cuda.graal.nodes) | 82 |
| `MultHalfNode` (cuda.graal.nodes) | 75 |
| `AtomAddNodeTemplate` (cuda.graal.nodes) | 70 |
| `DivNode` (cuda.graal.nodes.calc) | 70 |
| `CUDASwizzledStoreFP16Stride32Node` (cuda.graal.nodes) | 65 |
| `CUDASimdSumNode` (cuda.graal.nodes) | 64 |
| `VectorAddHalfNode` (cuda.graal.nodes.vector) | 63 |
| `VectorMulNode` (cuda.graal.nodes.vector) | 60 |
| `AddHalfFloatNode` (graal.nodes) | 57 |
| `WriteAtomicNode` (graal.nodes) | 57 |
| `WriteAtomicNodeExtension` (graal.nodes) | 56 |
| `LocalGroupSizeFixedWithNextNode` (graal.nodes) | 53 |
| `TornadoAtomicIntegerNode` (cuda.graal.nodes) | 51 |
| `Half2IntrinsicNode` (cuda.graal.nodes) | 50 |
| `CUDAAtomicRmwNode` (cuda.graal.nodes) | 43 |
| `TornadoReduceAddNode` (graal.nodes) | 38 |
| `CUDAMMALoadBSwizzledNode` (cuda.graal.nodes) | 37 |
| `Dp4aNode` (cuda.graal.nodes) | 33 |
| `CUDAConvertFP8ToFloat` (cuda.graal.nodes) | 28 |
| `CUDAConvertBF16ToFloat` (cuda.graal.nodes) | 28 |
| `CUDAMMAStoreBSwizzledNode` (cuda.graal.nodes) | 28 |
| `CUDAKernelContextAccessNode` (cuda.graal.nodes) | 21 |
| `MultHalfFloatNode` (graal.nodes) | 20 |
| `DecAtomicNode` (cuda.graal.nodes) | 20 |
| `CUDATileConcatNode` (cuda.graal.nodes) | 20 |
| `CUDASwizzledLoadFP16Stride32Node` (cuda.graal.nodes) | 15 |
| `CUDAIntTernaryIntrinsicNode` (cuda.graal.nodes) | 15 |
| `PrintfNode` (cuda.graal.nodes) | 15 |
| `CUDAConvertIntBitsToHalfNode` (cuda.graal.nodes) | 15 |
| `VectorDivNode` (cuda.graal.nodes.vector) | 15 |
| `VectorSubNode` (cuda.graal.nodes.vector) | 15 |
| `PanamaPrivateMemoryNode` (graal.nodes) | 10 |
| `VectorStoreGlobalMemory` (cuda.graal.nodes.vector) | 10 |
| `RSqrtNode` (cuda.graal.nodes) | 10 |
| `CUDAConvertFloatToBF16` (cuda.graal.nodes) | 10 |
| `CUDATileMatmulNode` (cuda.graal.nodes) | 10 |
| `CUDASimdBroadcastFirstNode` (cuda.graal.nodes) | 8 |
| `DivHalfNode` (cuda.graal.nodes) | 6 |
| `SubHalfNode` (cuda.graal.nodes) | 6 |
| `VectorMultHalfNode` (cuda.graal.nodes.vector) | 6 |
| `GetAtomicNode` (cuda.graal.nodes) | 5 |
| `GlobalGroupSizeFixedWithNextNode` (graal.nodes) | 5 |
| `TornadoReduceMulNode` (graal.nodes) | 4 |
| `DivHalfFloatNode` (graal.nodes) | 4 |
| `SubHalfFloatNode` (graal.nodes) | 4 |
| `DP4APackedNode` (cuda.graal.nodes) | 4 |
| `FixedArrayCopyNode` (cuda.graal.nodes) | 4 |
| `ReadHalf2Node` (cuda.graal.nodes) | 3 |
| `StoreAtomicIndexedNode` (graal.nodes) | 1 |
| `StoreAtomicIndexedNodeExtension` (graal.nodes) | 1 |

## Phases of the matrix with no top-level runs

These never ran as a top-level tier phase in the suite (option-gated, dead, or nested in another phase):

- TornadoFullInliningPolicy / TornadoPartialInliningPolicy
- TornadoValueTypeReplacement
- TornadoLoopUnroller
- TornadoPartialLoopUnrollPhase
- TornadoFeatureExtraction
- DumpLowTierGraph
- TornadoPragmaUnroll
- TornadoIfCanonicalization
- CUDACanonicalizer
- CUDATileLoopPolicies
- CUDAAddressLowering
- CUDALIRGenerationPhase
- CUDAGlobalLoadBatching
- Tier suites

## Phases that never changed a graph in the suite

Evidence for the matrix classifications: these ran but did not add or remove a node in any of the suite's compilations (a run can still change edges or stamps, so this supports, not proves, "not needed").

| Tier | Phase | Runs | Origin |
|---|---|---:|---|
| 2-high | `DeadCodeEliminationPhase` | 288,363 | Graal |
| 3-mid | `ExceptionCheckingElimination` | 192,234 | TornadoVM |
| 2-high | `ConvertDeoptimizeToGuardPhase` | 96,121 | Graal |
| 2-high | `IterativeConditionalEliminationPhase` | 96,121 | Graal |
| 2-high | `InliningPhase` | 96,121 | Graal |
| 2-high | `TornadoValueTypeCleanup` | 96,121 | TornadoVM |
| 2-high | `CUDAKernelContextNullCheckElimination` | 96,121 | TornadoVM |
| 3-mid | `GuardLoweringPhase` | 96,117 | Graal |
| 3-mid | `BoundCheckEliminationPhase` | 96,117 | TornadoVM |
| 3-mid | `TornadoPanamaSegmentsHeaderPhase` | 96,117 | TornadoVM |
| 4-low | `CUDADynamicParallelismSupportPhase` | 96,117 | TornadoVM |
| 4-low | `CUDATensorCoreSupportPhase` | 96,117 | TornadoVM |
| 4-low | `UseTrappingNullChecksPhase` | 96,106 | Graal |
| 4-low | `CUDAFP16SupportPhase` | 96,106 | TornadoVM |
| 4-low | `CUDAFP64SupportPhase` | 96,106 | TornadoVM |
| 4-low | `CUDATileSupportPhase` | 96,106 | TornadoVM |
| 4-low | `TornadoAtomicsScheduling` | 96,106 | TornadoVM |
| 1-sketch | `TornadoNumericPromotionPhase` | 1,239 | TornadoVM |
| 1-sketch | `DeadCodeEliminationPhase` | 1,238 | Graal |
| 1-sketch | `TornadoHalfFloatFixedGuardElimination` | 1,238 | TornadoVM |
| 1-sketch | `TornadoStampResolver` | 1,238 | TornadoVM |
| 1-sketch | `TornadoAutoParalleliser` | 1,237 | TornadoVM |
| 1-sketch | `TornadoBatchFunctionAnalysis` | 1,237 | TornadoVM |
| 1-sketch | `TornadoDataflowAnalysis` | 1,237 | TornadoVM |
