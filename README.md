
# Installing Tornado #

```bash
 $ git clone https://github.com/beehive-lab/tornado.git tornado
 $ cd tornado
 $ vim etc/tornado.env
```

Copy and paste the following - but update paths into the etc/tornado.env file:

```bash
#!/bin/bash
export JAVA_HOME=<path to jvmci 8 jdk with JVMCI>
export GRAAL_ROOT=<path to graal.jar>
export TORNADO_ROOT=<path to cloned git dir>

export GRAAL_VERSION=0.22
export JVMCI_VERSION=1.8.0_131

if [ ! -z "${PATH}" ]; then
        export PATH="${PATH}:${TORNADO_ROOT}/bin"
else
        export PATH="${TORNADO_ROOT}/bin"
fi
```

Then set the enviroment variables and compile as follows:

```bash
$ . etc/tornado.env
$ mvn -DskipTests package
$ cd drivers/opencl/jni-bindings
$ autoreconf -f -i -s
$ ./configure --prefix=${PWD} --with-jdk=${JAVA_HOME}
$ make && make install
```

Complete!

# Running Examples #

  [Optional]

```bash
$ . etc/tornado.env
$ tornado tornado.examples.HelloWorld
```

# Running Benchmarks #

```bash
$ tornado tornado.benchmarks.BenchmarkRunner tornado.benchmarks.sadd.Benchmark
```


