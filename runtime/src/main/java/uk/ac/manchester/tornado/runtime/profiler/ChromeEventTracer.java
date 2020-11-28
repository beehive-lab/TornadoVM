package uk.ac.manchester.tornado.runtime.profiler;
import java.io.*;
import java.util.Map;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class ChromeEventTracer {
    
    public static ChromeEventTracer create(){
            return new ChromeEventTracer();
        }
    static public final ChromeEventJSonWriter json = new ChromeEventJSonWriter();
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            json.write(new File(TornadoOptions.getChromeEventTracerFileName()));
        }));
    }
    
    static public boolean isEnabled(){
        return TornadoOptions.isChromeEventTracerEnabled();
    }
    static public void enqueueWriteIfEnabled(String tag, long bytes, long startNs, long endNs) {
        if (isEnabled()) {
            json.x(tag, "write", startNs, endNs, ()->{
                json.kv("bytes", bytes);
            });
        }
    }
    static public void enqueueReadIfEnabled(String tag, long bytes, long startNs, long endNs) {
        if (isEnabled()) {
            json.x(tag, "read",   startNs, endNs, ()->{
                json.kv("bytes", bytes);
            });
        }
    }
    static public void enqueueNDRangeKernelIfEnabled(String tag, long startNs, long endNs) {
        if (isEnabled()) {
            json.x(tag, "NDRangeKernel", startNs, endNs, null);
        }
    }
    static public void enqueueTaskIfEnabled(String tag, long startNs, long endNs) {
        if (isEnabled()) {
            json.x(tag, "exec", startNs, endNs, null);
        }
    }
    static public void trace(String tag, Runnable r) {
        long startNs = System.nanoTime();
        r.run();
        if (isEnabled()) {
            json.x(tag, "trace", startNs, System.nanoTime(), null);
        }
    }

    public interface Builder<T>{
        T build();
    }

     static public <T> T trace(String tag, Builder<T> b) {
        long startNs = System.nanoTime();
        T value = b.build();
        if (isEnabled()) {
            json.x(tag, "trace", startNs, System.nanoTime(), null);
        }
        return value;
    }
    public ChromeEventTracer() {

    }

    static public void opencltimes(int localId, long queuedNs,  long submitNs, long startNs, long endNs, Map<String, ?> meta){
        json.x("queued", null, queuedNs, endNs, meta ==null?null: ()->{
            for (String k:meta.keySet()){
                json.kv(k, (String)meta.get(k));
            }
        });
        json.x("submit", null, submitNs, endNs, null);
        json.x("start", null, startNs, endNs, null);
        // order queue submit start end
    }
}

