/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.tc.ir.analysis;

/** What {@link CountedLoop#analyse} found: a counted loop, or why the loop is not one. */
public sealed interface LoopShape permits CountedLoop, LoopShape.NotCounted {

    /** Why a loop is not counted, for passes that must explain a refusal. */
    enum Failure {
        /** Not exactly one entry edge and one back edge. */
        EDGES,
        /** The back edge does not pass {@code iv + step}. */
        INCREMENT,
        /** The step changes inside the loop. */
        STEP
    }

    /** Not a counted loop, and why. */
    record NotCounted(Failure failure) implements LoopShape {
    }
}
