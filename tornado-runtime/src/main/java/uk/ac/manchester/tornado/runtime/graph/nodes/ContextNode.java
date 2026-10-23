/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2023 APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.graph.nodes;

import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;

/**
 * It represents a context node used in a
 * {@link uk.ac.manchester.tornado.runtime.graph.TornadoVMGraphCompiler}.
 */
public class ContextNode extends AbstractNode {

    private int deviceIndex;
    private TornadoXPUDevice device;

    /**
     * It constructs a ContextNode with the given device index and
     * {@link TornadoXPUDevice}.
     *
     * @param index
     *     The index of the device.
     * @param device
     *     The {@link TornadoXPUDevice} associated with this context
     *     node.
     */
    public ContextNode(int index, TornadoXPUDevice device) {
        this.deviceIndex = index;
        this.device = device;
    }

    @Override
    public int compareTo(AbstractNode o) {
        if (!(o instanceof ContextNode)) {
            return -1;
        }

        return Integer.compare(deviceIndex, ((ContextNode) o).deviceIndex);
    }

    /**
     * It gets the device index associated with this context node.
     *
     * @return The device index.
     */
    public int getDeviceIndex() {
        return deviceIndex;
    }

    /**
     * It sets the device index associated with this context node.
     *
     * @param deviceIndex
     *     The device index to set.
     */
    public void setDeviceIndex(int deviceIndex) {
        this.deviceIndex = deviceIndex;
    }

    /**
     * It gets the {@link TornadoXPUDevice} associated with this context
     * node.
     *
     * @return {@link TornadoXPUDevice}
     */
    public TornadoXPUDevice getDevice() {
        return this.device;
    }

    /**
     * It sets the {@link TornadoXPUDevice} associated with this context
     * node.
     *
     * @param device
     *     The {@link TornadoXPUDevice} to set.
     */
    public void setDevice(TornadoXPUDevice device) {
        this.device = device;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("[%d]: context device=%d, [ ", id, deviceIndex));
        for (AbstractNode use : uses) {
            sb.append("").append(use.getId()).append(" ");
        }
        sb.append("]");
        return sb.toString();
    }
}
