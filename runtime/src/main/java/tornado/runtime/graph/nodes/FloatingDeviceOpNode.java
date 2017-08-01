/*
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.runtime.graph.nodes;

import tornado.common.TornadoDevice.BlockingMode;

import static tornado.common.TornadoDevice.BlockingMode.NON_BLOCKING;

public abstract class FloatingDeviceOpNode extends AbstractNode {

    private final DeviceNode device;
    private BlockingMode blocking;

    public FloatingDeviceOpNode(DeviceNode device) {
        this.device = device;
        this.blocking = NON_BLOCKING;
        device.addUse(this);
    }

    public DeviceNode getDevice() {
        return device;
    }

    public BlockingMode getBlocking() {
        return blocking;
    }

    public void setBlocking(BlockingMode value) {
        blocking = value;
    }
}
