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
package uk.ac.manchester.tornado.mlx.jit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a TornadoVM {@code KernelContext} kernel as the JIT counterpart of the MLX operation(s)
 * it computes, e.g. {@code @JitBaseline("mlx_add")}. Every MLX operation the provider binds is
 * compared against such a kernel doing the same work (same dtype, same layout).
 * {@code tornado-mlx/scripts/update_coverage.py} records the baseline in the coverage manifest, and
 * the build fails if a bound operation has no tested baseline.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface JitBaseline {

    /** MLX operation names, spelled as mlx-c's {@code mlx/c/*.h} spell them. */
    String[] value();

    /**
     * Where the kernel comes from: {@code "written"} for a kernel written for tornado-mlx, or the
     * TornadoVM source it was taken or adapted from (file and method).
     */
    String source() default "written";
}
