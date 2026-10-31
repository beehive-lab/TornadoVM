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
package uk.ac.manchester.tornado.api.options;

/**
 * The area an option belongs to. Used to group options in {@code tornado --list-flags} and in the
 * generated flags documentation, in declaration order.
 */
public enum OptionCategory {

    RUNTIME("Runtime and device selection"),
    DEBUG("Debugging and logging"),
    PROFILER("Profiling"),
    THREAD_SCHEDULING("Thread scheduling"),
    OPENCL("OpenCL backend"),
    CUDA("CUDA backend"),
    METAL("Metal backend"),
    COMPILER("TornadoVM compiler (TC)"),
    LIBRARIES("Native libraries");

    private final String title;

    OptionCategory(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
