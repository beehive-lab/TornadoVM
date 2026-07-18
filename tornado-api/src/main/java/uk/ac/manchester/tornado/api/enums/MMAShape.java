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
package uk.ac.manchester.tornado.api.enums;

/**
 * Supported mma.sync tile shapes for TornadoVM's PTX and CUDA backends.
 *
 * <p>A shape is only the M/N/K tile geometry; the operand element type comes from
 * the KernelContext method used:
 * <ul>
 *   <li>{@code M16N8K16} - fp16 ({@code mma}, f32 accumulator, sm_80+)</li>
 *   <li>{@code M16N8K32} - int8 ({@code mmaInt8}, s32 accumulator, sm_80+) or
 *       FP8 E4M3/E5M2 ({@code mmaFP8E4M3}/{@code mmaFP8E5M2}, f32 accumulator,
 *       sm_89+, CUDA backend)</li>
 * </ul>
 *
 * PTX instruction emitted:
 *   mma.sync.aligned.{shape}.row.col.{acc}.{ab}.{ab}.{acc}
 */
public enum MMAShape {
    M16N8K16("m16n8k16"),
    M16N8K32("m16n8k32");

    private final String ptxName;

    MMAShape(String ptxName) {
        this.ptxName = ptxName;
    }

    public String getPtxName() {
        return ptxName;
    }
}
