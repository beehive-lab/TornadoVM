import uk.ac.manchester.tornado.tc.runtime.*;
import uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation;

/** One cold TC compile in a fresh JVM: total wall time and the per-stage split TC records. */
public class Cold {
    public static void main(String[] a) throws Exception {
        long t0 = System.nanoTime();
        var root = java.nio.file.Path.of(System.getProperty("user.home"), "tornadovm-compiler");
        var urls = new java.net.URL[] { root.resolve("tornado-benchmarks/target/classes").toUri().toURL(), root.resolve("tornado-api/target/classes").toUri().toURL() };
        var loader = new java.net.URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
        var source = uk.ac.manchester.tornado.tc.frontend.Frontend.BytecodeSource.classLoader(loader);
        int runs = a.length > 2 ? Integer.parseInt(a[2]) : 1;
        for (int r = 0; r < runs; r++) {
            long s = System.nanoTime();
            var c = TcCompiler.compileJava(source, a[0], a[1], uk.ac.manchester.tornado.tc.ir.target.TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, java.util.List.of());
            long e = System.nanoTime();
            if (r == 0 || r == runs - 1) {
                var sb = new StringBuilder();
                for (var st : c.stages()) sb.append(st.name()).append('=').append(String.format("%.2f", st.nanos() / 1e6)).append(' ');
                System.out.printf("run %d ok=%s wall=%.2f ms tc=%.2f ms | %s%n", r, c.succeeded(), (e - s) / 1e6, c.nanos() / 1e6, sb);
            }
        }
        System.out.printf("jvm-to-done %.1f ms%n", (System.nanoTime() - t0) / 1e6);
    }
}
