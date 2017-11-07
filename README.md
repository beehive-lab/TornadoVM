
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
export GRAAL_ROOT=<path to graal.jar and truffle-api.jar>
export TORNADO_ROOT=<path to cloned git dir>

export GRAAL_VERSION=0.22
export JVMCI_VERSION=1.8.0_131

if [ ! -z "${PATH}" ]; then
        export PATH="${PATH}:${TORNADO_ROOT}/bin"
else
        export PATH="${TORNADO_ROOT}/bin"
fi
```

## Installation Method 1

Once the file etc/tornado.env has been created, there are currently two methods for compiling and installing tornado.
Method 1 is fully automatic.

```bash
$ python easy-install.py
```

And done! 


## Installation Method 2

Alternative, you can compile each phase separately as follows:

```bash
$ python scripts/generatePom.py
$ . etc/tornado.env
$ mvn -DskipTests package
$ cd drivers/opencl/jni-bindings
$ autoreconf -f -i -s
$ ./configure --prefix=${PWD} --with-jdk=${JAVA_HOME}
$ make && make install
```

Complete!

# Running Examples #

```bash
$ . etc/tornado.env
$ tornado tornado.examples.HelloWorld
```

# Running Benchmarks #

```bash
$ tornado tornado.benchmarks.BenchmarkRunner tornado.benchmarks.sadd.Benchmark
```


# Running Unittests

To run all unittest in Tornado:

```bash
make tests 

```

To run a separated unittest class:

```bash
$  tornado-test.py tornado.unittests.TestHello
```

Also, it can be executed in verbose mode:

```bash
$ tornado-test.py --verbose tornado.unittests.TestHello
```

To test just a method of a unittest class:

```bash
$ tornado-test.py --verbose tornado.unittests.TestHello#helloWorld
```

