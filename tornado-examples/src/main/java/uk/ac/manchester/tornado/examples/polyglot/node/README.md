Simple example of GraalVM with Node.js invoking Java code expressed in TornadoVM.

Note: `$JAVA_HOME` must point to GraalVM jdk8 or jdk11

## 1) Setup:

```bash 
cd $TORNADO_ROOT && make
cd $TORNADO_ROOT/tornado-examples/src/main/java/uk/ac/manchester/tornado/examples/polyglot/node
export CLASSPATH="$TORNADO_ROOT/bin/sdk/share/java/tornado/tornado-api-0.15.2-dev.jar" 
javac.py Mandelbrot.java
```

Install the following modules: 

```bash
## Install Nodejs
$ $JAVA_HOME/bin/gu install nodejs

## Install dependencies
$ $JAVA_HOME/bin/npm install express
$ $JAVA_HOME/bin/npm install jimp
$ $JAVA_HOME/bin/npm install fs
```

## 2) Run node.js

```bash 
tornado --truffle node server.js
wget -q -O - http://localhost:3000/
```

Or access `http://localhost:3000/` 
