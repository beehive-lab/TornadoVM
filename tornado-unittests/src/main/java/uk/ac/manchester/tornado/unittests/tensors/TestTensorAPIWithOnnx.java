/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.unittests.tensors;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import org.junit.Test;
import uk.ac.manchester.tornado.api.types.tensors.Shape;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.nio.FloatBuffer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

public class TestTensorAPIWithOnnx extends TornadoTestBase {

    private final String INPUT_TENSOR_NAME = "data";
    private final String OUTPUT_TENSOR_NAME = "mobilenetv20_output_flatten0_reshape0";
    private final String MODEL_PATH = "mobilenetv2-7.onnx";

    @Test
    public void testOnnxCompatibility() throws OrtException {
        // Initialize the ONNX Runtime environment
        Shape shape = new Shape(1, 3, 224, 224);
        System.out.println("Tornado Shape " + shape.toString());
        System.out.println("Tornado Shape dimensions " + shape.dimensions());
        System.out.println("Tornado Shape to Onnx " + shape.toONNXShapeString());
        float[] outputData = new float[0];
        try (OrtEnvironment env = OrtEnvironment.getEnvironment()) {
            // Load the MobileNet V2 ONNX model
            OrtSession session = env.createSession(MODEL_PATH, new OrtSession.SessionOptions());

            // Prepare input tensor data (assuming a single 224x224 RGB image)
            float[] inputData = new float[1 * 224 * 224 * 3];

            FloatBuffer inputBuffer = FloatBuffer.wrap(inputData);
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, inputBuffer, new long[] { 1, 3, 224, 224 });
            System.out.println("Session " + session.getInputInfo().toString());
            System.out.println("Session " + session.getInputNames());
            System.out.println("Session " + session.getMetadata().toString());
            System.out.println("Session " + session.getOutputNames());
            Map<String, OnnxTensor> inputMap = new HashMap<>();
            inputMap.put(INPUT_TENSOR_NAME, inputTensor);
            //            System.exit(1);

            // Run the model inference
            try (OrtSession.Result outputMap = session.run(inputMap)) {
                Optional<OnnxValue> optionalOutputTensor = outputMap.get(OUTPUT_TENSOR_NAME);
                if (optionalOutputTensor.isEmpty()) {
                    throw new IllegalArgumentException("Output tensor not found in model output.");
                }
                OnnxTensor outputTensor = (OnnxTensor) optionalOutputTensor.get();
                // Get the output tensor data (assuming it's a vector of probabilities)
                //                outputData = outputTensor.;

                //                printTopKProbabilities(outputData, 5);
                System.out.println("Output data; " + session.getOutputInfo().toString());
                System.out.println("Output data; " + outputTensor.getInfo());

                System.out.println("Output data; " + outputTensor.getValue().toString());

            }
        } finally {
            // Print the top 5 probability values and indices
        }

    }

    private static void printTopKProbabilities(float[] probabilities, int k) {
        PriorityQueue<ProbabilityIndexPair> pq = new PriorityQueue<>(Comparator.comparingDouble(p -> -p.probability));
        for (int i = 0; i < probabilities.length; i++) {
            pq.offer(new ProbabilityIndexPair(probabilities[i], i));
            if (pq.size() > k) {
                pq.poll();
            }
        }

        ProbabilityIndexPair[] topKPairs = new ProbabilityIndexPair[k];
        for (int i = k - 1; i >= 0; i--) {
            topKPairs[i] = pq.poll();
        }

        for (int i = 0; i < k; i++) {
            System.out.printf("Top %d: Index=%d, Probability=%.5f%n", i + 1, topKPairs[i].index, topKPairs[i].probability);
        }
    }

    private static class ProbabilityIndexPair {
        double probability;
        int index;

        ProbabilityIndexPair(double probability, int index) {
            this.probability = probability;
            this.index = index;
        }
    }
}
