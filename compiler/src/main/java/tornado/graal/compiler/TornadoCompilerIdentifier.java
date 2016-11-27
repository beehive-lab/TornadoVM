package tornado.graal.compiler;

import com.oracle.graal.compiler.common.CompilationIdentifier;

public class TornadoCompilerIdentifier implements CompilationIdentifier {

    private final int id;
    private final String name;

    public TornadoCompilerIdentifier(String name, int id) {
        this.name = name;
        this.id = id;
    }

    @Override
    public String toString(Verbosity verbosity) {
        return name + "-" + id;
    }

}
