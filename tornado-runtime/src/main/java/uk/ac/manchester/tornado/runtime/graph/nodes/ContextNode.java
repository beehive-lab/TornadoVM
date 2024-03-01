/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2023 APT Group, Department of Computer Science,
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
