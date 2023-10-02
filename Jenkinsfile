pipeline {
    agent any
    options {
        timestamps()
        timeout(time: 120, unit: 'MINUTES')
    }

    parameters {
       booleanParam(name: 'fullBuild', defaultValue: false, description: 'Perform a full test across multiple JDKs')
       string(name: 'fullBuild_branchToBuild', defaultValue: '**', description: 'Branch on which to perform the full build')
    }

    environment {
        ZULU_11_JAVA_HOME="/opt/jenkins/jdks/graal-22.3.2/zulu11.56.19-ca-jdk11.0.15-linux_x64"
        ZULU_17_JAVA_HOME="/opt/jenkins/jdks/graal-22.3.2/zulu17.34.19-ca-jdk17.0.3-linux_x64"
        CORRETTO_11_JAVA_HOME="/opt/jenkins/jdks/graal-22.3.2/amazon-corretto-11.0.15.9.1-linux-x64"
        JDK_17_JAVA_HOME="/opt/jenkins/jdks/graal-22.3.2/jdk-17.0.1"
        GRAALVM_11_JAVA_HOME="/opt/jenkins/jdks/graal-22.3.2/graalvm-ce-java11-22.3.2"
        GRAALVM_17_JAVA_HOME="/opt/jenkins/jdks/graal-22.3.2/graalvm-ce-java17-22.3.2"
        TORNADO_ROOT="/var/lib/jenkins/workspace/Tornado-pipeline"
        PATH="/var/lib/jenkins/workspace/Slambench/slambench-tornado-refactor/bin:/var/lib/jenkins/workspace/Tornado-pipeline/bin/bin:$PATH"    
        TORNADO_SDK="/var/lib/jenkins/workspace/Tornado-pipeline/bin/sdk" 
        CMAKE_ROOT="/opt/jenkins/cmake-3.25.2-linux-x86_64"
        KFUSION_ROOT="/var/lib/jenkins/workspace/Slambench/slambench-tornado-refactor"
        TORNADO_RAY_TRACER_ROOT="/var/lib/jenkins/workspace/TornadoVM-Ray-Tracer"
        JAVAFX_SDK="/var/lib/jenkins/workspace/TornadoVM-Ray-Tracer/javafx-sdk-18.0.1/"
    }
    stages {
        stage('Checkout Current Branch') {
            steps {
                step([$class: 'WsCleanup'])
                checkout([$class: 'GitSCM', branches: [[name: params.fullBuild_branchToBuild]], doGenerateSubmoduleConfigurations: false, extensions:[[$class: 'LocalBranch']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '9bca499b-bd08-4fb2-9762-12105b44890e', url: 'https://github.com/beehive-lab/TornadoVM-Internal.git']]])
            }
        }

        stage('Prepare build') {
            steps {
                script {
                    if (params.fullBuild == true) {
                        runZuluJDK11()
                        runZuluJDK17()
                        runJDK17()
                        runGraalVM17()
                        runGraalVM11()
                        runCorrettoJDK11()
                    } else {
                        Random rnd = new Random()
                        int NO_OF_JDKS = 6
                        switch (rnd.nextInt(NO_OF_JDKS)) {
                            case 0:
                                runZuluJDK11()
                                break
                            case 1:
                                runZuluJDK17()
                                break
                            case 2:
                                runJDK17()
                                break
                            case 3:
                                runGraalVM11()
                                break
                            case 4:
                                runGraalVM17()
                                break
                            case 5:
                                runCorrettoJDK11()
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

void runZuluJDK11() {
    stage('Zulu JDK 11') {
        withEnv(["JAVA_HOME=${ZULU_11_JAVA_HOME}"]) {
            buildAndTest("Zulu JDK 11", "jdk-11-plus")
        }
    }
}

void runZuluJDK17() {
    stage('Zulu JDK 17') {
        withEnv(["JAVA_HOME=${ZULU_17_JAVA_HOME}"]) {
            buildAndTest("Zulu JDK 17", "jdk-11-plus")
        }
    }
}

void runCorrettoJDK11() {
    stage('Corretto JDK 11') {
        withEnv(["JAVA_HOME=${CORRETTO_11_JAVA_HOME}"]) {
            buildAndTest("Corretto JDK 11", "jdk-11-plus")
            buildAndTestRayTracer("Corretto JDK 11")
        }
    }
}

void runJDK17() {
    stage('JDK 17') {
        withEnv(["JAVA_HOME=${JDK_17_JAVA_HOME}"]) {
            buildAndTest("JDK 17", "jdk-11-plus")
        }
    }
}

void runGraalVM11() {
    stage('GraalVM 11') {
        withEnv(["JAVA_HOME=${GRAALVM_11_JAVA_HOME}"]) {
            buildAndTest("GraalVM JDK 11", "graal-jdk-11-plus")
            buildAndTestRayTracer("GraalVM JDK 11")
        }
    }
}

void runGraalVM17() {
    stage('GraalVM 17') {
        withEnv(["JAVA_HOME=${GRAALVM_17_JAVA_HOME}"]) {
            buildAndTest("GraalVM JDK 17", "graal-jdk-11-plus")
        }
    }
}

void buildAndTest(String JDK, String tornadoProfile) {
    echo "-------------------------"
    echo "JDK used " + JDK
    echo "Tornado profile " + tornadoProfile
    echo "-------------------------"
    stage('Build with ' + JDK) {
        sh "make ${tornadoProfile} BACKEND=ptx,opencl"
    }
    stage('PTX: Unit Tests') {
        timeout(time: 12, unit: 'MINUTES') {
            sh 'tornado --devices'
            sh 'tornado-test --verbose -J"-Dtornado.unittests.device=0:0 -Dtornado.ptx.priority=100"'
            sh 'tornado-test -V  -J"-Dtornado.unittests.device=0:0 -Dtornado.device.memory=1MB -Dtornado.ptx.priority=100" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03'
            sh 'test-native.sh'
        }
    }
    stage("OpenCL: Unit Tests") {
        parallel (
            "OpenCL and GPU: Nvidia GeForce GTX 1060" : {
                timeout(time: 12, unit: 'MINUTES') {
                    sh 'tornado --devices'
                    sh 'tornado-test --verbose -J"-Dtornado.ptx.priority=100 -Dtornado.unittests.device=1:1"'
                    sh 'tornado-test -V  -J" -Dtornado.ptx.priority=100 -Dtornado.unittests.device=1:1 -Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03'
                    sh 'test-native.sh'
                }
            },
            "OpenCL and CPU: Intel Xeon E5-2620" : {
                timeout(time: 12, unit: 'MINUTES') {
                    sh 'tornado --devices'
                    sh 'tornado-test --verbose -J"-Dtornado.ptx.priority=100 -Dtornado.unittests.device=1:0"'
                    sh 'tornado-test -V  -J" -Dtornado.ptx.priority=100 -Dtornado.unittests.device=1:0 -Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03'
                    sh 'test-native.sh'
                }
            }
        )
    }
    stage('Benchmarks') {
        timeout(time: 15, unit: 'MINUTES') {
            sh 'python3 tornado-assembly/src/bin/tornado-benchmarks.py --printBenchmarks '
            sh 'python3 tornado-assembly/src/bin/tornado-benchmarks.py --medium --skipSequential --iterations 5 '
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
            sh 'cd ${KFUSION_ROOT} && ./scripts/run.sh'

        }
    }
    stage('PTX: Run KFusion') {
        sleep 5
        timeout(time: 5, unit: 'MINUTES') {
            sh "cd ${KFUSION_ROOT} && sed -i 's/kfusion.tornado.backend=OpenCL/kfusion.tornado.backend=PTX/' conf/kfusion.settings"
            sh 'cd ${KFUSION_ROOT} && ./scripts/run.sh'
        }
    }
}

void buildAndTestRayTracer(String JDK) {
    stage('Clone & Build TornadoVM-Ray-Tracer') {
        if (JDK == 'JDK 8') {
            echo 'TornadoVM-Ray-Tracer builds for JDK > 11'
        } else {
            timeout(time: 5, unit: 'MINUTES') {
                sh 'cd ${TORNADO_RAY_TRACER_ROOT} && git reset HEAD --hard && git fetch && git checkout master && git pull origin master && mvn clean install'
            }
        }
    }
    stage('Run TornadoVM-Ray-Tracer') {
        sleep 5
        if (JDK == 'JDK 8') {
            echo 'TornadoVM-Ray-Tracer builds for JDK > 11'
        } else {
            timeout(time: 5, unit: 'MINUTES') {
                sh 'cd ${TORNADO_RAY_TRACER_ROOT} && ./bin/tornadovm-ray-tracer regression'
            }
        }
    }
}
