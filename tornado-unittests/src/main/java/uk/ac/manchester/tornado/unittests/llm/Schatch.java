//package uk.ac.manchester.tornado.unittests.llm;
//
//import uk.ac.manchester.tornado.api.GridScheduler;
//import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
//import uk.ac.manchester.tornado.api.KernelContext;
//import uk.ac.manchester.tornado.api.TaskGraph;
//import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
//import uk.ac.manchester.tornado.api.WorkerGrid;
//import uk.ac.manchester.tornado.api.WorkerGrid1D;
//import uk.ac.manchester.tornado.api.enums.DataTransferMode;
//
//import java.lang.foreign.MemorySegment;
//
//public class Schatch {
//    public static FloatTensor forward(Llama model, State state, int token, int position,
//            Tuple2<TornadoExecutionPlan, GridScheduler> executionPlanTuple) {
//        // Configuration parameters
//        Configuration config = model.configuration();
//        Weights weights = model.weights();
//        int dim = config.dim;
//        int headSize = config.headSize;
//        int numHeads = config.numberOfHeads;
//        int numKVHeads = config.numberOfKeyValueHeads;
//        int kvDim = (dim * numKVHeads) / numHeads;
//        int kvMul = numHeads / numKVHeads; // integer multiplier for kv sharing in multiquery
//        float sqrtHeadSize = (float) Math.sqrt(headSize);
//
//        // Copy the token embedding into x - this is done on the CPU
//        weights.token_embedding_table.copyTo(token * dim, state.x, 0, dim);
//
//        // Copy x into TornadoVM-compatible array for processing
//        MemorySegment.copy(state.x.asMemorySegment(), 0, state.wrapX.getSegmentWithHeader(), 0, dim * 4);
//
//        TornadoExecutionPlan executionPlan = executionPlanTuple.getFirst();
//        GridScheduler gridScheduler = executionPlanTuple.getSecond();
//
//        try {
//            // Process each layer
//            for (int l = 0; l < config.numberOfLayers; l++) {
//                state.layer = l;
//                state.position = position;
//
//                // Step 1: RMSNorm for attention
//                executionPlan.withGraph(0).withGridScheduler(gridScheduler).execute();
//
//                // Step 2: QKV Matmuls
//                executionPlan.withGraph(1).withGridScheduler(gridScheduler).execute();
//
//                // Step 3: RoPE rotation
//                executionPlan.withGraph(2).withGridScheduler(gridScheduler).execute();
//
//                // Map key and value from rotation output to KV cache using device pointers
//                // Instead of copying on CPU, we map memory regions directly on device
//                executionPlan.mapOnDeviceMemoryRegion(
//                        state.keyCache[l], state.k, 0, 2, 3);
//                executionPlan.mapOnDeviceMemoryRegion(
//                        state.valueCache[l], state.v, 0, 2, 3);
//
//                // Step 4: Multi-head Attention (scores, softmax, weighted sum)
//                executionPlan.withGraph(3).withGridScheduler(gridScheduler).execute();
//
//                // Step 5: Feed-forward neural network
//                executionPlan.withGraph(4).withGridScheduler(gridScheduler).execute();
//            }
//
//            // Final RMSNorm
//            executionPlan.withGraph(5).withGridScheduler(gridScheduler).execute();
//
//            // Final projection to logits
//            executionPlan.withGraph(6).withGridScheduler(gridScheduler).execute();
//
//            // Copy results from TornadoVM buffers to state.logits
//            state.logits.asMemorySegment().copyFrom(state.wrapLogits.getSegment());
//        } catch (Exception e) {
//            System.err.println("Error during TornadoVM execution: " + e.getMessage());
//            e.printStackTrace();
//        }
//
//        return state.logits;
//    }
//
//    /**
//     * Set up and initialize all TornadoVM execution plans for LLM inference.
//     * This method creates all task graphs, configures worker grids, and returns
//     * a tuple containing the execution plan and grid scheduler that can be used in the forward method.
//     */
//    public static Tuple2<TornadoExecutionPlan, GridScheduler> setupTornadoExecutionPlans(
//            Configuration config, Weights weights, State state) {
//
//        int dim = config.dim;
//        int headSize = config.headSize;
//        int numHeads = config.numberOfHeads;
//        int numKVHeads = config.numberOfKeyValueHeads;
//        int kvDim = (dim * numKVHeads) / numHeads;
//        int kvMul = numHeads / numKVHeads;
//
//        // Define worker grid sizes
//        int localSizeRMS = 256;
//        int localSizeHeads = 64;
//        int localSizeFFN = 256;
//
//        // Create kernel context
//        KernelContext context = new KernelContext();
//
//        // --- Worker Grids ---
//        WorkerGrid dimWorker = new WorkerGrid1D(dim);
//        dimWorker.setGlobalWork(dim, 1, 1);
//        dimWorker.setLocalWork(localSizeRMS, 1, 1);
//
//        WorkerGrid headsWorker = new WorkerGrid1D(numHeads * localSizeHeads);
//        headsWorker.setGlobalWork(numHeads * localSizeHeads, 1, 1);
//        headsWorker.setLocalWork(localSizeHeads, 1, 1);
//
//        WorkerGrid singleWorker = new WorkerGrid1D(1);
//        singleWorker.setGlobalWork(1, 1, 1);
//        singleWorker.setLocalWork(1, 1, 1);
//
//        WorkerGrid hiddenDimWorker = new WorkerGrid1D(config.hiddenDim);
//        hiddenDimWorker.setGlobalWork(config.hiddenDim, 1, 1);
//        hiddenDimWorker.setLocalWork(localSizeFFN, 1, 1);
//
//        WorkerGrid vocabWorker = new WorkerGrid1D(config.vocabularySize);
//        vocabWorker.setGlobalWork(config.vocabularySize, 1, 1);
//        vocabWorker.setLocalWork(256, 1, 1);
//
//        WorkerGrid ropeWorker = new WorkerGrid1D(dim / 2);
//        ropeWorker.setGlobalWork(dim / 2, 1, 1);
//        ropeWorker.setLocalWork(localSizeRMS / 2, 1, 1);
//
//        // --- Configure Grid Scheduler ---
//        GridScheduler gridScheduler = new GridScheduler();
//
//        // --- Create Task Graphs ---
//
//        // Task Graph 0: RMSNorm
//        TaskGraph rmsNormGraph = new TaskGraph("rms-norm")
//                .transferToDevice(DataTransferMode.FIRST_EXECUTION, state.x)
//                .task("reduce", TestRMSNormLayer::reduceSquareSums, context, state.wrapX, state.reduce)
//                .task("sum", TestRMSNormLayer::finalSum, context, state.reduce, dim, config.rmsNormEps)
//                .task("normalize", TestRMSNormLayer::normalizeAndScale, context,
//                        state.wrapXb, state.wrapX, weights.rms_att_weightFlat, state.reduce, dim, config.rmsNormEps)
//                .transferToHost(DataTransferMode.UNDER_DEMAND, state.xb);
//
//        gridScheduler.setWorkerGrid("rms-norm.reduce", dimWorker);
//        gridScheduler.setWorkerGrid("rms-norm.sum", singleWorker);
//        gridScheduler.setWorkerGrid("rms-norm.normalize", dimWorker);
//
//        // Task Graph 1: QKV Matmuls
//        TaskGraph qkvGraph = new TaskGraph("qkv")
//                .transferToDevice(DataTransferMode.UNDER_DEMAND, state.xb)
//                .task("q-matmul", TestFusedLayer::matrixVectorSimple, context, state.wrapXb, state.wrapQ,
//                        weights.wqFlat, dim, dim)
//                .task("k-matmul", TestFusedLayer::matrixVectorSimple, context, state.wrapXb, state.wrapK,
//                        weights.wkFlat, dim, dim)
//                .task("v-matmul", TestFusedLayer::matrixVectorSimple, context, state.wrapXb, state.wrapV,
//                        weights.wvFlat, dim, dim)
//                .transferToHost(DataTransferMode.UNDER_DEMAND, state.q, state.k, state.v);
//
//        gridScheduler.setWorkerGrid("qkv.q-matmul", dimWorker);
//        gridScheduler.setWorkerGrid("qkv.k-matmul", dimWorker);
//        gridScheduler.setWorkerGrid("qkv.v-matmul", dimWorker);
//
//        // Task Graph 2: RoPE
//        TaskGraph ropeGraph = new TaskGraph("rope")
//                .transferToDevice(DataTransferMode.UNDER_DEMAND, state.q, state.k)
//                .task("rope", TestFusedLayer::ropeRotation, context, state.position, state.wrapQ,
//                        state.wrapK, kvDim, headSize)
//                .transferToHost(DataTransferMode.UNDER_DEMAND, state.q, state.k);
//
//        gridScheduler.setWorkerGrid("rope.rope", ropeWorker);
//
//        // Task Graph 3: Multi-head Attention
//        // Important: The KV cache arrays are mapped to this graph from Graph 2 using device pointers
//        TaskGraph attentionGraph = new TaskGraph("attention")
//                // Attention memory is allocated on-device
//                .transferToDevice(DataTransferMode.FIRST_EXECUTION, state.att, state.maxValues,
//                        state.expValues, state.sumValues)
//                // KV cache arrays are mapped from previous graph (see mapOnDeviceMemoryRegion in forward method)
//                .transferToDevice(DataTransferMode.UNDER_DEMAND, state.q)
//
//                // Step 1: Calculate attention scores
//                .task("scores", TestMultiHeadAttention::calculateAttentionScores, context,
//                        state.position, config.contextLength, state.wrapQ, state.wrapKeyCache,
//                        state.wrapAtt, kvDim, kvMul, headSize, 0)
//                // Step 2: Find max for numerical stability
//                .task("max", TestMultiHeadAttention::findMaxAttentionScores, context,
//                        state.position, config.contextLength, state.wrapAtt, state.maxValues, localSizeHeads)
//                // Step 3: Calculate exp and sum
//                .task("expsum", TestMultiHeadAttention::calculateExpAndSum, context,
//                        state.position, config.contextLength, state.wrapAtt, state.maxValues,
//                        state.expValues, state.sumValues, localSizeHeads)
//                // Step 4: Normalize with softmax
//                .task("normalize", TestMultiHeadAttention::normalizeSoftmax, context,
//                        state.position, config.contextLength, state.expValues,
//                        state.sumValues, state.wrapAtt)
//                // Step 5: Compute weighted sum
//                .task("weighted-sum", TestMultiHeadAttention::computeWeightedSum, context,
//                        state.position, config.contextLength, state.wrapAtt, state.wrapValueCache,
//                        state.wrapXb, kvDim, kvMul, headSize, 0)
//                .transferToHost(DataTransferMode.UNDER_DEMAND, state.xb);
//
//        gridScheduler.setWorkerGrid("attention.scores", headsWorker);
//        gridScheduler.setWorkerGrid("attention.max", headsWorker);
//        gridScheduler.setWorkerGrid("attention.expsum", headsWorker);
//        gridScheduler.setWorkerGrid("attention.normalize", headsWorker);
//        gridScheduler.setWorkerGrid("attention.weighted-sum", headsWorker);
//
//        // Task Graph 4: FFN
//        TaskGraph ffnGraph = new TaskGraph("ffn")
//                // Input arrays are transferred on-demand (results from previous graph)
//                .transferToDevice(DataTransferMode.UNDER_DEMAND, state.xb)
//                // Static arrays are transferred once
//                .transferToDevice(DataTransferMode.FIRST_EXECUTION, state.hb, state.hb2, state.reduce)
//
//                // Step 1: Matrix multiplication with attention output and residual
//                .task("matmul1", TestParallelFFNLayer::matrixVectorMultiply,
//                        context, state.xb, state.x, weights.woFlat, dim, dim)
//                .task("residual1", TestParallelFFNLayer::addInPlace, context, state.x, state.xb)
//
//                // Step 2: RMSNorm sequence
//                .task("reduce", TestParallelFFNLayer::reduceSquareSums, context, state.xb, state.reduce, localSizeFFN)
//                .task("sum", TestParallelFFNLayer::finalSum, context, state.reduce, dim, config.rmsNormEps)
//                .task("ns", TestParallelFFNLayer::normalizeAndScale2,
//                        context, state.x, state.xb, weights.rms_ffn_weightFlat, state.reduce, dim)
//
//                // Step 3: Parallel projections with W1 and W3
//                .task("projection1", TestParallelFFNLayer::matrixVectorMultiply,
//                        context, state.x, state.hb, weights.w1Flat, dim, config.hiddenDim)
//                .task("projection3", TestParallelFFNLayer::matrixVectorMultiply,
//                        context, state.x, state.hb2, weights.w3Flat, dim, config.hiddenDim)
//
//                // Step 4: SiLU activation and element-wise multiplication
//                .task("silu", TestParallelFFNLayer::siluActivation, context, state.hb)
//                .task("multiply", TestParallelFFNLayer::elementMultiply, context, state.hb2, state.hb)
//
//                // Step 5: Final projection and residual
//                .task("projection2", TestParallelFFNLayer::matrixVectorMultiply,
//                        context, state.hb, state.x, weights.w2Flat, config.hiddenDim, dim)
//                .task("residual2", TestParallelFFNLayer::addInPlace, context, state.x, state.xb)
//
//                // Transfer result to host on-demand (will remain on device for next layer)
//                .transferToHost(DataTransferMode.UNDER_DEMAND, state.xb);
//
//        // Set FFN worker grids
//        gridScheduler.setWorkerGrid("ffn.matmul1", dimWorker);
//        gridScheduler.setWorkerGrid("ffn.residual1", dimWorker);
//        gridScheduler.setWorkerGrid("ffn.reduce", dimWorker);
//        gridScheduler.setWorkerGrid("ffn.sum", singleWorker);
//        gridScheduler.setWorkerGrid("ffn.ns", dimWorker);
//        gridScheduler.setWorkerGrid("ffn.projection1", hiddenDimWorker);
//        gridScheduler.setWorkerGrid("ffn.projection3", hiddenDimWorker);
//        gridScheduler.setWorkerGrid("ffn.silu", hiddenDimWorker);
//        gridScheduler.setWorkerGrid("ffn.multiply", hiddenDimWorker);
//        gridScheduler.setWorkerGrid("ffn.projection2", dimWorker);
//        gridScheduler.setWorkerGrid("ffn.residual2", dimWorker);
//
//        // Task Graph 5: Final RMSNorm
//        TaskGraph finalRmsNormGraph = new TaskGraph("final-rms")
//                .transferToDevice(DataTransferMode.UNDER_DEMAND, state.x)
//                .task("reduce", TestRMSNormLayer::reduceSquareSums, context, state.wrapX, state.reduce, 256)
//                .task("sum", TestRMSNormLayer::finalSum, context, state.reduce, dim, config.rmsNormEps)
//                .task("normalize", TestRMSNormLayer::normalizeAndScale, context,
//                        state.wrapXFloat, state.wrapX, weights.rms_final_weight_as_floatArray, state.reduce, dim, config.rmsNormEps)
//                .transferToHost(DataTransferMode.UNDER_DEMAND, state.x);
//
//        gridScheduler.setWorkerGrid("final-rms.reduce", dimWorker);
//        gridScheduler.setWorkerGrid("final-rms.sum", singleWorker);
//        gridScheduler.setWorkerGrid("final-rms.normalize", dimWorker);
//
//        // Task Graph 6: Final Projection to Logits
//        TaskGraph logitsGraph = new TaskGraph("logits")
//                .transferToDevice(DataTransferMode.UNDER_DEMAND, state.x)
//                .task("projection", TestMatMulWithByteArrays::matmulTornado, context,
//                        weights.wclsByteArray, state.wrapXFloat, state.wrapLogits, dim)
//                .transferToHost(DataTransferMode.EVERY_EXECUTION, state.logits);
//
//        gridScheduler.setWorkerGrid("logits.projection", vocabWorker);
//
//        // Create immutable task graphs
//        ImmutableTaskGraph immutableRMSGraph = rmsNormGraph.snapshot();
//        ImmutableTaskGraph immutableQKVGraph = qkvGraph.snapshot();
//        ImmutableTaskGraph immutableRopeGraph = ropeGraph.snapshot();
//        ImmutableTaskGraph immutableAttentionGraph = attentionGraph.snapshot();
//        ImmutableTaskGraph immutableFFNGraph = ffnGraph.snapshot();
//        ImmutableTaskGraph immutableFinalRMSGraph = finalRmsNormGraph.snapshot();
//        ImmutableTaskGraph immutableLogitsGraph = logitsGraph.snapshot();
//
//        // Create execution plan with all graphs
//        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(
//                immutableRMSGraph,         // Graph 0: RMSNorm
//                immutableQKVGraph,         // Graph 1: QKV Matmuls
//                immutableRopeGraph,        // Graph 2: RoPE
//                immutableAttentionGraph,   // Graph 3: Multi-head Attention
//                immutableFFNGraph,         // Graph 4: FFN
//                immutableFinalRMSGraph,    // Graph 5: Final RMSNorm
//                immutableLogitsGraph       // Graph 6: Final projection to logits
//        );
//
//        // Return the execution plan and grid scheduler as a tuple
//        return new Tuple2<>(executionPlan, gridScheduler);
//    }
//}
