package uk.ac.manchester.tornado.api.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import uk.ac.manchester.tornado.api.AbstractTaskGraph;
import uk.ac.manchester.tornado.api.runtinface.TornadoRuntimeInterface;

public class TornadoAPIProvider {

    public static AbstractTaskGraph loadScheduleRuntime(String name) {
        AbstractTaskGraph taskGraphImpl = null;
        try {
            String tornadoAPIimplementation = System.getProperty("tornado.load.api.implementation");
            Class<?> klass = Class.forName(tornadoAPIimplementation);
            Constructor<?> constructor = klass.getConstructor(String.class);
            taskGraphImpl = (AbstractTaskGraph) constructor.newInstance(name);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | SecurityException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException("[ERROR] Tornado API Implementation class not found");
        }
        return taskGraphImpl;
    }

    public static TornadoRuntimeInterface loadRuntime() {
        TornadoRuntimeInterface runtime = null;
        try {
            String tornadoRuntimeimplementation = System.getProperty("tornado.load.runtime.implementation");
            Class<?> klass = Class.forName(tornadoRuntimeimplementation);
            Method method = klass.getDeclaredMethod("getTornadoRuntime");
            runtime = (TornadoRuntimeInterface) method.invoke(null);
        } catch (ClassNotFoundException | IllegalAccessException | NoSuchMethodException | SecurityException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException("[ERROR] Tornado API Implementation class not found");
        }
        return runtime;
    }

}
