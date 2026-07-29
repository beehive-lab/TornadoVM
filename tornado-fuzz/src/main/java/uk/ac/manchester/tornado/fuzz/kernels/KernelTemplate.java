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
package uk.ac.manchester.tornado.fuzz.kernels;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.fuzz.CaseResult;
import uk.ac.manchester.tornado.fuzz.FuzzConfig;
import uk.ac.manchester.tornado.fuzz.RandomGen;

/**
 * A family of KernelContext kernels that the fuzzer instantiates with random
 * data, element types and launch configs. Each template owns its JVM golden
 * reference (a plain loop over the same pure op helpers the kernel uses), so the
 * device result is checked against ordinary Java semantics.
 */
public interface KernelTemplate {

    /** Stable identifier used in reports and bundle names. */
    String id();

    /**
     * Instantiate and execute one case on the CUDA device, then compare against
     * the JVM oracle. Must not throw for MISMATCH/EXCEPTION cases (those are
     * returned as a {@link CaseResult}); may propagate a JVM/native crash.
     */
    CaseResult run(FuzzConfig cfg, RandomGen rng, TornadoDevice device);
}
