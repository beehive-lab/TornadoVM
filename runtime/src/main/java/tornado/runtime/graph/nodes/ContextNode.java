/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
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
 *
 * Authors: James Clarkson
 *
 */
package tornado.runtime.graph.nodes;

public class ContextNode extends AbstractNode {

    private final int deviceIndex;

    public ContextNode(int index) {
        deviceIndex = index;
    }

    @Override
    public int compareTo(AbstractNode o) {
        if (!(o instanceof ContextNode)) {
            return -1;
        }

        return Integer.compare(deviceIndex, ((ContextNode) o).deviceIndex);
    }

    public int getIndex() {
        return deviceIndex;
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
