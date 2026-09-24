/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.kotlin;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

/**
 * Decides whether the Kotlin-specific compiler support applies to a method.
 *
 * <p>
 * Every Kotlin-specific rewrite in the compiler is guarded by {@link #isKotlinMethod}: it only applies to methods whose
 * declaring class was compiled by kotlinc (kotlinc annotates every class it emits with {@code kotlin.Metadata}), and
 * only while {@link TornadoOptions#KOTLIN_SUPPORT} ({@code -Dtornado.kotlin.support}) is enabled. Java methods always
 * take the existing code paths.
 * </p>
 *
 * <p>
 * The annotation is matched by name, so the runtime does not depend on the Kotlin standard library.
 * </p>
 */
public final class KotlinSupport {

    private static final String KOTLIN_METADATA = "kotlin.Metadata";

    private static final Map<ResolvedJavaType, Boolean> KOTLIN_TYPES = new ConcurrentHashMap<>();

    private KotlinSupport() {
    }

    /**
     * @param method
     *     a method being compiled.
     * @return true if Kotlin support is enabled and {@code method} was compiled by kotlinc.
     */
    public static boolean isKotlinMethod(ResolvedJavaMethod method) {
        return TornadoOptions.KOTLIN_SUPPORT && method != null && isKotlinType(method.getDeclaringClass());
    }

    private static boolean isKotlinType(ResolvedJavaType type) {
        return KOTLIN_TYPES.computeIfAbsent(type, KotlinSupport::hasKotlinMetadata);
    }

    private static boolean hasKotlinMetadata(ResolvedJavaType type) {
        try {
            for (Annotation annotation : type.getDeclaredAnnotations()) {
                if (KOTLIN_METADATA.equals(annotation.annotationType().getName())) {
                    return true;
                }
            }
        } catch (RuntimeException | LinkageError e) {
            // Types whose annotations cannot be read (e.g. synthetic or array types) are not Kotlin classes.
        }
        return false;
    }
}
