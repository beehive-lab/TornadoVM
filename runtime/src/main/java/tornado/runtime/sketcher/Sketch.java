package tornado.runtime.sketcher;

import com.oracle.graal.graph.CachedGraph;
import tornado.meta.Meta;

public class Sketch {

    private final CachedGraph graph;
    private final Meta meta;

    public Sketch(CachedGraph graph, Meta meta) {
        this.graph = graph;
        this.meta = meta;
    }

    public CachedGraph getGraph() {
        return graph;
    }

    public Meta getMeta() {
        return meta;
    }

}
