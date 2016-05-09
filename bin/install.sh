#!/bin/bash

TORNADO_URL=https://${USER}@bitbucket.org/clarksoj/tornado.git
TORNADO_ROOT=$1

# 1. Obtain Tornado from bitbucket
git clone ${TORNADO_URL} ${TORNADO_ROOT}
pushd ${TORNADO_ROOT}

# get the absolute path
TORNADO_ROOT=${PWD}

# 2. Obtain graal
hg clone http://hg.openjdk.java.net/graal/graal-compiler graal
pushd graal
hg up -r 37f65dc8c713

# Setup mercurial
if [ ! -e "${HOME}/.hgrc" ]; then
cat > ${HOME}/.hgrc <<- EOF
[ui]
username = ${USER}
[extensions]
mq =
EOF
fi

# Create patch queues and patch the graal source
hg qinit
cp -r ../graal-patches/* .hg/patches
hg qpush -a

# Configure GRAAL in server mode
echo "DEFAULT_VM=server"  > ./mx/env
./mx.sh build

JAVA_VERSION=$(find . -name jdk* -exec basename {} \;)
JAVA_HOME=${TORNADO_ROOT}/graal/${JAVA_VERSION}/product
popd

# 3. Write the configuration to tornado.env
mkdir etc
touch etc/tornado.env
cat > etc/tornado.env <<- EOF
export TORNADO_ROOT=${TORNADO_ROOT}
export JAVA_HOME=${JAVA_HOME}
export PATH=\${PATH}:\${TORNADO_ROOT}/bin
EOF

# 4. Build the Tornado drivers (JNI interface to OpenCL)
pushd drivers/opencl/jni-bindings
autoreconf
./configure --prefix=${PWD} --with-jdk=${JAVA_HOME}
make
make install

popd

# 5. Configure and build Tornado

mvn clean install -U
