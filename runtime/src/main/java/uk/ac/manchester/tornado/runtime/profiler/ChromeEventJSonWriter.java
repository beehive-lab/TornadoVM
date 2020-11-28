package uk.ac.manchester.tornado.runtime.profiler;

import sun.management.VMManagement;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

public class ChromeEventJSonWriter extends JSonWriter<ChromeEventJSonWriter> {
    private int pid = 0;
    ContentWriter NO_ARGS = null;
    ChromeEventJSonWriter() {
        super();
        // Java 9 we could use long pid = ProcessHandle.current().pid();
        // For java 8 we must go through some hoops.
        try {
            RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
            Field jvm = runtime.getClass().getDeclaredField("jvm");
            jvm.setAccessible(true);
            VMManagement mgmt = (VMManagement) jvm.get(runtime);
            java.lang.reflect.Method pid_method = mgmt.getClass().getDeclaredMethod("getProcessId");
            pid_method.setAccessible(true);
            pid = (Integer) pid_method.invoke(mgmt);
        } catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException | InvocationTargetException nsf) {
            // well we tried.  Lets just use the default 0
        }
        objectStart();
        arrayStart("traceEvents");
        object(()->{
            object("args", ()-> {
                kv("name", "Tornado");
            });
            kv("ph", "M");
            pidAndTid();
            kv("name", "tornadovm");
            kv("sort_index", 1);
        });
    }


    JSonWriter pidAndTid() {
        return kv("pid", pid).kv("tid", Thread.currentThread().getId());
    }

    JSonWriter common(String phase, String name, String category ) {
        return kv("ph", phase).kv("name", name). kv("cat", category).pidAndTid();
    }

    public JSonWriter x(String name, String category, long startNs, long endNs, ContentWriter cw) {
       return  compact().object(()->{
            common("X", name, category);
            ns("ts", startNs);
            nsd("dur", endNs - startNs);
            if (cw!= NO_ARGS) {
                object("args", ()->{
                    nonCompact();
                    cw.write();
                });
            }else {
                nonCompact();
            }
        });
    }

    JSonWriter b(String name, String category, long startNs) {
        return common("B", name, category).ns("ts", startNs);
    }

    JSonWriter e(String name, long durationNs) {
        return kv("ph", "E").kv("name", name).pidAndTid().ns("ts", durationNs);
    }


    @Override
    void write(File file) {
        arrayEnd().objectEnd();
        super.write(file);
    }
}
