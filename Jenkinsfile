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
        JDK_8_JAVA_HOME="/opt/jenkins/jdks/openjdk1.8.0_302-jvmci-21.3-b05"
        CORRETTO_11_JAVA_HOME="/opt/jenkins/jdks/amazon-corretto-11.0.13.8.1-linux-x64"
        JDK_17_JAVA_HOME="/opt/jenkins/jdks/jdk-17.0.1+12"
        GRAALVM_11_JAVA_HOME="/opt/jenkins/jdks/graalvm-ce-java11-21.3.0"
        GRAALVM_17_JAVA_HOME="/opt/jenkins/jdks/graalvm-ce-java17-21.3.0"
        TORNADO_ROOT="/var/lib/jenkins/workspace/Tornado-pipeline"
        PATH="/var/lib/jenkins/workspace/Slambench/slambench-tornado-refactor/bin:/var/lib/jenkins/workspace/Tornado-pipeline/bin/bin:$PATH"    
        TORNADO_SDK="/var/lib/jenkins/workspace/Tornado-pipeline/bin/sdk" 
        CMAKE_ROOT="/opt/jenkins/cmake-3.10.2-Linux-x86_64"
        KFUSION_ROOT="/var/lib/jenkins/workspace/Slambench/slambench-tornado-refactor"
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
                        runJDK8()
                        runCorrettoJDK11()
                        runJDK17()
                        runGraalVM11()
                        runGraalVM17()
                    } else {
                        Random rnd = new Random()
                        int NO_OF_JDKS = 5
                        switch (rnd.nextInt(NO_OF_JDKS)) {
                            case 0:
                                runJDK8()
                                break
                            case 1:
                                runCorrettoJDK11()
                                break
                            case 2:
                                runGraalVM11()
                                break
                            case 3:
                                runGraalVM17()
                                break
                            case 4:
                                runJDK17()
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

void runJDK8() {
    stage('JDK 8') {
        withEnv(["JAVA_HOME=${JDK_8_JAVA_HOME}"]) {
            buildAndTest("JDK 8", "jdk-8")
        }
    }
}

void runCorrettoJDK11() {
    stage('Corretto JDK 11') {
        withEnv(["JAVA_HOME=${CORRETTO_11_JAVA_HOME}"]) {
            buildAndTest("Corretto JDK 11", "jdk-11-plus")
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
            sh 'tornado-test.py --verbose -J"-Dtornado.unittests.device=0:0 -Dtornado.ptx.priority=100"'
            sh 'tornado-test.py -V  -J"-Dtornado.unittests.device=0:0 -Dtornado.heap.allocation=1MB -Dtornado.ptx.priority=100" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03'
            sh 'test-native.sh'
        }
    }
    stage("OpenCL: Unit Tests") {
        parallel (
            "OpenCL and GPU: Nvidia GeForce GTX 1060" : {
                timeout(time: 12, unit: 'MINUTES') {
                    sh 'tornado --devices'
                    sh 'tornado-test.py --verbose -J"-Dtornado.ptx.priority=100 -Dtornado.unittests.device=1:1"'
                    sh 'tornado-test.py -V  -J" -Dtornado.ptx.priority=100 -Dtornado.unittests.device=1:1 -Dtornado.heap.allocation=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03'
                    sh 'test-native.sh'
                }
            },
            "OpenCL and CPU: Intel Xeon E5-2620" : {
                timeout(time: 12, unit: 'MINUTES') {
                    sh 'tornado --devices'
                    sh 'tornado-test.py --verbose -J"-Dtornado.ptx.priority=100 -Dtornado.unittests.device=1:0"'
                    sh 'tornado-test.py -V  -J" -Dtornado.ptx.priority=100 -Dtornado.unittests.device=1:0 -Dtornado.heap.allocation=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03'
                    sh 'test-native.sh'
                }
            }
        )
    }
    stage('Benchmarks') {
        timeout(time: 15, unit: 'MINUTES') {
            sh 'python assembly/src/bin/tornado-benchmarks.py --printBenchmarks '
            sh 'python assembly/src/bin/tornado-benchmarks.py --medium --skipSequential --iterations 5 '
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
            sh 'cd ${KFUSION_ROOT} && kfusion kfusion.tornado.Benchmark ${KFUSION_ROOT}/conf/traj2.settings'
        }
    }
    stage('PTX: Run KFusion') {
        sleep 5
        timeout(time: 5, unit: 'MINUTES') {
            sh "cd ${KFUSION_ROOT} && sed -i 's/kfusion.tornado.backend=OpenCL/kfusion.tornado.backend=PTX/' conf/kfusion.settings"
            sh 'cd ${KFUSION_ROOT} && kfusion kfusion.tornado.Benchmark ${KFUSION_ROOT}/conf/traj2.settings'
        }
    }
}
