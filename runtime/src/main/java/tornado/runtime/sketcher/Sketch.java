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
package tornado.runtime.sketcher;

import com.oracle.graal.graph.CachedGraph;
import tornado.api.meta.TaskMetaData;

public class Sketch {

    private final CachedGraph graph;
    private final TaskMetaData meta;

    public Sketch(CachedGraph graph, TaskMetaData meta) {
        this.graph = graph;
        this.meta = meta;
    }

    public CachedGraph getGraph() {
        return graph;
    }

    public TaskMetaData getMeta() {
        return meta;
    }

}
