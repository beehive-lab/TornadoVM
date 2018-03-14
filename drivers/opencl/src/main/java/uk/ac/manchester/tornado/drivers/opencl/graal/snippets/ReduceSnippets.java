package uk.ac.manchester.tornado.drivers.opencl.graal.snippets;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.replacements.Snippets;

public class ReduceSnippets implements Snippets {

    /**
     * Dummy snippet to understand the process of node replacements using
     * snippet during lowering.
     * 
     */
    @Snippet
    public static int hello(int n) {
        int value = 0;
        for (int i = 0; i < n; i++) {
            value += i;
        }
        return value;
    }
}
