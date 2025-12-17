pipeline {
    agent any
    options {
        timestamps()
        timeout(time: 240, unit: 'MINUTES')
    }

    parameters {
       booleanParam(name: 'fullBuild', defaultValue: false, description: 'Perform a full test across multiple JDKs')
    }

    environment {
        JDK_21_JAVA_HOME="/opt/jenkins/jdks/graal-23.1.0/jdk-21.0.3"
        GRAALVM_21_JAVA_HOME="/opt/jenkins/jdks/graal-23.1.0/graalvm-community-openjdk-21.0.1+12.1"
        MANDREL_21_JAVA_HOME="/opt/jenkins/jdks/graal-23.1.0/mandrel-java21-23.1.0.0-Final"
        ZULU_21_JAVA_HOME="/opt/jenkins/jdks/graal-23.1.0/zulu21.28.85-ca-jdk21.0.0-linux_x64"
        CORRETTO_21_JAVA_HOME="/opt/jenkins/jdks/graal-23.1.0/amazon-corretto-21.0.3.9.1-linux-x64"
        MICROSOFT_21_JAVA_HOME="/opt/jenkins/jdks/graal-23.1.0/jdk-21.0.3+9"
        TORNADO_ROOT="/var/lib/jenkins/workspace/TornadoVM-pipeline"
        PATH="/opt/maven/bin:/var/lib/jenkins/workspace/kfusion-tornadovm/bin:/var/lib/jenkins/workspace/TornadoVM-pipeline/bin/bin:$PATH"
        TORNADOVM_HOME="/var/lib/jenkins/workspace/TornadoVM-pipeline/bin/sdk"
        CMAKE_ROOT="/opt/jenkins/cmake-3.25.2-linux-x86_64"
        KFUSION_ROOT="/var/lib/jenkins/workspace/kfusion-tornadovm"
        TORNADO_RAY_TRACER_ROOT="/var/lib/jenkins/workspace/TornadoVM-Ray-Tracer"
        JAVAFX_SDK="/var/lib/jenkins/workspace/TornadoVM-Ray-Tracer/javafx-sdk-21.0.3/"
    }
    stages {
        stage('Prepare build') {
            steps {
                script {
                    if (params.fullBuild == true) {
                        runJDK21()
                        runGraalVM21()
                        runMandrelJDK21()
                        runZuluJDK21()
                        runCorrettoJDK21()
                        runMicrosoftJDK21()
                    } else {
                        Random rnd = new Random()
                        int NO_OF_JDKS = 6
                        switch (rnd.nextInt(NO_OF_JDKS)) {
                            case 0:
                                runJDK21()
                                break
                            case 1:
                                runGraalVM21()
                                break
                            case 2:
                                runMandrelJDK21()
                                break
                            case 3:
                                runZuluJDK21()
                                break
                            case 4:
                                runCorrettoJDK21()
                                break
                            case 5:
                                runMicrosoftJDK21()
                        }
                    }
                }
            }
        }

    }
    post {
        success {
            slackSend color: '#00CC00', message: "SUCCESSFUL: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})"
            deleteDir()
        }
       failure {
            slackSend color: '#CC0000', message: "FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})"
        }
    }
}

void runJDK21() {
    stage('OpenJDK 21') {
        withEnv(["JAVA_HOME=${JDK_21_JAVA_HOME}"]) {
            buildAndTest("OpenJDK 21", "jdk21")
            buildAndTestRayTracer("OpenJDK 21")
        }
    }
}


void runGraalVM21() {
    stage('GraalVM JDK 21') {
        withEnv(["JAVA_HOME=${GRAALVM_21_JAVA_HOME}"]) {
            buildAndTest("GraalVM JDK 21", "graal-jdk-21")
            buildAndTestRayTracer("GraalVM JDK 21")
        }
    }
}

void runMandrelJDK21() {
    stage('Red Hat Mandrel 21') {
        withEnv(["JAVA_HOME=${MANDREL_21_JAVA_HOME}"]) {
            buildAndTest("Red Hat Mandrel 21", "jdk21")
        }
    }
}

void runZuluJDK21() {
    stage('Azul Zulu JDK 21') {
        withEnv(["JAVA_HOME=${ZULU_21_JAVA_HOME}"]) {
            buildAndTest("Azul Zulu JDK 21", "jdk21")
            buildAndTestRayTracer("Azul Zulu JDK 21")
        }
    }
}

void runCorrettoJDK21() {
    stage('Amazon Corretto JDK 21') {
        withEnv(["JAVA_HOME=${CORRETTO_21_JAVA_HOME}"]) {
            buildAndTest("Amazon Corretto JDK 21", "jdk21")
            buildAndTestRayTracer("Amazon Corretto JDK 21")
        }
    }
}


void runMicrosoftJDK21() {
    stage('Microsoft JDK 21') {
        withEnv(["JAVA_HOME=${MICROSOFT_21_JAVA_HOME}"]) {
            buildAndTest("Microsoft JDK 21", "jdk21")
            buildAndTestRayTracer("Microsoft JDK 21")
        }
    }
}

