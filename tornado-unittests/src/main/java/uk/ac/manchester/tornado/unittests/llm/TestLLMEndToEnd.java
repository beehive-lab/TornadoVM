package uk.ac.manchester.tornado.unittests.llm;

import org.junit.Test;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class TestLLMEndToEnd {

    private State tornadoState;
    private Configuration config;
    private Weights weights;

    private State sequentialState;

    // Intermediate arrays for validation
    private FloatArray intermediateReduceFirst;
    private FloatArray intermediateReduceTwo;
    private FloatArray intermediateReduceThree;
    private FloatArray maxValues;
    private FloatArray expValues;
    private FloatArray sumValues;

    // For detailed debugging
    private boolean captureIntermediateStates = true;
    private FloatArray xCopy;
    private FloatArray xbCopy;
    private FloatArray qCopy;
    private FloatArray kCopy;
    private FloatArray vCopy;
    private FloatArray attCopy;
    private FloatArray hbCopy;
    private FloatArray hb2Copy;

    // TG 1
    public static void reduceSquareSums(KernelContext context, FloatArray a, FloatArray reduce, int localWorkGroupSize) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID

        float[] localA = context.allocateFloatLocalArray(localWorkGroupSize);
        localA[localIdx] = a.get(globalIdx) * a.get(globalIdx);

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localA[localIdx] += localA[localIdx + stride];
            }
        }
        if (localIdx == 0) {
            reduce.set(groupID, localA[0]);
        }
    }

    public static void finalSum(FloatArray reduce, int size, float eps) {

        float sum = 0.0f;

        for (int i = 0; i < reduce.getSize(); i++) {
            sum += reduce.get(i);
        }

        float ss = sum / (float) size;  // Keep dividing by the original size
        ss += eps;
        ss = 1.0f / TornadoMath.sqrt(ss);
        reduce.set(0, ss);
    }

    public static void normalizeAndScale(KernelContext context, FloatArray out, FloatArray input, FloatArray weight, FloatArray scalingFactorBuffer, int size, IntArray positionNlayer) {

        int globalIdx = context.globalIdx;

        int layerOffset = positionNlayer.get(1) * size;

        float scaledValue = weight.get(layerOffset + globalIdx) * (scalingFactorBuffer.get(0) * input.get(globalIdx));
        out.set(globalIdx, scaledValue);
    }

    public static void forcePropagationThreeArrays(FloatArray x, FloatArray y, IntArray z) {
        x.set(0, x.get(0));
        y.set(0, y.get(0));
        z.set(0, z.get(0));
    }
    // ---
    // TG 2
    public static void matrixVectorSimple(KernelContext context, FloatArray x, FloatArray output, FloatArray weights, int n, int d, IntArray posAndLayer) {
        int idx = context.globalIdx;

        if (idx < output.getSize()) {  // Ensure we don't go out of bounds
            int layer = posAndLayer.get(1);
            // Base offset for the current layer: layer * d * n
            // Each layer has a full d×n matrix
            int layerOffset = layer * d * n;

            float sum = 0.0f;
            for (int j = 0; j < n; j++) {
                // For each output idx, we need to do a dot product of the row idx with vector x
                // The weights are stored in row-major format
                sum += weights.get(layerOffset + idx * n + j) * x.get(j);
            }
            output.set(idx, sum);
        }
    }

    public static void forcePropagationFiveArrays(FloatArray x, FloatArray y, FloatArray z, FloatArray w, FloatArray cv) {
        x.set(0, x.get(0));
        y.set(0, y.get(0));
        z.set(0, z.get(0));
        w.set(0, w.get(0));
        cv.set(0, cv.get(0));
    }
    // --
    // TG 3
    public static void ropeRotation(KernelContext context, IntArray positionNlayer, FloatArray sq, FloatArray sk, int kv_dim, int head_size) {
        int i = context.globalIdx * 2;

        // Ensure we're within bounds and handle the even indices properly
        if (i < sq.getSize() && i % 2 == 0) {
            int head_dim = i % head_size;
            float freq = 1.0f / TornadoMath.pow(10000.0f, head_dim / (float) head_size);
            float val = positionNlayer.get(0) * freq;
            float fcr = TornadoMath.cos(val);
            float fci = TornadoMath.sin(val);

            int rotn = i < kv_dim ? 2 : 1; // how many vectors? 2 = q & k, 1 = q only

            // Rotate query vector
            float v0q = sq.get(i);
            float v1q = sq.get(i + 1);
            sq.set(i, v0q * fcr - v1q * fci);
            sq.set(i + 1, v0q * fci + v1q * fcr);

            // Rotate key vector if needed
            if (rotn > 1 && i < sk.getSize()) {
                float v0k = sk.get(i);
                float v1k = sk.get(i + 1);
                sk.set(i, v0k * fcr - v1k * fci);
                sk.set(i + 1, v0k * fci + v1k * fcr);
            }
        }
    }
    // --
    // TG 4
    public static void copyToCache(FloatArray dest, FloatArray src, IntArray positioNlayer) {
        int destOffset = positioNlayer.get(2);
        for (@Parallel int i = 0; i < src.getSize(); i++) {
            dest.set(destOffset + i, src.get(i));
        }

    }

    public static void forcePropagationSixArrays(FloatArray x, FloatArray y, FloatArray z, FloatArray w, FloatArray cv, FloatArray xyz) {
        x.set(0, x.get(0));
        y.set(0, y.get(0));
        z.set(0, z.get(0));
        w.set(0, w.get(0));
        cv.set(0, cv.get(0));
        xyz.set(0, xyz.get(0));
    }
    // --
    // TG 5
    public static void calculateAttentionScores(KernelContext context, IntArray positionNlayer, int seqLen, FloatArray query, FloatArray keyCache, FloatArray attScores, int kvDim, int kvMul,
                                                int headSize, int loff, int localWorkgourpSize) {
        int h = context.groupIdx;         // Head index
        int threadId = context.localIdx;  // Thread ID within work group
        int blockDim = context.localGroupSizeX;  // Work group size

        // Get the query vector offset for this head
        int queryOffset = h * headSize;

        // Attention scores offset for this head
        int attOffset = h * seqLen;
        int position = positionNlayer.get(0) + 1;

        for (int t = threadId; t < position; t += blockDim) {
            // Get the key vector for this head and at this timestep
            int keyOffset = loff + t * kvDim + (h / kvMul) * headSize;

            // Calculate the attention score as the dot product of query and key
            float score = 0.0f;
            for (int i = 0; i < headSize; i++) {
                score += query.get(queryOffset + i) * keyCache.get(keyOffset + i);
            }

            // Scale by sqrt(head_size)
            score /= TornadoMath.sqrt(headSize);

            // Save the score to the attention buffer
            attScores.set(attOffset + t, score);
        }
    }

    public static void findMaxAttentionScoress(KernelContext context, IntArray positionNlayer, int seqLen, FloatArray attScores, FloatArray maxValues, int workGroupSize) {
        int h = context.groupIdx;         // Head index
        int threadId = context.localIdx;  // Thread ID within work group
        int blockDim = context.localGroupSizeX;  // Work group size

        // Attention scores offset for this head
        int attOffset = h * seqLen;

        // Find the maximum value for numerical stability
        float maxVal = Float.NEGATIVE_INFINITY;
        int position = positionNlayer.get(0) + 1;

        for (int t = threadId; t < position; t += blockDim) {
            maxVal = Math.max(maxVal, attScores.get(attOffset + t));
        }

        // Parallel reduction to find global maximum
        float[] maxReduction = context.allocateFloatLocalArray(workGroupSize); //TODO: ISSUES
        maxReduction[threadId] = maxVal;

        for (int stride = blockDim / 2; stride > 0; stride /= 2) {
            context.localBarrier();
            if (threadId < stride) {
                maxReduction[threadId] = Math.max(maxReduction[threadId], maxReduction[threadId + stride]);
            }
        }

        // Thread 0 in each work group writes the max value
        if (threadId == 0) {
            maxValues.set(h, maxReduction[0]);
        }
    }

    // TG 6

    // --

    public static void calculateExpAndSum(KernelContext context, IntArray positionNlayer, int seqLen, FloatArray attScores, FloatArray maxValues, FloatArray expValues, FloatArray sumValues,
                                          int localWorkGroupSize) {
        int h = context.groupIdx;         // Head index
        int threadId = context.localIdx;  // Thread ID within work group
        int blockDim = context.localGroupSizeX;  // Work group size

        // Get max value for this head
        float maxVal = maxValues.get(h);

        // Attention scores and exp values offset for this head
        int attOffset = h * seqLen;
        int expOffset = h * seqLen;
        int position = positionNlayer.get(0) + 1;

        // Compute exp(score - max) and thread-local sum
        float expSum = 0.0f;
        for (int t = threadId; t < position; t += blockDim) {
            float score = attScores.get(attOffset + t);
            float expValue = (float) Math.exp(score - maxVal);
            expValues.set(expOffset + t, expValue);
            expSum += expValue;
        }

        // Ensure all exp values are computed before summing
        context.localBarrier();

        // Parallel reduction to get the total sum
        float[] sumReduction = context.allocateFloatLocalArray(localWorkGroupSize);
        sumReduction[threadId] = expSum;

        for (int stride = blockDim / 2; stride > 0; stride /= 2) {
            context.localBarrier();
            if (threadId < stride) {
                sumReduction[threadId] += sumReduction[threadId + stride];
            }
        }

        // Thread 0 in each work group writes the sum
        if (threadId == 0) {
            sumValues.set(h, sumReduction[0]);
        }

        // Ensure sum value is written before proceeding
        context.localBarrier();
    }

    /**
     * Normalize exponential values to get softmax probabilities
     */
    public static void normalizeSoftmax(KernelContext context, IntArray positionNlayer, int seqLen, FloatArray expValues, FloatArray sumValues, FloatArray attScores) {
        int h = context.groupIdx;         // Head index
        int threadId = context.localIdx;  // Thread ID within work group
        int blockDim = context.localGroupSizeX;  // Work group size

        // Get sum value for this head
        float sum = sumValues.get(h);

        // Exp values and attention scores offset for this head
        int expOffset = h * seqLen;
        int attOffset = h * seqLen;
        int position = positionNlayer.get(0) + 1;

        // Normalize values and write back to attention scores
        for (int t = threadId; t < position; t += blockDim) {
            float normalizedValue = expValues.get(expOffset + t) / sum;
            attScores.set(attOffset + t, normalizedValue);
        }
    }

    public static void computeWeightedSum(KernelContext context, IntArray positionNlayer, int seqLen, FloatArray attScores, FloatArray valueCache, FloatArray output, int kvDim, int kvMul,
                                          int headSize, int loff) {
        int h = context.groupIdx;         // Head index
        int threadId = context.localIdx;  // Thread ID within work group
        int blockDim = context.localGroupSizeX;  // Work group size

        // Attention scores offset for this head
        int attOffset = h * seqLen;

        // Output offset for this head
        int outputOffset = h * headSize;
        int position = positionNlayer.get(0) + 1;

        // Calculate weighted sum for each head dimension
        for (int i = threadId; i < headSize; i += blockDim) {
            float val = 0.0f;
            for (int t = 0; t < position; t++) {
                // Get the value vector for this head and timestep
                int valueOffset = loff + t * kvDim + (h / kvMul) * headSize;

                // Get the attention weight for this timestep
                float a = attScores.get(attOffset + t);

                val += a * valueCache.get(valueOffset + i);
            }
            output.set(outputOffset + i, val);
        }

        // Make sure all threads finish writing their outputs
        context.localBarrier();
    }
    // --
    public static void matrixVectorMultiply(KernelContext context, FloatArray x, FloatArray output, FloatArray weights, int n, int d, IntArray positionNlayer) {
        int idx = context.globalIdx;

        // Calculate the layer offset correctly
        int layer = positionNlayer.get(1);
        int layerOffset = layer * d * n;  // The correct formula

        float sum = 0.0f;
        for (int j = 0; j < n; j++) {
            if (j < x.getSize() && (layerOffset + idx * n + j) < weights.getSize()) {
                // Use the correct index calculation
                sum += weights.get(layerOffset + idx * n + j) * x.get(j);
            }
        }

        output.set(idx, sum);
    }

    public static void addInPlace(KernelContext context, FloatArray input, FloatArray output) {
        int idx = context.globalIdx;

        if (idx < Math.min(input.getSize(), output.getSize())) {
            output.set(idx, output.get(idx) + input.get(idx));
        }
    }

    public static void siluActivation(KernelContext context, FloatArray input) {
        int idx = context.globalIdx;

        if (idx < input.getSize()) {
            float value = input.get(idx);
            float result = value / (1.0f + TornadoMath.exp(-value));
            input.set(idx, result);
        }
    }

    public static void elementMultiply(KernelContext context, FloatArray input, FloatArray output) {
        int idx = context.globalIdx;

        if (idx < Math.min(input.getSize(), output.getSize())) {
            output.set(idx, output.get(idx) * input.get(idx));
        }
    }

    public static void forcePropagationTwoArrays(FloatArray x, IntArray y) {
        x.set(0, x.get(0));
        y.set(0, y.get(0));
    }

    public static void normalizeAndScaleInNout(KernelContext context, FloatArray inputNoUT, FloatArray weight, FloatArray scalingFactorBuffer, int size, IntArray positionNlayer) {
        int globalIdx = context.globalIdx;

        int layerOffset = positionNlayer.get(1) * size;

        float scaledValue = weight.get(layerOffset + globalIdx) * (scalingFactorBuffer.get(0) * inputNoUT.get(globalIdx));
        inputNoUT.set(globalIdx, scaledValue);
    }

    public static void matmulTornadoQ8(KernelContext context, ByteArray thisx, FloatArray that, FloatArray out, int dim1) {
        final int BLOCK_SIZE = 32; // Assuming this is the block size used in quantization
        final int BYTES_PER_BLOCK = Float16.BYTES + BLOCK_SIZE; // 2 bytes for scale + block_size bytes for values

        int idx = context.globalIdx;

        float result = 0f;
        int thisOffset = idx * dim1;

        for (int j = 0; j < dim1; j++) {
            int index = thisOffset + j;
            // Calculate block position
            int blockIndex = index / BLOCK_SIZE;
            int withinBlockIndex = index % BLOCK_SIZE;
            int blockOffset = blockIndex * BYTES_PER_BLOCK;

            // Read scale (float16) for this block
            int scaleByte1 = thisx.get(blockOffset) & 0xFF;
            int scaleByte2 = thisx.get(blockOffset + 1) & 0xFF;
            short scaleFloat16 = (short) ((scaleByte2 << 8) | scaleByte1);
            float scale = decodeFloat16(scaleFloat16);

            // Read quantized value
            byte quantized = thisx.get(blockOffset + 2 + withinBlockIndex);

            // Dequantize and multiply
            result += (quantized * scale) * that.get(j);
        }

        out.set(idx, result);

    }

    private static float decodeFloat16(short value) {
        int sign = (value & 0x8000) >>> 15;
        int exp = (value & 0x7C00) >>> 10;
        int frac = value & 0x03FF;

        // Handle special cases
        if (exp == 0x1F) {
            return sign == 0 ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
        }
        if (exp == 0) {
            if (frac == 0) {
                return sign == 0 ? 0.0f : -0.0f;
            }
            float result = frac * pow2(-24);
            return sign == 0 ? result : -result;
        }

        float result = 1.0f + (frac / 1024.0f);
        result *= pow2(exp - 15);
        return sign == 0 ? result : -result;
    }

    private static float pow2(int n) {
        if (n >= 0) {
            if (n < 31) {
                return (float) (1 << n);
            }
            return Float.POSITIVE_INFINITY;
        }
        if (n > -150) {
            return 1.0f / (1 << -n);
        }
        return 0.0f;
    }

    public FloatArray rnsnorm(FloatArray x, FloatArray weight, int size, float rmsNormEps) {
        FloatArray out = new FloatArray(size);
        // Step 1: Calculate sum of squares
        float ss = 0.0f;
        for (int i = 0; i < size; i++) {
            ss += x.get(i) * x.get(i);  // Sum of squares
        }

        ss /= size;  // Normalize by the size
        ss += rmsNormEps; // Add epsilon
        ss = (float) (1.0 / Math.sqrt(ss)); // Inverse square root

        // Step 2: Normalize and scale
        for (int i = 0; i < size; i++) {
            float normalizedValue = ss * x.get(i);
            out.set(i, weight.get(i) * normalizedValue);
        }
        return out;
    }

    @Test
    public void testTinyLLM() throws TornadoExecutionPlanException {
        // Create a very small LLM configuration for quick testing
        config = new Configuration(
                32,        // dim
                64,        // hiddenDim
                1,         // numberOfLayers
                4,         // numberOfHeads
                2,         // numberOfKeyValueHeads
                256,       // vocabularySize
                32,        // contextLength
                1e-5f,     // rmsNormEps
                10000.0f   // ropeTheta
        );

        // Setup the test infrastructure
        setupTestInfrastructure();

        // Create the task graphs and execution plan
        List<ImmutableTaskGraph> taskGraphs = createTaskGraphs();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraphs.toArray(new ImmutableTaskGraph[taskGraphs.size()]));

        // Test a simple prompt
        int[] promptTokens = {42, 99, 123};
        System.out.println("====== Testing with prompt: " + Arrays.toString(promptTokens) + " ======");
        runGenerationTest(executionPlan, promptTokens, 5);
    }

