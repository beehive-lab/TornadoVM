/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.kotlin.benchmarks

import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider
import uk.ac.manchester.tornado.benchmarks.BenchmarkRunner

/**
 * Entry point of the Kotlin benchmarks, the counterpart of the Java BenchmarkRunner:
 *
 * ```
 * tornado -m tornado.kotlin.benchmarks/uk.ac.manchester.tornado.kotlin.benchmarks.KotlinBenchmarkRunner saxpy [iterations size]
 * ```
 *
 * With `-Dtornado.kotlin.benchmarks.compareJava=True` the Java benchmark of the same name runs
 * afterwards with the same arguments, so the Kotlin (`bm=kotlin-<name>-...`) and Java
 * (`bm=<name>-...`) results are printed together.
 */
object KotlinBenchmarkRunner {

    private val KOTLIN_BENCHMARKS: Map<String, () -> BenchmarkRunner> = linkedMapOf(
        "saxpy" to ::saxpyBenchmark,
        "sgemm" to ::sgemmBenchmark,
        "blackscholes" to ::blackscholesBenchmark,
        "nbody" to ::nbodyBenchmark,
        "mandelbrot" to ::mandelbrotBenchmark,
        "dft" to ::dftBenchmark,
        "montecarlo" to ::monteCarloBenchmark,
    )

    private val JAVA_BENCHMARKS: Map<String, () -> BenchmarkRunner> = mapOf(
        "saxpy" to { uk.ac.manchester.tornado.benchmarks.saxpy.Benchmark() },
        "sgemm" to { uk.ac.manchester.tornado.benchmarks.sgemm.Benchmark() },
        "blackscholes" to { uk.ac.manchester.tornado.benchmarks.blackscholes.Benchmark() },
        "nbody" to { uk.ac.manchester.tornado.benchmarks.nbody.Benchmark() },
        "mandelbrot" to { uk.ac.manchester.tornado.benchmarks.mandelbrot.Benchmark() },
        "dft" to { uk.ac.manchester.tornado.benchmarks.dft.Benchmark() },
        "montecarlo" to { uk.ac.manchester.tornado.benchmarks.montecarlo.Benchmark() },
    )

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty() || args[0].lowercase() !in KOTLIN_BENCHMARKS) {
            println("[ERROR] Provide a Kotlin benchmark to run: ${KOTLIN_BENCHMARKS.keys.joinToString()}")
            println("Example: tornado -m tornado.kotlin.benchmarks/${KotlinBenchmarkRunner::class.java.name} saxpy 101 16777216")
            return
        }
        val name = args[0].lowercase()
        val benchmarkArgs = args.copyOfRange(1, args.size)
        if (System.getProperty("config") != null) {
            TornadoRuntimeProvider.loadSettings(System.getProperty("config"))
        }

        run(KOTLIN_BENCHMARKS.getValue(name)(), benchmarkArgs)

        if (System.getProperty("tornado.kotlin.benchmarks.compareJava", "False").toBoolean()) {
            // Java benchmarks that take `iterations width height` get a square problem of the same size.
            val javaArgs = if (name == "sgemm" && benchmarkArgs.size == 2) benchmarkArgs + benchmarkArgs[1] else benchmarkArgs
            run(JAVA_BENCHMARKS.getValue(name)(), javaArgs)
        }
    }

    private fun run(benchmark: BenchmarkRunner, args: Array<String>) {
        benchmark.parseArgs(args)
        benchmark.run()
    }
}
