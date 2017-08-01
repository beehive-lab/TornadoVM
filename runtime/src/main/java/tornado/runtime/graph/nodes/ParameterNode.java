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

public class ParameterNode extends AbstractNode {

    private int index;

    public ParameterNode(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public int compareTo(AbstractNode o) {
        if (!(o instanceof ParameterNode)) {
            return -1;
        }
        return Integer.compare(index, ((ParameterNode) o).index);
    }

    @Override
    public String toString() {
        return String.format("[%d]: object %d", id, index);
    }

}
