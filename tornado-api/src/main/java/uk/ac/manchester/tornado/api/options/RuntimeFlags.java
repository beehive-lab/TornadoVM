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
 * Options of the TornadoVM runtime: which backends and devices it uses, and how it manages them.
 */
public final class RuntimeFlags {

    private static final OptionCategory CATEGORY = OptionCategory.RUNTIME;

    public static final Option<Integer> BACKEND_INDEX = Option.integer("tornado.backend") //
            .defaultValue(0) //
            .description("Index of the default backend, as numbered by `tornado --devices`.") //
            .category(CATEGORY).build();

    public static final Option<Integer> DEVICE_INDEX = Option.integer("tornado.device") //
            .defaultValue(0) //
            .description("Index of the default device within the default backend.") //
            .category(CATEGORY).build();

    public static final Option<String> ENABLED_BACKENDS = Option.string("tornado.enabled.backends") //
            .defaultValue("") //
            .description("Comma-separated backends to load (`opencl`, `cuda`, `metal`); empty loads every installed backend. "
                    + "A backend left out is not initialised, which shortens start-up. Backend indices count only the loaded backends.") //
            .category(CATEGORY).build();

    public static final Option<Long> DEVICE_MEMORY = Option.size("tornado.device.memory") //
            .defaultValue(() -> 4L * 1024 * 1024 * 1024, "4GB") //
            .description("Maximum device memory TornadoVM allocates per device, e.g. `512MB`, `8GB`.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> CONCURRENT_DEVICES = Option.bool("tornado.concurrent.devices") //
            .defaultValue(false) //
            .description("Run the tasks of a task graph concurrently on several devices, in one or several backends.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> OUT_OF_ORDER_EXECUTION = Option.bool("tornado.ooo.execution.enable") //
            .defaultValue(false) //
            .description("Create device command queues that may run commands out of order.") //
            .category(CATEGORY).deprecatedKey("tornado.ooo-execution.enable").build();

    public static final Option<Boolean> RECOVER_BAILOUT = Option.bool("tornado.recover.bailout") //
            .defaultValue(false) //
            .description("When a task fails to compile or launch, run it as sequential Java on the host instead of failing. "
                    + "Off by default: the fallback is silent, so a broken device path looks like a working one.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> REUSE_DEVICE_BUFFERS = Option.bool("tornado.reuse.device.buffers") //
            .defaultValue(true) //
            .description("Keep device buffers between executions of a task graph instead of allocating them every time.") //
            .category(CATEGORY).dynamic().build();

    public static final Option<Boolean> DEALLOCATE_BUFFERS = Option.bool("tornado.deallocate.buffers") //
            .defaultValue(true) //
            .description("Free every device buffer of an execution plan when the plan is closed.") //
            .category(CATEGORY).dynamic().build();

    public static final Option<Boolean> VIRTUAL_DEVICE = Option.bool("tornado.virtual.device") //
            .defaultValue(false) //
            .description("Generate code for a virtual device described by `tornado.device.desc` instead of a real one.") //
            .category(CATEGORY).build();

    public static final Option<String> VIRTUAL_DEVICE_FILE = Option.string("tornado.device.desc") //
            .defaultValue("etc/virtual-device-template.json") //
            .description("JSON description of the virtual device used by `tornado.virtual.device`.") //
            .category(CATEGORY).build();

    public static final Option<Integer> MAX_BYTECODE_SIZE = Option.integer("tornado.tvm.max.bytecode.size") //
            .defaultValue(4096) //
            .description("Size in bytes of the buffer that holds the TornadoVM bytecodes of a task graph.") //
            .category(CATEGORY).deprecatedKey("tornado.tvm.maxbytecodesize").build();

    public static final Option<String> API_IMPLEMENTATION = Option.string("tornado.load.api.implementation") //
            .defaultValue("uk.ac.manchester.tornado.runtime.tasks.TornadoTaskGraph") //
            .description("Class implementing the task-graph API.") //
            .category(CATEGORY).internal().build();

    public static final Option<String> RUNTIME_IMPLEMENTATION = Option.string("tornado.load.runtime.implementation") //
            .defaultValue("uk.ac.manchester.tornado.runtime.TornadoCoreRuntime") //
            .description("Class implementing the TornadoVM runtime.") //
            .category(CATEGORY).internal().build();

    public static final Option<String> SETTINGS_IMPLEMENTATION = Option.string("tornado.load.tornado.implementation") //
            .defaultValue("uk.ac.manchester.tornado.runtime.common.Tornado") //
            .description("Class implementing the TornadoVM settings access.") //
            .category(CATEGORY).internal().build();

    private RuntimeFlags() {
    }
}