void buildAndTest(String JDK, String tornadoProfile) {
    echo "-------------------------"
    echo "JDK used " + JDK
    echo "Tornado profile " + tornadoProfile
    echo "-------------------------"
    stage('Build with ' + JDK) {
        sh "make ${tornadoProfile} BACKEND=ptx,opencl,spirv"
    }
    stage('PTX: Unit Tests') {
        timeout(time: 45, unit: 'MINUTES') {
            sh 'tornado --devices'
            sh 'tornado-test --verbose -J"-Dtornado.unittests.device=0:0 -Dtornado.ptx.priority=100"'
            sh 'tornado-test -V  -J"-Dtornado.unittests.device=0:0 -Dtornado.device.memory=1MB -Dtornado.ptx.priority=100" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03'
            sh 'test-native.sh'
        }
    }
    stage("OpenCL: Unit Tests") {
        parallel (
            "OpenCL and GPU: Nvidia Quadro GP100" : {
                timeout(time: 45, unit: 'MINUTES') {
                    sh 'tornado --devices'
                    sh 'tornado-test --verbose -J"-Dtornado.ptx.priority=100 -Dtornado.unittests.device=2:0"'
                    sh 'tornado-test -V  -J" -Dtornado.ptx.priority=100 -Dtornado.unittests.device=2:0 -Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03'
                    sh 'test-native.sh'
                }
            },
            "OpenCL and Integrated GPU: Intel(R) UHD Graphics 630" : {
                timeout(time: 45, unit: 'MINUTES') {
                    sh 'tornado --devices'
                    sh 'tornado-test --verbose -J"-Dtornado.ptx.priority=100 -Dtornado.unittests.device=2:1"'
                    sh 'tornado-test -V  -J" -Dtornado.ptx.priority=100 -Dtornado.unittests.device=2:1 -Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03'
                    sh 'test-native.sh'
                }
            }
        )
    }
    stage("SPIR-V (OpenCL Runtime): Unit Tests") {
        timeout(time: 45, unit: 'MINUTES') {
            sh 'tornado --devices'
            sh 'tornado-test --verbose -J"-Dtornado.ptx.priority=100 -Dtornado.unittests.device=1:0 -Dtornado.spirv.dispatcher=opencl"'
            sh 'tornado-test -V  -J" -Dtornado.ptx.priority=100 -Dtornado.unittests.device=1:0 -Dtornado.spirv.dispatcher=opencl -Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03'
            sh 'test-native.sh'
        }
    }
    stage("SPIR-V (LevelZero Runtime): Unit Tests") {
            timeout(time: 45, unit: 'MINUTES') {
                sh 'tornado --devices'
                sh 'tornado-test --verbose -J"-Dtornado.ptx.priority=100 -Dtornado.unittests.device=1:1 -Dtornado.spirv.dispatcher=levelzero"'
                sh 'tornado-test -V  -J" -Dtornado.ptx.priority=100 -Dtornado.unittests.device=1:1 -Dtornado.spirv.dispatcher=levelzero -Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03'
                sh 'test-native.sh'
            }
        }
    stage('Benchmarks') {
        timeout(time: 15, unit: 'MINUTES') {
            sh 'python3 tornado-assembly/src/bin/tornado-benchmarks.py --printBenchmarks '
            sh 'python3 tornado-assembly/src/bin/tornado-benchmarks.py --medium --skipSerial --iterations 5 '
        }
    }
    stage('Clone & Build KFusion') {
        timeout(time: 5, unit: 'MINUTES') {
            sh 'cd ${KFUSION_ROOT} && git reset HEAD --hard && git fetch && git pull origin master && mvn clean install -DskipTests'
        }
    }
    stage('OpenCL: Run KFusion') {
        sleep 5
        timeout(time: 5, unit: 'MINUTES') {
            sh "cd ${KFUSION_ROOT} && sed -i 's/kfusion.tornado.backend=PTX/kfusion.tornado.backend=OpenCL/' conf/kfusion.settings"
            sh 'cd ${KFUSION_ROOT} && ./scripts/runOnlyOpenCL.sh'

        }
    }
    stage('PTX: Run KFusion') {
        sleep 5
        timeout(time: 5, unit: 'MINUTES') {
            sh "cd ${KFUSION_ROOT} && sed -i 's/kfusion.tornado.backend=OpenCL/kfusion.tornado.backend=PTX/' conf/kfusion.settings"
            sh 'cd ${KFUSION_ROOT} && ./scripts/runOnlyPTX.sh'
        }
    }
}

void buildAndTestRayTracer(String JDK) {
    stage('Clone & Build TornadoVM-Ray-Tracer') {
        timeout(time: 5, unit: 'MINUTES') {
            sh 'cd ${TORNADO_RAY_TRACER_ROOT} && git reset HEAD --hard && git fetch && git checkout master && git pull origin master && mvn clean install'
        }
    }
    stage('Run TornadoVM-Ray-Tracer') {
        sleep 5
        timeout(time: 5, unit: 'MINUTES') {
            sh 'cd ${TORNADO_RAY_TRACER_ROOT} && ./bin/tornadovm-ray-tracer regression'
        }
    }
}
