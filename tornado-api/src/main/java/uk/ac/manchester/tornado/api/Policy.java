/*
 * Copyright (c) 2013-2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api;

/**
 * Enumerate with the different policies for the dynamic reconfiguration (live
 * task migration).
 *
 */
public enum Policy {

    /**
     * Perform dynamic reconfiguration based on peak performance for each device
     * used in the evaluation. In this mode, the TornadoVM runtime will perform
     * a warm-up phase in which the code will be compiled and executed, and then
     * it will evaluate the overall performance for all the devices.
     * When using the {@link Policy#PERFORMANCE} mode, the dynamic reconfiguration
     * runtime switches to the highest-performance device (lowest end-to-end runtime
     * after warm-up).
     */
    PERFORMANCE("Performance"), //

    /**
     * Perform dynamic reconfiguration based on best end-to-end runtime, including
     * compilation, data transfers and execution. In this mode, the TornadoVM runtime
     * will compile and run the whole application for every reachable accelerator. In
     * this mode, there is no warm-up. When using this mode, the dynamic reconfiguration
     * runtime of TornadoVM switches to the best device based on the lowest end-to-end
     * runtime, including compilation and data transfers.
     */
    END_2_END("End_2_End"), //

    /**
     * Perform dynamic reconfiguration based on the lowest latency. In this mode, TornadoVM
     * will run a set of Java threads in a pool. Each Java threads maps to a reachable
     * hardware accelerator by the TornadoVM runtime. The first thread that finishes the execution
     * is considered the best performing device, thus, the TornadoVM runtime will kill the rest
     * of the threads.
     *
     * <p>Note that with this mode, TornadoVM may not select the fastest device for the task if
     * the CPU is considered as a hardware accelerator too, since the TornadoVM runtime also
     * operates on the same CPU. However, for big data applications and compute-bound workloads,
     * this solution can provide a faster answer on which device is the most suitable for the
     * task without waiting for all executions to finish.
     * </p>
     */
    LATENCY("Latency");

    private final String policyName;

    Policy(String policyName) {
        this.policyName = policyName;
    }

    @Override
    public String toString() {
        return policyName;
    }
}