//    @Test
//    public void testMediumLLM() throws TornadoExecutionPlanException {
//        // Create a medium-sized LLM configuration
//        config = new Configuration(
//                128,       // dim
//                256,       // hiddenDim
//                4,         // numberOfLayers
//                8,         // numberOfHeads
//                4,         // numberOfKeyValueHeads
//                256,       // vocabularySize
//                64,        // contextLength
//                1e-5f,     // rmsNormEps
//                10000.0f   // ropeTheta
//        );
//
//        // Setup the test infrastructure
//        setupTestInfrastructure();
//
//        // Create the task graphs and execution plan
//        List<ImmutableTaskGraph> taskGraphs = createTaskGraphs();
//        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraphs.toArray(new ImmutableTaskGraph[0]));
//
//        // Test a simple prompt
//        int[] promptTokens = {10, 20, 30, 40, 50};
//        System.out.println("====== Testing with prompt: " + Arrays.toString(promptTokens) + " ======");
//        runGenerationTest(executionPlan, promptTokens, 10);
//    }

    private void setupTestInfrastructure() {
        // Initialize weights with dummy data
        initializeWeights();

        // Initialize states
        tornadoState = new State(config);
        sequentialState = new State(config);

        // Initialize intermediate arrays
        int reduceArraySize = calculateReduceArraySize();
        intermediateReduceFirst = new FloatArray(reduceArraySize);
        intermediateReduceTwo = new FloatArray(reduceArraySize);
        intermediateReduceThree = new FloatArray(reduceArraySize);

        maxValues = new FloatArray(config.numberOfHeads);
        expValues = new FloatArray(config.numberOfHeads * config.contextLength);
        sumValues = new FloatArray(config.numberOfHeads);

        // Allocate arrays for intermediate state capture
        if (captureIntermediateStates) {
            xCopy = new FloatArray(config.dim);
            xbCopy = new FloatArray(config.dim);
            qCopy = new FloatArray(config.dim);
            kCopy = new FloatArray(config.dim);
            vCopy = new FloatArray(config.dim);
            attCopy = new FloatArray(config.numberOfHeads * config.contextLength);
            hbCopy = new FloatArray(config.hiddenDim);
            hb2Copy = new FloatArray(config.hiddenDim);
        }

        System.out.println("Model configuration:");
        System.out.println("  Dimension: " + config.dim);
        System.out.println("  Hidden dimension: " + config.hiddenDim);
        System.out.println("  Layers: " + config.numberOfLayers);
        System.out.println("  Attention heads: " + config.numberOfHeads);
        System.out.println("  KV heads: " + config.numberOfKeyValueHeads);
        System.out.println("  Context length: " + config.contextLength);
        System.out.println("  Vocabulary size: " + config.vocabularySize);
    }

    private int calculateReduceArraySize() {
        int localSizeRMS = 256;
        if (config.dim % localSizeRMS != 0) {
            for (int i = localSizeRMS; i > 0; i--) {
                if (config.dim % i == 0) {
                    localSizeRMS = i;
                    break;
                }
            }
        }
        return config.dim / localSizeRMS;
    }

    private void runGenerationTest(TornadoExecutionPlan executionPlan, int[] promptTokens, int numGenerationSteps) {
        GridScheduler scheduler = setupGridSchedulers();

        // Process the prompt
        processSequence(executionPlan, scheduler, promptTokens);

        // Validate the KV cache after processing the prompt
        validateKVCache();

        // Generate additional tokens
        List<Integer> generatedTokens = new ArrayList<>();
        for (int i = 0; i < numGenerationSteps; i++) {
            // Get the highest probability token from logits
            int nextToken = tornadoState.logits.argmax();
            generatedTokens.add(nextToken);

            System.out.println("Generated token " + (i+1) + ": " + nextToken);

            int[] nextTokenArray = {nextToken};
            processSequence(executionPlan, scheduler, nextTokenArray);

            // Validate after each generation step
            validateKVCache();
        }

        System.out.println("Generated sequence: " + generatedTokens);

        // Final validation
        System.out.println("=== Final Output Validation ===");
        NumericalValidator.ValidationResult logitsResult =
                NumericalValidator.compareArrays(tornadoState.wrapLogits, sequentialState.wrapLogits, "Final Logits");
        System.out.println(logitsResult);

        // Distribution analysis
        System.out.println("=== Distribution Analysis ===");
        NumericalValidator.DistributionStats tornadoLogitsStats =
                NumericalValidator.analyzeDistribution(tornadoState.wrapLogits);
        System.out.println("Tornado Logits: " + tornadoLogitsStats);

        NumericalValidator.DistributionStats seqLogitsStats =
                NumericalValidator.analyzeDistribution(sequentialState.wrapLogits);
        System.out.println("Sequential Logits: " + seqLogitsStats);
    }

    private void processSequence(TornadoExecutionPlan executionPlan, GridScheduler scheduler, int[] tokens) {
        for (int t = 0; t < tokens.length; t++) {
            int position = (int)tornadoState.positionAndLayer.get(0);
            position += 1;  // Increment position for next token

            // Set position for this token
            tornadoState.positionAndLayer.set(0, position);
            sequentialState.positionAndLayer.set(0, position);

            // Initialize with token embedding
            initializeWithToken(tokens[t]);

            // Run forward pass for both implementations
            runTornadoForwardPass(executionPlan, scheduler);
            runSequentialForwardPass();

            // Verify outputs
            System.out.println("=== Token position " + position + " verification ===");
            validateResults();

            // Update latestToken
            tornadoState.latestToken = tokens[t];
            sequentialState.latestToken = tokens[t];
        }
    }

    private void initializeWithToken(int token) {
        // Get the token embedding for this token
        for (int i = 0; i < config.dim; i++) {
            tornadoState.wrapX.set(i, weights.token_embedding_table.getFloat(token * config.dim + i));
            sequentialState.wrapX.set(i, weights.token_embedding_table.getFloat(token * config.dim + i));
        }
    }

    private void runTornadoForwardPass(TornadoExecutionPlan executionPlan, GridScheduler scheduler) {
        for (int l = 0; l < config.numberOfLayers; l++) {
            // Update layer index
            tornadoState.positionAndLayer.set(1, l);

            // Set KV cache offset
            int kvDim = (config.dim * config.numberOfKeyValueHeads) / config.numberOfHeads;
            int position = (int)tornadoState.positionAndLayer.get(0);
            int cacheOffset = (l * config.contextLength * kvDim) + (position * kvDim);
            tornadoState.positionAndLayer.set(2, cacheOffset);

            // Step 1: RMSNorm for attention
            executionPlan.withGraph(0).withGridScheduler(scheduler).execute();

            // Step 2: QKV Matmuls
            executionPlan.withGraph(1).withGridScheduler(scheduler).execute();

            // Step 3: RoPE rotation
            executionPlan.withGraph(2).withGridScheduler(scheduler).execute();

            // Update Cache value and key
            executionPlan.withGraph(3).execute();

            // Step 4: Multi-head Attention (scores, softmax, weighted sum)
            executionPlan.withGraph(4).withGridScheduler(scheduler).execute();

            // Step 5: Feed-forward neural network
            executionPlan.withGraph(5).withGridScheduler(scheduler).execute();

            // Optional: Capture intermediate states
            if (captureIntermediateStates) {
                captureIntermediateState();
            }
        }

        // Final RMSNorm
        executionPlan.withGraph(6).withGridScheduler(scheduler).execute();

        // Final projection to logits
        executionPlan.withGraph(7).withGridScheduler(scheduler).execute();
    }

    private void captureIntermediateState() {
        // Copy the current state for debugging
        for (int i = 0; i < config.dim; i++) {
            xCopy.set(i, tornadoState.wrapX.get(i));
            xbCopy.set(i, tornadoState.wrapXb.get(i));
            if (i < qCopy.getSize()) qCopy.set(i, tornadoState.wrapQ.get(i));
            if (i < kCopy.getSize()) kCopy.set(i, tornadoState.wrapK.get(i));
            if (i < vCopy.getSize()) vCopy.set(i, tornadoState.wrapV.get(i));
        }

        for (int i = 0; i < attCopy.getSize(); i++) {
            attCopy.set(i, tornadoState.wrapAtt.get(i));
        }

        for (int i = 0; i < config.hiddenDim; i++) {
            hbCopy.set(i, tornadoState.wrapHb.get(i));
            hb2Copy.set(i, tornadoState.wrapHb2.get(i));
        }
    }

    private void runSequentialForwardPass() {
        KernelContext context = new KernelContext();
        int localSizeRMS = 256;
        int localSizeHeads = 64;
        int localSizeFFN = 256;

        int kvDim = (config.dim * config.numberOfKeyValueHeads) / config.numberOfHeads;
        int kvMul = config.numberOfHeads / config.numberOfKeyValueHeads;
        int headSize = config.headSize;

        for (int l = 0; l < config.numberOfLayers; l++) {
            // Update layer index
            sequentialState.positionAndLayer.set(1, l);

            // Set KV cache offset
            int position = (int)sequentialState.positionAndLayer.get(0);
            int cacheOffset = (l * config.contextLength * kvDim) + (position * kvDim);
            sequentialState.positionAndLayer.set(2, cacheOffset);

            // Execute the sequential operations
            TestLLMEndToEnd.reduceSquareSums(context, sequentialState.wrapX, intermediateReduceFirst, localSizeRMS);
            TestLLMEndToEnd.finalSum(intermediateReduceFirst, config.dim, config.rmsNormEps);
            TestLLMEndToEnd.normalizeAndScale(context, sequentialState.wrapXb, sequentialState.wrapX, weights.rms_att_weightFlat, intermediateReduceFirst, config.dim, sequentialState.positionAndLayer);

            TestLLMEndToEnd.matrixVectorSimple(context, sequentialState.wrapXb, sequentialState.wrapQ, weights.wqFlat, config.dim, config.dim, sequentialState.positionAndLayer);
            TestLLMEndToEnd.matrixVectorSimple(context, sequentialState.wrapXb, sequentialState.wrapK, weights.wkFlat, config.dim, kvDim, sequentialState.positionAndLayer);
            TestLLMEndToEnd.matrixVectorSimple(context, sequentialState.wrapXb, sequentialState.wrapV, weights.wvFlat, config.dim, kvDim, sequentialState.positionAndLayer);

            TestLLMEndToEnd.ropeRotation(context, sequentialState.positionAndLayer, sequentialState.wrapQ, sequentialState.wrapK, kvDim, headSize);

            // Copy to KV caches
            TestLLMEndToEnd.copyToCache(sequentialState.wrapKeyCache, sequentialState.wrapK, sequentialState.positionAndLayer);
            TestLLMEndToEnd.copyToCache(sequentialState.wrapValueCache, sequentialState.wrapV, sequentialState.positionAndLayer);

            TestLLMEndToEnd.calculateAttentionScores(context, sequentialState.positionAndLayer, config.contextLength, sequentialState.wrapQ, sequentialState.wrapKeyCache, sequentialState.wrapAtt, kvDim, kvMul, headSize, 0, localSizeRMS);
            TestLLMEndToEnd.findMaxAttentionScoress(context, sequentialState.positionAndLayer, config.contextLength, sequentialState.wrapAtt, maxValues, localSizeHeads);
            TestLLMEndToEnd.calculateExpAndSum(context, sequentialState.positionAndLayer, config.contextLength, sequentialState.wrapAtt, maxValues, expValues, sumValues, localSizeHeads);
            TestLLMEndToEnd.normalizeSoftmax(context, sequentialState.positionAndLayer, config.contextLength, expValues, sumValues, sequentialState.wrapAtt);
            TestLLMEndToEnd.computeWeightedSum(context, sequentialState.positionAndLayer, config.contextLength, sequentialState.wrapAtt, sequentialState.wrapValueCache, sequentialState.wrapXb, kvDim, kvMul, headSize, 0);

            TestLLMEndToEnd.matrixVectorMultiply(context, sequentialState.wrapXb, sequentialState.wrapXb2, weights.woFlat, config.dim, config.dim, sequentialState.positionAndLayer);
            TestLLMEndToEnd.addInPlace(context, sequentialState.wrapX, sequentialState.wrapXb2);

            TestLLMEndToEnd.reduceSquareSums(context, sequentialState.wrapX, intermediateReduceTwo, localSizeFFN);
            TestLLMEndToEnd.finalSum(intermediateReduceTwo, config.dim, config.rmsNormEps);
            TestLLMEndToEnd.normalizeAndScale(context, sequentialState.wrapXb, sequentialState.wrapX, weights.rms_ffn_weightFlat, intermediateReduceTwo, config.dim, sequentialState.positionAndLayer);

            TestLLMEndToEnd.matrixVectorMultiply(context, sequentialState.wrapXb, sequentialState.wrapHb, weights.w1Flat, config.dim, config.hiddenDim, sequentialState.positionAndLayer);
            TestLLMEndToEnd.matrixVectorMultiply(context, sequentialState.wrapXb, sequentialState.wrapHb2, weights.w3Flat, config.dim, config.hiddenDim, sequentialState.positionAndLayer);
            TestLLMEndToEnd.siluActivation(context, sequentialState.wrapHb);
            TestLLMEndToEnd.elementMultiply(context, sequentialState.wrapHb2, sequentialState.wrapHb);
            TestLLMEndToEnd.matrixVectorMultiply(context, sequentialState.wrapHb, sequentialState.wrapXb, weights.w2Flat, config.hiddenDim, config.dim, sequentialState.positionAndLayer);
            TestLLMEndToEnd.addInPlace(context, sequentialState.wrapX, sequentialState.wrapXb);
        }

        // Final layer norm
        TestLLMEndToEnd.reduceSquareSums(context, sequentialState.wrapX, intermediateReduceThree, localSizeRMS);
        TestLLMEndToEnd.finalSum(intermediateReduceThree, config.dim, config.rmsNormEps);
        TestLLMEndToEnd.normalizeAndScaleInNout(context, sequentialState.wrapX, weights.rms_final_weight_as_floatArray, intermediateReduceThree, config.dim, sequentialState.positionAndLayer);

        // Final projection to logits
        TestLLMEndToEnd.matmulTornadoQ8(context, weights.wclsByteArray, sequentialState.wrapX, sequentialState.wrapLogits, config.dim);
    }

    private void validateResults() {
        // Compare the main outputs
        NumericalValidator.ValidationResult logitsResult =
                NumericalValidator.compareArrays(tornadoState.wrapLogits, sequentialState.wrapLogits, "Logits");
        System.out.println(logitsResult);

        // Ensure outputs are close enough
        if (!logitsResult.passed) {
            System.err.println("WARNING: Logits differ significantly between TornadoVM and sequential implementation");
        }

        // If we're capturing intermediate states, we can compare those too
        if (captureIntermediateStates) {
            System.out.println("Intermediate state comparison:");
            NumericalValidator.ValidationResult xResult =
                    NumericalValidator.compareArrays(tornadoState.wrapX, sequentialState.wrapX, "X");
            System.out.println(xResult);

            NumericalValidator.ValidationResult xbResult =
                    NumericalValidator.compareArrays(tornadoState.wrapXb, sequentialState.wrapXb, "Xb");
            System.out.println(xbResult);
        }

        // Get top 5 tokens from both implementations
        int[] topTornadoIndices = getTopKIndices(tornadoState.wrapLogits, 5);
        int[] topSequentialIndices = getTopKIndices(sequentialState.wrapLogits, 5);

        System.out.println("Top 5 TornadoVM logits:");
        for (int i = 0; i < 5; i++) {
            System.out.println("  Token " + topTornadoIndices[i] + ": " + tornadoState.wrapLogits.get(topTornadoIndices[i]));
        }

        System.out.println("Top 5 Sequential logits:");
        for (int i = 0; i < 5; i++) {
            System.out.println("  Token " + topSequentialIndices[i] + ": " + sequentialState.wrapLogits.get(topSequentialIndices[i]));
        }

        // Check if the argmax (highest probability token) is the same
        int tornadoArgmax = tornadoState.logits.argmax();
        int seqArgmax = sequentialState.logits.argmax();

        if (tornadoArgmax != seqArgmax) {
            System.out.println("WARNING: Top predicted token differs between implementations!");
            System.out.println("  TornadoVM: " + tornadoArgmax + " (score: " + tornadoState.wrapLogits.get(tornadoArgmax) + ")");
            System.out.println("  Sequential: " + seqArgmax + " (score: " + sequentialState.wrapLogits.get(seqArgmax) + ")");
        } else {
            System.out.println("Top predicted token matches: " + tornadoArgmax);
        }
    }

    private void validateKVCache() {
        int kvDim = (config.dim * config.numberOfKeyValueHeads) / config.numberOfHeads;

        // Compare KV caches between TornadoVM and sequential implementations
        float maxKeyDiff = 0.0f;
        float maxValDiff = 0.0f;

        for (int l = 0; l < config.numberOfLayers; l++) {
            NumericalValidator.ValidationResult keyCacheResult =
                    NumericalValidator.compareKVCacheLayer(
                            tornadoState.wrapKeyCache,
                            sequentialState.wrapKeyCache,
                            l,
                            (int)tornadoState.positionAndLayer.get(0),
                            kvDim,
                            config.contextLength
                    );

            NumericalValidator.ValidationResult valueCacheResult =
                    NumericalValidator.compareKVCacheLayer(
                            tornadoState.wrapValueCache,
                            sequentialState.wrapValueCache,
                            l,
                            (int)tornadoState.positionAndLayer.get(0),
                            kvDim,
                            config.contextLength
                    );

            System.out.println("Layer " + l + " key cache: " + (keyCacheResult.passed ? "PASSED" : "FAILED"));
            System.out.println("Layer " + l + " value cache: " + (valueCacheResult.passed ? "PASSED" : "FAILED"));

            if (!keyCacheResult.passed || !valueCacheResult.passed) {
                System.out.println("  Key cache max diff: " + keyCacheResult.maxAbsDiff);
                System.out.println("  Value cache max diff: " + valueCacheResult.maxAbsDiff);
            }

            maxKeyDiff = Math.max(maxKeyDiff, keyCacheResult.maxAbsDiff);
            maxValDiff = Math.max(maxValDiff, valueCacheResult.maxAbsDiff);
        }

        // Ensure the KV caches are close enough
        if (maxKeyDiff > 1e-4 || maxValDiff > 1e-4) {
            System.err.println("WARNING: KV cache differences exceed threshold");
            System.err.println("  Max key diff: " + maxKeyDiff);
            System.err.println("  Max value diff: " + maxValDiff);
        }
    }

    // Helper method to get top-k indices
    private int[] getTopKIndices(FloatArray array, int k) {
        int size = array.getSize();

        // Create a pair of (index, value) for sorting
        class IndexValuePair implements Comparable<IndexValuePair> {
            final int index;
            final float value;

            IndexValuePair(int index, float value) {
                this.index = index;
                this.value = value;
            }

            @Override
            public int compareTo(IndexValuePair other) {
                // Sort in descending order
                return Float.compare(other.value, this.value);
            }
        }

        IndexValuePair[] pairs = new IndexValuePair[size];
        for (int i = 0; i < size; i++) {
            pairs[i] = new IndexValuePair(i, array.get(i));
        }

        // Sort the pairs
        Arrays.sort(pairs);

        // Extract the top-k indices
        int[] result = new int[Math.min(k, size)];
        for (int i = 0; i < result.length; i++) {
            result[i] = pairs[i].index;
        }
        return result;
    }

    private GridScheduler setupGridSchedulers() {
        GridScheduler tornadoForwardScheduler = new GridScheduler();

        // Create common worker grids that will be used across different schedulers
        WorkerGrid dimWorker = new WorkerGrid1D(config.dim);
        dimWorker.setGlobalWork(config.dim, 1, 1);
        dimWorker.setLocalWork(Math.min(256, config.dim), 1, 1);

        WorkerGrid headsWorker = new WorkerGrid1D(config.numberOfHeads * config.contextLength);
        headsWorker.setGlobalWork(config.numberOfHeads * config.contextLength, 1, 1);
        headsWorker.setLocalWork(Math.min(64, config.contextLength), 1, 1);

        WorkerGrid singleWorker = new WorkerGrid1D(1);
        singleWorker.setGlobalWork(1, 1, 1);
        singleWorker.setLocalWork(1, 1, 1);

        WorkerGrid hiddenDimWorker = new WorkerGrid1D(config.hiddenDim);
        hiddenDimWorker.setGlobalWork(config.hiddenDim, 1, 1);
        hiddenDimWorker.setLocalWork(Math.min(256, config.hiddenDim), 1, 1);

        WorkerGrid vocabWorker = new WorkerGrid1D(config.vocabularySize);
        vocabWorker.setGlobalWork(config.vocabularySize, 1, 1);
        vocabWorker.setLocalWork(Math.min(256, config.vocabularySize), 1, 1);

        WorkerGrid ropeWorker = new WorkerGrid1D(config.dim / 2);
        ropeWorker.setGlobalWork(config.dim / 2, 1, 1);
        ropeWorker.setLocalWork(Math.min(128, config.dim / 2), 1, 1);

        // Scheduler 0: RMSNorm
        tornadoForwardScheduler.setWorkerGrid("rmsnorm.reduce", dimWorker);
        tornadoForwardScheduler.setWorkerGrid("rmsnorm.sum", singleWorker);
        tornadoForwardScheduler.setWorkerGrid("rmsnorm.normalize", dimWorker);

        // Scheduler 1: QKV
        tornadoForwardScheduler.setWorkerGrid("qkv.qmatmul", dimWorker);
        tornadoForwardScheduler.setWorkerGrid("qkv.kmatmul", dimWorker);
        tornadoForwardScheduler.setWorkerGrid("qkv.vmatmul", dimWorker);

        // Scheduler 2: RoPE
        tornadoForwardScheduler.setWorkerGrid("rotation.rope", ropeWorker);

        // Scheduler 3: Attention
        tornadoForwardScheduler.setWorkerGrid("attention.scores", headsWorker);
        tornadoForwardScheduler.setWorkerGrid("attention.max", headsWorker);
        tornadoForwardScheduler.setWorkerGrid("attention.expsum", headsWorker);
        tornadoForwardScheduler.setWorkerGrid("attention.normalize", headsWorker);
        tornadoForwardScheduler.setWorkerGrid("attention.weighted-sum", headsWorker);

        // Scheduler 4: FFN
        tornadoForwardScheduler.setWorkerGrid("ffn.matmul1", dimWorker);
        tornadoForwardScheduler.setWorkerGrid("ffn.residual1", dimWorker);
        tornadoForwardScheduler.setWorkerGrid("ffn.reduceFFN", dimWorker);
        tornadoForwardScheduler.setWorkerGrid("ffn.sum", singleWorker);
        tornadoForwardScheduler.setWorkerGrid("ffn.ns", dimWorker);
        tornadoForwardScheduler.setWorkerGrid("ffn.projcectOne", hiddenDimWorker);
        tornadoForwardScheduler.setWorkerGrid("ffn.projectionThree", hiddenDimWorker);
        tornadoForwardScheduler.setWorkerGrid("ffn.silu", hiddenDimWorker);
        tornadoForwardScheduler.setWorkerGrid("ffn.multiply", hiddenDimWorker);
        tornadoForwardScheduler.setWorkerGrid("ffn.projectionTwo", dimWorker);
        tornadoForwardScheduler.setWorkerGrid("ffn.residual2", dimWorker);

        // Scheduler 5: Final RMSNorm
        tornadoForwardScheduler.setWorkerGrid("finalrms.reduceRMS", dimWorker);
        tornadoForwardScheduler.setWorkerGrid("finalrms.sum", singleWorker);
        tornadoForwardScheduler.setWorkerGrid("finalrms.normalize", dimWorker);

        // Scheduler 6: Logits
        tornadoForwardScheduler.setWorkerGrid("logits.projection", vocabWorker);

        return tornadoForwardScheduler;
    }

    private List<ImmutableTaskGraph> createTaskGraphs() {
        List<ImmutableTaskGraph> taskGraphs = new ArrayList<>();
        KernelContext context = new KernelContext();

        int localSizeRMS = Math.min(256, config.dim);
        int localSizeHeads = Math.min(64, config.contextLength);
        int localSizeFFN = Math.min(256, config.dim);

        // ================ TASK GRAPH 0: RMS NORM ================
        TaskGraph rmsNormGraph = new TaskGraph("rmsnorm")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights.rms_att_weightFlat, intermediateReduceFirst, tornadoState.wrapXb)
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tornadoState.positionAndLayer)
                .task("reduce", TestLLMEndToEnd::reduceSquareSums, context, tornadoState.wrapX, intermediateReduceFirst, localSizeRMS)
                .task("sum", TestLLMEndToEnd::finalSum, intermediateReduceFirst, config.dim, config.rmsNormEps)
                .task("normalize", TestLLMEndToEnd::normalizeAndScale, context, tornadoState.wrapXb, tornadoState.wrapX, weights.rms_att_weightFlat, intermediateReduceFirst, config.dim, tornadoState.positionAndLayer)
                .task("forcePropagation", TestLLMEndToEnd::forcePropagationThreeArrays, tornadoState.wrapX, tornadoState.wrapXb, tornadoState.positionAndLayer)
                .persistOnDevice(weights.rms_att_weightFlat, intermediateReduceFirst)
                .persistOnDevice(tornadoState.wrapX, tornadoState.wrapXb, tornadoState.positionAndLayer, context);
        taskGraphs.add(rmsNormGraph.snapshot());

        // ================ TASK GRAPH 1: QKV MATMULS ================
        TaskGraph qkvGraph = new TaskGraph("qkv")
                .consumeFromDevice(rmsNormGraph.getTaskGraphName(),
                        tornadoState.wrapXb, tornadoState.positionAndLayer, tornadoState.wrapX, context)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION,
                        weights.wqFlat, weights.wkFlat, weights.wvFlat)
                .task("qmatmul", TestLLMEndToEnd::matrixVectorSimple, context, tornadoState.wrapXb, tornadoState.wrapQ, weights.wqFlat, config.dim, config.dim, tornadoState.positionAndLayer)
                .task("kmatmul", TestLLMEndToEnd::matrixVectorSimple, context, tornadoState.wrapXb, tornadoState.wrapK, weights.wkFlat, config.dim, (config.dim * config.numberOfKeyValueHeads) / config.numberOfHeads, tornadoState.positionAndLayer)
                .task("vmatmul", TestLLMEndToEnd::matrixVectorSimple, context, tornadoState.wrapXb, tornadoState.wrapV, weights.wvFlat, config.dim, (config.dim * config.numberOfKeyValueHeads) / config.numberOfHeads, tornadoState.positionAndLayer)
                .task("forcePropagation", TestLLMEndToEnd::forcePropagationFiveArrays, tornadoState.wrapX, tornadoState.wrapXb, tornadoState.wrapQ, tornadoState.wrapK, tornadoState.wrapV)
                .persistOnDevice(weights.wqFlat, weights.wkFlat, weights.wvFlat)
                .persistOnDevice(tornadoState.wrapX, tornadoState.wrapXb,
                        tornadoState.wrapQ, tornadoState.wrapK, tornadoState.wrapV,
                        tornadoState.positionAndLayer, context);
        taskGraphs.add(qkvGraph.snapshot());

        // ================ TASK GRAPH 2: ROPE ROTATION ================
        TaskGraph ropeGraph = new TaskGraph("rotation")
                .consumeFromDevice(qkvGraph.getTaskGraphName(),
                        tornadoState.wrapX, tornadoState.wrapXb,
                        tornadoState.wrapQ, tornadoState.wrapK, tornadoState.wrapV,
                        tornadoState.positionAndLayer, context)
                .task("rope", TestLLMEndToEnd::ropeRotation, context, tornadoState.positionAndLayer, tornadoState.wrapQ, tornadoState.wrapK, (config.dim * config.numberOfKeyValueHeads) / config.numberOfHeads, config.headSize)
                .task("forcePropagation", TestLLMEndToEnd::forcePropagationFiveArrays, tornadoState.wrapX, tornadoState.wrapXb, tornadoState.wrapQ, tornadoState.wrapK, tornadoState.wrapV)
                .persistOnDevice(tornadoState.wrapX, tornadoState.wrapXb,
                        tornadoState.wrapQ, tornadoState.wrapK, tornadoState.wrapV,
                        tornadoState.positionAndLayer, context);
        taskGraphs.add(ropeGraph.snapshot());

        // ================ TASK GRAPH 3: Copy to Caches ================
        TaskGraph copyToCaches = new TaskGraph("copyToCaches")
                .consumeFromDevice(ropeGraph.getTaskGraphName(),
                        tornadoState.wrapX, tornadoState.wrapXb,
                        tornadoState.wrapQ, tornadoState.wrapK, tornadoState.wrapV,
                        tornadoState.positionAndLayer, context)
                .transferToDevice(DataTransferMode.UNDER_DEMAND, tornadoState.wrapKeyCache, tornadoState.wrapValueCache)
                .task("copyToKeyCache", TestLLMEndToEnd::copyToCache, tornadoState.wrapKeyCache, tornadoState.wrapK, tornadoState.positionAndLayer)
                .task("copyToValueCache", TestLLMEndToEnd::copyToCache, tornadoState.wrapValueCache, tornadoState.wrapV, tornadoState.positionAndLayer)
                .task("forcePropagation", TestLLMEndToEnd::forcePropagationSixArrays, tornadoState.wrapX, tornadoState.wrapXb, tornadoState.wrapQ, tornadoState.wrapK, tornadoState.wrapKeyCache, tornadoState.wrapValueCache)
                .persistOnDevice(tornadoState.wrapKeyCache,
                        tornadoState.wrapValueCache, tornadoState.wrapX, tornadoState.wrapXb,
                        tornadoState.wrapQ, tornadoState.wrapK, tornadoState.wrapV,
                        tornadoState.positionAndLayer, context);
        taskGraphs.add(copyToCaches.snapshot());

        // ================ TASK GRAPH 4: MULTI-HEAD ATTENTION ================
        int kvDim = (config.dim * config.numberOfKeyValueHeads) / config.numberOfHeads;
        int kvMul = config.numberOfHeads / config.numberOfKeyValueHeads;

        TaskGraph attentionGraph = new TaskGraph("attention")
                .consumeFromDevice(copyToCaches.getTaskGraphName(),
                        tornadoState.wrapKeyCache,
                        tornadoState.wrapValueCache, tornadoState.wrapX, tornadoState.wrapXb,
                        tornadoState.wrapQ, tornadoState.wrapK, tornadoState.wrapV,
                        tornadoState.positionAndLayer, context)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, maxValues, expValues, sumValues)
                .task("scores", TestLLMEndToEnd::calculateAttentionScores, context, tornadoState.positionAndLayer, config.contextLength, tornadoState.wrapQ, tornadoState.wrapKeyCache, tornadoState.wrapAtt, kvDim, kvMul,
                        config.headSize, 0, localSizeRMS)
                .task("max", TestLLMEndToEnd::findMaxAttentionScoress, context, tornadoState.positionAndLayer, config.contextLength, tornadoState.wrapAtt, maxValues, localSizeHeads)
                .task("expsum", TestLLMEndToEnd::calculateExpAndSum, context, tornadoState.positionAndLayer, config.contextLength, tornadoState.wrapAtt, maxValues, expValues, sumValues, localSizeHeads)
                .task("normalize", TestLLMEndToEnd::normalizeSoftmax, context, tornadoState.positionAndLayer, config.contextLength, expValues, sumValues, tornadoState.wrapAtt)
                .task("weighted-sum", TestLLMEndToEnd::computeWeightedSum, context, tornadoState.positionAndLayer, config.contextLength, tornadoState.wrapAtt, tornadoState.wrapValueCache, tornadoState.wrapXb, kvDim, kvMul,
                        config.headSize, 0)
                .task("forcePropagationAttention", TestLLMEndToEnd::forcePropagationThreeArrays, tornadoState.wrapX, tornadoState.wrapXb, tornadoState.positionAndLayer)
                .persistOnDevice(tornadoState.wrapKeyCache, tornadoState.wrapValueCache,
                        tornadoState.wrapQ, tornadoState.wrapK, tornadoState.wrapV, tornadoState.wrapAtt,
                        maxValues, expValues, sumValues)
                .persistOnDevice(tornadoState.wrapXb, tornadoState.positionAndLayer, tornadoState.wrapX, context);
        taskGraphs.add(attentionGraph.snapshot());

        // ================ TASK GRAPH 5: FFN ================
        TaskGraph ffnGraph = new TaskGraph("ffn")
                .consumeFromDevice(attentionGraph.getTaskGraphName(),
                        tornadoState.wrapXb, tornadoState.positionAndLayer,
                        tornadoState.wrapX, context)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION,
                        weights.woFlat, weights.rms_ffn_weightFlat,
                        weights.w1Flat, weights.w2Flat, weights.w3Flat,
                        intermediateReduceTwo, tornadoState.wrapHb, tornadoState.wrapHb2, tornadoState.wrapXb2)
                .task("matmul1", TestLLMEndToEnd::matrixVectorMultiply, context, tornadoState.wrapXb, tornadoState.wrapXb2, weights.woFlat, config.dim, config.dim, tornadoState.positionAndLayer)
                .task("residual1", TestLLMEndToEnd::addInPlace, context, tornadoState.wrapX, tornadoState.wrapXb2)
                .task("reduceFFN", TestLLMEndToEnd::reduceSquareSums, context, tornadoState.wrapX, intermediateReduceTwo, localSizeFFN)
                .task("sum", TestLLMEndToEnd::finalSum, intermediateReduceTwo, config.dim, config.rmsNormEps)
                .task("ns", TestLLMEndToEnd::normalizeAndScale, context, tornadoState.wrapXb, tornadoState.wrapX, weights.rms_ffn_weightFlat, intermediateReduceTwo, config.dim, tornadoState.positionAndLayer)
                .task("projcectOne", TestLLMEndToEnd::matrixVectorMultiply, context, tornadoState.wrapXb, tornadoState.wrapHb, weights.w1Flat, config.dim, config.hiddenDim, tornadoState.positionAndLayer)
                .task("projectionThree", TestLLMEndToEnd::matrixVectorMultiply, context, tornadoState.wrapXb, tornadoState.wrapHb2, weights.w3Flat, config.dim, config.hiddenDim, tornadoState.positionAndLayer)
                .task("silu", TestLLMEndToEnd::siluActivation, context, tornadoState.wrapHb)
                .task("multiply", TestLLMEndToEnd::elementMultiply, context, tornadoState.wrapHb2, tornadoState.wrapHb)
                .task("projectionTwo", TestLLMEndToEnd::matrixVectorMultiply, context, tornadoState.wrapHb, tornadoState.wrapXb, weights.w2Flat, config.hiddenDim, config.dim, tornadoState.positionAndLayer)
                .task("residual2", TestLLMEndToEnd::addInPlace, context, tornadoState.wrapX, tornadoState.wrapXb)
                .task("forcePropagationFFN", TestLLMEndToEnd::forcePropagationTwoArrays, tornadoState.wrapX, tornadoState.positionAndLayer)
                .persistOnDevice(
                        weights.woFlat, weights.rms_ffn_weightFlat,
                        weights.w1Flat, weights.w2Flat, weights.w3Flat,
                        intermediateReduceTwo, tornadoState.wrapHb, tornadoState.wrapHb2, tornadoState.wrapXb2)
                .persistOnDevice(tornadoState.wrapX, tornadoState.positionAndLayer, tornadoState.wrapXb, context);
        taskGraphs.add(ffnGraph.snapshot());

        // ================ TASK GRAPH 6: FINAL RMS NORM ================
        TaskGraph finalRmsNormGraph = new TaskGraph("finalrms")
                .consumeFromDevice(ffnGraph.getTaskGraphName(), tornadoState.wrapX, tornadoState.positionAndLayer, context)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights.rms_final_weight_as_floatArray)
                .task("reduceRMS", TestLLMEndToEnd::reduceSquareSums, context, tornadoState.wrapX, intermediateReduceThree, localSizeRMS)
                .task("sum", TestLLMEndToEnd::finalSum, intermediateReduceThree, config.dim, config.rmsNormEps)
                .task("normalize", TestLLMEndToEnd::normalizeAndScaleInNout, context, tornadoState.wrapX, weights.rms_final_weight_as_floatArray, intermediateReduceThree, config.dim, tornadoState.positionAndLayer)
                .task("forcePropagationFinalRMS", TestLLMEndToEnd::forcePropagationTwoArrays, tornadoState.wrapX, tornadoState.positionAndLayer)
                .persistOnDevice(weights.rms_final_weight_as_floatArray)
                .persistOnDevice(tornadoState.wrapX, tornadoState.positionAndLayer, context);
        taskGraphs.add(finalRmsNormGraph.snapshot());

        // ================ TASK GRAPH 7: FINAL PROJECTION TO LOGITS ================
        TaskGraph logitsGraph = new TaskGraph("logits")
                .consumeFromDevice(finalRmsNormGraph.getTaskGraphName(), tornadoState.wrapX, tornadoState.positionAndLayer, context)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights.wclsByteArray)
                .task("projection", TestLLMEndToEnd::matmulTornadoQ8, context, weights.wclsByteArray, tornadoState.wrapX, tornadoState.wrapLogits, config.dim)
                .persistOnDevice(weights.wclsByteArray)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tornadoState.wrapLogits);
        taskGraphs.add(logitsGraph.snapshot());

        return taskGraphs;
    }

    private void initializeWeights() {
        // Create a dummy token embedding table
        float[] tokenEmbeddingData = new float[config.vocabularySize * config.dim];
        for (int i = 0; i < tokenEmbeddingData.length; i++) {
            tokenEmbeddingData[i] = 0.01f * (i % 100);
        }
        FloatTensor token_embedding_table = new ArrayFloatTensor(tokenEmbeddingData);

        // Create RMS attention weights
        FloatBuffer[] rms_att_weight = new FloatBuffer[config.numberOfLayers];
        for (int l = 0; l < config.numberOfLayers; l++) {
            float[] rmsAttData = new float[config.dim];
            for (int i = 0; i < config.dim; i++) {
                rmsAttData[i] = 0.5f + 0.01f * (i % 20);
            }
            rms_att_weight[l] = FloatBuffer.wrap(rmsAttData);
        }

        // Create query, key, value weights
        FloatTensor[] wq = new FloatTensor[config.numberOfLayers];
        FloatTensor[] wk = new FloatTensor[config.numberOfLayers];
        FloatTensor[] wv = new FloatTensor[config.numberOfLayers];
        FloatTensor[] wo = new FloatTensor[config.numberOfLayers];

        int qSize = config.dim * config.dim;
        int kvSize = config.dim * ((config.dim * config.numberOfKeyValueHeads) / config.numberOfHeads);

        for (int l = 0; l < config.numberOfLayers; l++) {
            float[] qData = new float[qSize];
            float[] kData = new float[kvSize];
            float[] vData = new float[kvSize];
            float[] oData = new float[qSize];

            for (int i = 0; i < qSize; i++) {
                qData[i] = 0.01f * ((i + l) % 100 - 50) / 50.0f;
                if (i < oData.length) oData[i] = 0.01f * ((i + l + 2) % 100 - 50) / 50.0f;
            }

            for (int i = 0; i < kvSize; i++) {
                kData[i] = 0.01f * ((i + l + 1) % 100 - 50) / 50.0f;
                vData[i] = 0.01f * ((i + l + 3) % 100 - 50) / 50.0f;
            }

            wq[l] = new ArrayFloatTensor(qData);
            wk[l] = new ArrayFloatTensor(kData);
            wv[l] = new ArrayFloatTensor(vData);
            wo[l] = new ArrayFloatTensor(oData);
        }

        // Create FFN weights
        FloatBuffer[] rms_ffn_weight = new FloatBuffer[config.numberOfLayers];
        for (int l = 0; l < config.numberOfLayers; l++) {
            float[] rmsFFNData = new float[config.dim];
            for (int i = 0; i < config.dim; i++) {
                rmsFFNData[i] = 0.5f + 0.01f * (i % 20);
            }
            rms_ffn_weight[l] = FloatBuffer.wrap(rmsFFNData);
        }

        // Create FFN weights
        FloatTensor[] w1 = new FloatTensor[config.numberOfLayers];
        FloatTensor[] w2 = new FloatTensor[config.numberOfLayers];
        FloatTensor[] w3 = new FloatTensor[config.numberOfLayers];

        int w1w3Size = config.hiddenDim * config.dim;
        int w2Size = config.dim * config.hiddenDim;

        for (int l = 0; l < config.numberOfLayers; l++) {
            float[] w1Data = new float[w1w3Size];
            float[] w2Data = new float[w2Size];
            float[] w3Data = new float[w1w3Size];

            for (int i = 0; i < w1w3Size; i++) {
                w1Data[i] = 0.01f * ((i + l) % 100 - 50) / 50.0f;
                w3Data[i] = 0.01f * ((i + l + 2) % 100 - 50) / 50.0f;
            }

            for (int i = 0; i < w2Size; i++) {
                w2Data[i] = 0.01f * ((i + l + 1) % 100 - 50) / 50.0f;
            }

            w1[l] = new ArrayFloatTensor(w1Data);
            w2[l] = new ArrayFloatTensor(w2Data);
            w3[l] = new ArrayFloatTensor(w3Data);
        }

        // Create final RMS norm weight
        float[] rmsFinalData = new float[config.dim];
        for (int i = 0; i < config.dim; i++) {
            rmsFinalData[i] = 0.5f + 0.01f * (i % 20);
        }
        FloatBuffer rms_final_weight = FloatBuffer.wrap(rmsFinalData);

        // Create RoPE frequency components
        float[] freqCisRealData = new float[config.contextLength * (config.headSize / 2)];
        float[] freqCisImagData = new float[config.contextLength * (config.headSize / 2)];

        for (int pos = 0; pos < config.contextLength; pos++) {
            for (int i = 0; i < config.headSize / 2; i++) {
                float freq = (float) (1.0 / Math.pow(config.ropeTheta, 2.0 * i / config.headSize));
                float val = pos * freq;
                int idx = pos * (config.headSize / 2) + i;
                freqCisRealData[idx] = (float) Math.cos(val);
                freqCisImagData[idx] = (float) Math.sin(val);
            }
        }
        FloatBuffer freq_cis_real = FloatBuffer.wrap(freqCisRealData);
        FloatBuffer freq_cis_imag = FloatBuffer.wrap(freqCisImagData);

        // Create classifier weights (wcls)
        float[] wclsData = new float[config.vocabularySize * config.dim];
        for (int i = 0; i < wclsData.length; i++) {
            wclsData[i] = 0.01f * ((i + 5) % 100 - 50) / 50.0f;
        }
        FloatTensor wcls = new ArrayFloatTensor(wclsData);

        // Create the Weights object
        weights = new Weights(
                token_embedding_table,
                rms_att_weight,
                wq,
                wk,
                wv,
                wo,
                rms_ffn_weight,
                w1,
                w2,
                w3,
                rms_final_weight,
                freq_cis_real,
                freq_cis_imag,
                wcls
        );
    }
}


