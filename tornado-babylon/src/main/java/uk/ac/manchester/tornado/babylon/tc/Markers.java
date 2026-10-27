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
package uk.ac.manchester.tornado.babylon.tc;

/**
 * Markers put into code models before they are translated. {@code parallel(init)} wraps the initial
 * value of a loop variable annotated {@code @Parallel}: code models do not keep annotations on local
 * variables, so the frontend reads them from the class file and marks the variable, and the
 * translator tags the loop header it reaches.
 */
public final class Markers {

    private Markers() {
    }

    public static int parallel(int init) {
        return init;
    }

    public static long parallel(long init) {
        return init;
    }
}
