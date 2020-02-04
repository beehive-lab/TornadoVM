Simple example of Graal Node calling into Java which uses tornado.

Note: $JAVA_HOME must point to GraalVM jdk8 or jdk11

Setup:

```bash 
cd $TORNADO_ROOT && make
cd $TORNADO_ROOT/examples/src/main/java/uk/ac/manchester/tornado/examples/polyglot/node
javac.py Compute.java
```

Install express for the node server: `$JAVA_HOME/bin/npm install express`

Run node js

```bash 
bash node.sh server.js
wget -q -O - http://localhost:3000/
```

node.sh sets the classpath/modulepath for the underlying graal jvm. It supports the same parameters as tornado.sh
