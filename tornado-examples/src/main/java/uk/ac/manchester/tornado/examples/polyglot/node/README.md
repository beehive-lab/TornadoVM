## NodeJS Example With Hardware Acceleration on GPUs

**Note:** This example has been tested on Apple OSx with M2 GPUs. 


This example demonstrates how to run a program in `node.js` that invokes Java code using the TornadoVM APIs. 


## How to run? 

`$JAVA_HOME` must point to GraalVM jdk11 and jdk17

### 1) Setup:

```bash 
cd $TORNADO_ROOT && maken graal-jdk-11-plus
cd $TORNADO_ROOT/tornado-examples/src/main/java/uk/ac/manchester/tornado/examples/polyglot/node
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

### 2) Run node.js

```bash 
tornado --truffle node server.js
wget -q -O - http://localhost:3000/   
```

Or access `http://localhost:3000/` for the accelerated version with TornadoVM and `http://localhost:3000/java` for running without TornadoVM. 


