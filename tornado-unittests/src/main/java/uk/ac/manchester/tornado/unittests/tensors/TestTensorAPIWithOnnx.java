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

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import uk.ac.manchester.tornado.api.types.tensors.Shape;
import uk.ac.manchester.tornado.api.types.tensors.TensorFP32;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tensors.TestTensorAPIWithOnnx
 * </code>
 */
public class TestTensorAPIWithOnnx extends TornadoTestBase {

    private final String INPUT_TENSOR_NAME = "data";
    private final String OUTPUT_TENSOR_NAME = "mobilenetv20_output_flatten0_reshape0";

    private final String MODEL_URL = "https://github.com/onnx/models/raw/main/validated/vision/classification/mobilenet/model/mobilenetv2-7.onnx";

    /**
     * Tests the compatibility and functionality of an ONNX model using the ONNX Runtime (ORT) in Java.
     * This test verifies that the model, presumably MobileNet V2, can process a predefined Tornado tensor,
     * perform inference, and produce an output tensor.
     *
     * <p>The test follows these steps:
     * <ol>
     * <li>Creates a {@link Shape} object to define the dimensions of the input tensor.</li>
     * <li>Initializes a {@link TensorFP32} with the shape and sets all elements to a specific value.</li>
     * <li>Obtains an {@link OrtEnvironment} instance for working with ONNX Runtime.</li>
     * <li>Creates a session with the model specified by {@code MODEL_PATH} and session options.</li>
     * <li>Prepares the input tensor and maps it to the expected input name of the model.</li>
     * <li>Executes the model inference with the input map and captures the output.</li>
     * <li>Verifies the existence of the output tensor and performs cleanup.</li>
     * </ol>
     * </p>
     *
     * @throws OrtException
     *     If an error occurs in the ONNX Runtime environment, such as issues with creating the session or running the model.
     */
    @Test
    public void testOnnxCompatibility() throws OrtException, IOException {
        Shape shape = new Shape(1, 3, 224, 224);
        TensorFP32 tornadoTensor = new TensorFP32(shape);

        tornadoTensor.init(2f);

        OnnxTensor outputTensor = null;

        String modelPath = downloadAndSetModelPath(MODEL_URL);

        try (OrtEnvironment env = OrtEnvironment.getEnvironment()) {
            // Load the MobileNet V2 ONNX model
            OrtSession session = env.createSession(modelPath, new OrtSession.SessionOptions());

            OnnxTensor inputTensor = OnnxTensor.createTensor(env, tornadoTensor.getFloatBuffer(), shape.dimensions());

            Map<String, OnnxTensor> inputMap = new HashMap<>();
            inputMap.put(INPUT_TENSOR_NAME, inputTensor);

            // Run the model inference
            try (OrtSession.Result outputMap = session.run(inputMap)) {
                Optional<OnnxValue> optionalOutputTensor = outputMap.get(OUTPUT_TENSOR_NAME);
                if (optionalOutputTensor.isEmpty()) {
                    throw new IllegalArgumentException("Output tensor not found in model output.");
                }
                outputTensor = (OnnxTensor) optionalOutputTensor.get();

            }
        } finally {
            Assert.assertNotNull(outputTensor);
        }
    }

    private String downloadAndSetModelPath(String urlString) throws IOException {
        String path;
        URL url = new URL(urlString);
        Path tempDir = Files.createTempDirectory("onnx_models");
        Path modelPath = Paths.get(tempDir.toString(), "mobilenetv2-7.onnx");
        path = modelPath.toString();

        try (BufferedInputStream in = new BufferedInputStream(url.openStream()); FileOutputStream fileOutputStream = new FileOutputStream(modelPath.toFile())) {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        }
        return path;
    }

}
