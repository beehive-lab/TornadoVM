/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.exceptions;

/**
 * Thrown when hardware FP8 is unavailable for the current (device, library)
 * pair - either the GPU predates FP8 tensor cores (Ada sm_89) or the installed
 * cuBLAS has no FP8 kernels for this architecture (e.g. a pre-CUDA-12.8 cuBLAS
 * on a Blackwell sm_120 device).
 *
 * <p>Deliberately extends {@link RuntimeException} rather than
 * {@link TornadoRuntimeException}: the task-graph bailout handler catches
 * {@code TornadoRuntimeException} and replaces it with a generic
 * "Recover option disabled" message, which would hide this condition. Staying
 * outside that hierarchy lets the exception reach the caller intact, so the
 * unit-test harness can report Unsupported instead of Failed - mirroring
 * {@link TornadoDeviceMMANotSupported}.</p>
 */
public class TornadoDeviceFP8NotSupported extends RuntimeException {

    public TornadoDeviceFP8NotSupported(final String msg) {
        super(msg);
    }

}
