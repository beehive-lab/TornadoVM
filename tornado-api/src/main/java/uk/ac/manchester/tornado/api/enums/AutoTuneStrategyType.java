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
 * Algorithm used to generate the candidate local work sizes that auto-tuning benchmarks.
 */
public enum AutoTuneStrategyType {
    /**
     * Every candidate within the device limits. The exact set is defined by the backend.
     */
    EXHAUSTIVE,
    /**
     * Reserved for a heuristic search and not implemented. The Metal strategy throws
     * {@code UnsupportedOperationException} for it, so tuning logs a warning and leaves the task unchanged.
     */
    SMART;
}
