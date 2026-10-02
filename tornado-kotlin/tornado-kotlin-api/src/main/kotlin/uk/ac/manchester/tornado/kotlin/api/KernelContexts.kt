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
@file:JvmName("KernelContexts")

package uk.ac.manchester.tornado.kotlin.api

import uk.ac.manchester.tornado.api.KernelContext

// The thread-index fields of KernelContext are boxed Integers. `val i = context.globalIdx` keeps
// the box and unboxes it at every use, which the KernelContext lowering cannot compile. These
// inline accessors unbox exactly once per read, which is the shape javac emits for
// `int i = context.globalIdx`.

/** Global thread id in dimension X (`get_global_id(0)`). */
inline val KernelContext.globalIdX: Int get() = globalIdx

/** Global thread id in dimension Y (`get_global_id(1)`). */
inline val KernelContext.globalIdY: Int get() = globalIdy

/** Global thread id in dimension Z (`get_global_id(2)`). */
inline val KernelContext.globalIdZ: Int get() = globalIdz

/** Local (work-group) thread id in dimension X (`get_local_id(0)`). */
inline val KernelContext.localIdX: Int get() = localIdx

/** Local (work-group) thread id in dimension Y (`get_local_id(1)`). */
inline val KernelContext.localIdY: Int get() = localIdy

/** Local (work-group) thread id in dimension Z (`get_local_id(2)`). */
inline val KernelContext.localIdZ: Int get() = localIdz

/** Work-group id in dimension X (`get_group_id(0)`). */
inline val KernelContext.groupIdX: Int get() = groupIdx

/** Work-group id in dimension Y (`get_group_id(1)`). */
inline val KernelContext.groupIdY: Int get() = groupIdy

/** Work-group id in dimension Z (`get_group_id(2)`). */
inline val KernelContext.groupIdZ: Int get() = groupIdz

/** Global size in dimension X (`get_global_size(0)`). */
inline val KernelContext.globalSizeX: Int get() = globalGroupSizeX

/** Global size in dimension Y (`get_global_size(1)`). */
inline val KernelContext.globalSizeY: Int get() = globalGroupSizeY

/** Global size in dimension Z (`get_global_size(2)`). */
inline val KernelContext.globalSizeZ: Int get() = globalGroupSizeZ

/** Work-group size in dimension X (`get_local_size(0)`). */
inline val KernelContext.localSizeX: Int get() = localGroupSizeX

/** Work-group size in dimension Y (`get_local_size(1)`). */
inline val KernelContext.localSizeY: Int get() = localGroupSizeY

/** Work-group size in dimension Z (`get_local_size(2)`). */
inline val KernelContext.localSizeZ: Int get() = localGroupSizeZ
