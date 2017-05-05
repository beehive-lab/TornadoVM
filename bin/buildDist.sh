#!/bin/bash

ROOT_DIR=$1
OUTPUT_FILE=$2
printf "packaging into %s (build in %s)\n" ${OUTPUT_FILE} ${ROOT_DIR}


BIN_FILES="bin/runBenchmarks.sh bin/convert2csv.sh"
DRIVER_FILES="drivers/opencl/jni-bindings"

if [ -e ${ROOT_DIR} ];then
	rm -f ${ROOT_DIR}
fi

mkdir -p ${ROOT_DIR}
mkdir -p ${ROOT_DIR}/{bin,etc,lib,drivers,var,share/examples,share/benchmarks}

for f in ${BIN_FILES};do
	cp -p ${f} ${ROOT_DIR}/bin/
done

find ${TORNADO_ROOT}/target -name "*.jar" -exec cp {} ${ROOT_DIR}/lib/ \;
cp -r ${DRIVER_FILES} ${ROOT_DIR}/drivers/opencl
cp -r ${TORNADO_ROOT}/examples/src/main/java ${ROOT_DIR}/share/examples
cp -r ${TORNADO_ROOT}/benchmarks/src/main/java ${ROOT_DIR}/share/benchmarks

cat > ${ROOT_DIR}/etc/tornado.env <<- EOF
#!/bin/bash

# need to be set to tornado's jvmci enabled OpenJDK build
export JAVA_HOME=""

if [ -z "\${JAVA_HOME}" ];then
	echo "tornado: JAVA_HOME needs to be set to an JVMCI enabled OpenJDK build"
fi

export TORNADO_ROOT=""
if [ -z "\${TORNADO_ROOT}" ];then
	echo "tornado: TORNADO_ROOT needs to be set"
fi

if [ ! -z "\${PATH}" ]; then
        export PATH="\${PATH}:\${TORNADO_ROOT}/bin"
else
        export PATH="\${TORNADO_ROOT}/bin"
fi
EOF

cat > ${ROOT_DIR}/etc/tornado.properties <<- EOF
tornado.kernels.coarsener = false
tornado.kernels.parallelise = true
tornado.opencl.schedule = true

EOF

cat > ${ROOT_DIR}/bin/tornado <<- EOF
#!/bin/bash

. \${TORNADO_ROOT}/etc/tornado.conf

DEPS=""
for f in \$(ls \${TORNADO_ROOT}/lib/*.jar); do
        DEPS="\${DEPS}:\${f}"
done

if [ -z "\${CLASSPATH}" ]; then
        CLASSPATH=\${DEPS}
else
        CLASSPATH="\${CLASSPATH}:\${DEPS}"
fi

CLASSPATH=\${CLASSPATH} \${JAVA_CMD} \${JAVA_FLAGS} \$@
EOF
chmod +x ${ROOT_DIR}/bin/*



cat > ${ROOT_DIR}/etc/tornado.conf <<- EOF
#!/bin/bash

TORNADO_FLAGS="-XX:-UseCompressedOops -Djava.library.path=\${TORNADO_ROOT}/drivers/opencl/lib"
JAVA_CMD=\${JAVA_HOME}/bin/java
JAVA_FLAGS="-server -XX:-UseJVMCIClassLoader \${TORNADO_FLAGS} -Dlog4j.configurationFile=\${TORNADO_ROOT}/etc/log4j2.xml"
EOF

cat > ${ROOT_DIR}/etc/log4j2.xml <<- EOF
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="INFO">
        <Appenders>
                <Console name="Console" target="SYSTEM_OUT">
                        <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
                </Console>
    <Async name="Async">
      <AppenderRef ref="Console"/>
    </Async>
        </Appenders>
        <Loggers>
                <Root level="warn">
                        <AppenderRef ref="Console"/>
                </Root>
                <Logger name="tornado" level="warn" additivity="false">
                        <AppenderRef ref="Console"/>
                </Logger>
        </Loggers>
</Configuration>
EOF

cat > ${ROOT_DIR}/README <<- EOF
Installing Tornado:
  1. Edit etc/tornado.conf
    - set JAVA_HOME to point to the JVMCI enable OpenJDK 
    - set TORNADO_ROOT to the location of this directory
  2. Compile the OpenCL drivers
    - cd drivers/opencl
    - autoreconf -f -i -s
    - ./configure --prefix=\${TORNADO_ROOT} --with-jdk=\${JAVA_HOME}
    - make clean && make && make install

Before running Tornado:
  1. configure environment
    - . etc/tornado.env

Running examples:
  - tornado tornado.examples.HelloWorld


EOF

sudo chown -R 0:0 ${ROOT_DIR}
pushd ${ROOT_DIR}/..
DIR=$(basename ${ROOT_DIR})
tar cfz ${OUTPUT_FILE} ${DIR} 
sudo rm -rf ./${DIR}
popd
