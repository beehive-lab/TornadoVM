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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractNode implements Comparable<AbstractNode> {

    protected int id;

    protected final List<AbstractNode> uses;

    public AbstractNode() {
        id = -1;
        uses = new ArrayList<>();
    }

    public boolean hasInputs() {
        return false;
    }

    public List<AbstractNode> getInputs() {
        return Collections.emptyList();
    }

    public void addUse(AbstractNode use) {
        if (!uses.contains(use)) {
            uses.add(use);
        }
    }

    @Override
    public int compareTo(AbstractNode o) {
        if (o == null) {
            return -1;
        }

        return (this == o) ? 0 : 1;
    }

    public int getId() {
        return id;
    }

    public List<AbstractNode> getUses() {
        return uses;
    }

    public void replaceAtUses(AbstractNode toReplace, AbstractNode replacement) {
        uses.remove(toReplace);
        uses.add(replacement);
    }

    public void setId(int value) {
        id = value;
    }

    @Override
    public String toString() {
        return String.format("[%d]: %s", id, this.getClass().getSimpleName());
    }
}
