package tornado.common.exceptions;

import java.util.ArrayList;

public class TornadoUnsupportedError extends Error {

    private static final long serialVersionUID = 6639694094043791236L;

    private final ArrayList<String> context = new ArrayList<>();

    public static RuntimeException unsupported(String reason) {
        throw new TornadoUnsupportedError("unsupported: %s", reason);
    }

    public static RuntimeException unsupported(String reason, Object... args) {
        throw new TornadoUnsupportedError("unsupported: " + reason, args);
    }

    public TornadoUnsupportedError(String msg, Object... args) {
        super(String.format(msg, args));
    }

    public TornadoUnsupportedError(Throwable cause) {
        super(cause);
    }

    public TornadoUnsupportedError addContext(String newContext) {
        context.add(newContext);
        return this;
    }

    public TornadoUnsupportedError addContext(String name, Object obj) {
        return addContext(String.format("%s: %s", name, obj));
    }

}
