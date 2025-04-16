/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * *
 */
package uk.ac.manchester.tornado.runtime.graph;

/**
 * Enum representing TornadoVM bytecodes.
 */
public enum TornadoVMBytecodes {

    /**
     * Native buffer allocation. If there is not enough space on the target device,
     * then we throw an exception.
     * <p>
     * Format:
     *
     * <code>
     * ALLOC(dest, numObjects, objects)
     * </code>
     */
    ALLOC((byte) 10),

    /**
     * Send data from Host -> Device only in the first execution of a task-graph.
     * <p>
     * If there is no ALLOC associated with the TRANSFER_HOST_TO_DEVICE_ONCE-in, an
     * exception is launched.
     * <p>
     * Format:
     *
     * <code>
     * TRANSFER_HOST_TO_DEVICE_ONCE(obj, src, dest)
     * </code>
     */
    TRANSFER_HOST_TO_DEVICE_ONCE((byte) 11),

    /**
     * Send data from Host -> Device in every execution of a task-graph. If there is
     * no ALLOC associated with the TRANSFER_HOST_TO_DEVICE_ALWAYS, an exception is
     * launched.
     * <p>
     * Format:
     *
     * <code>
     * TRANSFER_HOST_TO_DEVICE_ALWAYS(obj, src, dest)
     * </code>
     */
    TRANSFER_HOST_TO_DEVICE_ALWAYS((byte) 12),

    /**
     * Send data from Device -> Host in every execution of the task-graph. If there
     * is no ALLOC associated with the TRANSFER_DEVICE_TO_HOST_ALWAYS, an exception
     * is launched.
     * <p>
     * Format:
     *
     * <code>
     * TRANSFER_DEVICE_TO_HOST_ALWAYS(obj, src, dest)
     * </code>
     */
    TRANSFER_DEVICE_TO_HOST_ALWAYS((byte) 13),

    /**
     *
     */
    TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING((byte) 14), // TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING(obj, src, dest)

    /**
     * Compile the code the first iteration that the task-graph is executed and then
     * execute the kernel. If the kernel is already compiled, the kernel is directly
     * launched.
     * <p>
     * Format:
     *
     * <code>
     * LAUNCH(dep list index)
     * </code>
     */
    LAUNCH((byte) 15),

    /**
     * Sync point. The BC interpreter waits for an event to be finished before
     * continuing the execution.
     * <p>
     * Format:
     *
     * <code>
     * BARRIER <event>
     * </code>
     */
    BARRIER((byte) 16),

    /**
     * Initialization of a TornadoVM BC region.
     * <p>
     * Format:
     *
     * <code>
     * INIT(num contexts, num stacks, num dep lists)
     * </code>
     */
    INIT((byte) 17),

    /**
     * Execution initialization.
     */
    BEGIN((byte) 18),

    /**
     * Register an event to be used in a barrier bytecode.
     * <p>
     * Format:
     *
     * <code>
     * ADD_DEPENDENCY(list index)
     * </code>
     */
    ADD_DEPENDENCY((byte) 19),

    /**
     * Open an execution context. It needs a context-id (device-id).
     * <p>
     * Format:
     *
     * <code>
     * CONTEXT(ctx)
     * </code>
     */
    CONTEXT((byte) 20),

    /**
     * Close a bytecode region associated with a context.
     * <p>
     * Format:
     *
     * <code>
     * END(ctx)
     * </code>
     */
    END((byte) 21),

    /**
     * Add a constant value to be used as an argument for a compute-kernel.
     * <p>
     * Format:
     *
     * <code>
     * PUSH_CONSTANT_ARGUMENT(constant)
     * </code>
     */
    PUSH_CONSTANT_ARGUMENT((byte) 22),

    /**
     * Add a reference (e.g., a Java array reference) to be used as an argument for
     * a compute-kernel.
     * <p>
     * Format:
     *
     * <code>
     * PUSH_REFERENCE_ARGUMENT(reference)
     * </code>
     */
    PUSH_REFERENCE_ARGUMENT((byte) 23),

    /**
     * De-allocation of a buffer from a device.
     * <p>
     * Format:
     *
     * <code>
     * DEALLOC(obj,dest)
     * </code>
     */
    DEALLOC((byte) 24),


    /**
     * Reuse of a buffer from a device.
     * <p>
     * Format:
     *
     * <code>
     * ON_DEVICE(obj,dest)
     * </code>
     */
    ON_DEVICE((byte) 25),


    /**
     * Persist a buffer on a device.
     * <p>
     * Format:
     *
     * <code>
     * PERSIST(obj,dest)
     * </code>
     */
    PERSIST((byte) 26);

    final byte value;

    TornadoVMBytecodes(byte value) {
        this.value = value;
    }

    public byte value() {
        return value;
    }
}
