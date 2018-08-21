package uk.ac.manchester.tornado.api.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import uk.ac.manchester.tornado.api.AbstractTaskGraph;

public class Loader {

    public static AbstractTaskGraph loadRuntime(String name) {
        AbstractTaskGraph taskGraphImpl = null;
        try {
            String tornadoAPIimplementation = System.getProperty("tornado.load.implementation");
            Class<?> klass = Class.forName(tornadoAPIimplementation);
            Constructor<?> constructor = klass.getConstructor(String.class);
            taskGraphImpl = (AbstractTaskGraph) constructor.newInstance(name);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | SecurityException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException("[ERROR] Tornado API Implementation class not found");
        }
        return taskGraphImpl;
    }
}
