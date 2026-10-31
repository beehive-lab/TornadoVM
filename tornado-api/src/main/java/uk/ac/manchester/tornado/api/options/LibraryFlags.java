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
 * Options of the native-library bindings (MLX, cuDNN, NCCL).
 */
public final class LibraryFlags {

    private static final OptionCategory CATEGORY = OptionCategory.LIBRARIES;

    public static final Option<Boolean> MLX_ASYNC = Option.bool("tornado.mlx.async") //
            .defaultValue(false) //
            .description("Commit MLX library tasks without waiting for the GPU; library calls only encode GPU work.") //
            .category(CATEGORY).build();

    public static final Option<String> MLX_METALLIB = Option.string("tornado.mlx.metallib") //
            .noDefault("the mlx.metallib installed next to libmlx") //
            .description("Path of MLX's compiled kernel library (`mlx.metallib`).") //
            .category(CATEGORY).build();

    public static final Option<Boolean> CUDNN_DETERMINISTIC = Option.bool("tornado.cudnn.deterministic") //
            .defaultValue(false) //
            .description("Choose only deterministic cuDNN algorithms.") //
            .category(CATEGORY).build();

    public static final Option<Long> NCCL_SPIN_WAIT_MICROS = Option.longValue("tornado.nccl.spin.wait.micros") //
            .defaultValue(1000L) //
            .description("Microseconds NCCL rank threads and the caller spin before parking; 0 parks at once.") //
            .category(CATEGORY).internal().deprecatedKey("tornado.nccl.spinWaitMicros").build();

    private LibraryFlags() {
    }
}
