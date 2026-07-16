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
package uk.ac.manchester.tornado.fuzz.oracle;

/**
 * A numeric mismatch between the CUDA device result and the JVM oracle.
 */
public final class Diff {

    public final int firstBadIndex;
    public final String expected;
    public final String actual;
    public final int mismatchCount;
    /** Human-readable dump of the first few mismatching indices (with bit patterns for floats). */
    public final String detail;

    public Diff(int firstBadIndex, String expected, String actual, int mismatchCount, String detail) {
        this.firstBadIndex = firstBadIndex;
        this.expected = expected;
        this.actual = actual;
        this.mismatchCount = mismatchCount;
        this.detail = detail;
    }
}
