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
 * Supported mma.sync tile shapes for TornadoVM's PTX backend.
 * Each entry encodes the M, N, K dimensions and the minimum sm requirement.
 *
 * PTX instruction emitted:
 *   mma.sync.aligned.{shape}.row.col.f32.f16.f16.f32
 */
public enum MMAShape {
    M16N8K8("m16n8k8"),
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
