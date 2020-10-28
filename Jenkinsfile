pipeline {
    agent any
    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }
    environment {
        JDK_8_JAVA_HOME="/opt/jenkins/openjdk1.8.0_262-jvmci-20.2-b03"
        CORRETTO_11_JAVA_HOME="/opt/jenkins/amazon-corretto-11.0.9.11.1-linux-x64"
        GRAALVM_8_JAVA_HOME="/opt/jenkins/graalvm-ce-java8-20.2.0"
        GRAALVM_11_JAVA_HOME="/opt/jenkins/graalvm-ce-java11-20.2.0"
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
                checkout scm
                sh 'git checkout master'
                checkout([$class: 'GitSCM', branches: [[name: '**']], doGenerateSubmoduleConfigurations: false, extensions:[[$class: 'LocalBranch']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '9bca499b-bd08-4fb2-9762-12105b44890e', url: 'https://github.com/beehive-lab/TornadoVM-Internal.git']]])
            }
        }
        stage('JDK 8') {
            environment {
                JAVA_HOME="${JDK_8_JAVA_HOME}"
            }
            steps {
                buildAndTest("JDK 8", "jdk-8")
            }
        }
        stage('Corretto JDK 11') {
            environment {
                JAVA_HOME="${CORRETTO_11_JAVA_HOME}"
            }
            steps {
                buildAndTest("Corretto JDK 11", "jdk-11-plus")
            }
        }
        stage('GraalVM 8') {
            environment {
                JAVA_HOME="${GRAALVM_8_JAVA_HOME}"
            }
            steps {
                buildAndTest("GraalVM JDK 8", "graal-jdk-8")
            }
        }
        stage('GraalVM 11') {
            environment {
                JAVA_HOME="${GRAALVM_11_JAVA_HOME}"
            }
            steps {
                buildAndTest("GraalVM JDK 11", "graal-jdk-11")
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


void buildAndTest(String JDK, String profile) {
    echo "-------------------------"
    echo JDK
    echo profile
    echo "-------------------------"
    stage('Build with ' + JDK) {
        steps {
            sh "make ${profile} BACKEND=ptx,opencl"
        }
    }
    stage('PTX: Unit Tests') {
        steps {
            timeout(time: 5, unit: 'MINUTES') {
                sh 'tornado-test.py --verbose -J"-Dtornado.unittests.device=0:0"'
                sh 'tornado-test.py -V  -J"-Dtornado.unittests.device=0:0" -J"-Dtornado.heap.allocation=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03'
                sh 'test-native.sh'
            }
        }
    }
    stage("OpenCL: Unit Tests") {
        parallel {
            stage('OpenCL and GPU: Nvidia GeForce GTX 1060') {
                steps {
                    timeout(time: 5, unit: 'MINUTES') {
                        sh 'tornado-test.py --verbose -J"-Dtornado.unittests.device=1:1"'
                        sh 'tornado-test.py -V  -J"-Dtornado.unittests.device=1:1" -J"-Dtornado.heap.allocation=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03'
                        sh 'test-native.sh'
                    }
                }
            }
            stage('OpenCL and CPU: Intel Xeon E5-2620') {
                steps {
                    timeout(time: 5, unit: 'MINUTES') {
                        sh 'tornado-test.py --verbose -J"-Dtornado.unittests.device=1:0"'
                        sh 'tornado-test.py -V  -J"-Dtornado.unittests.device=1:0" -J"-Dtornado.heap.allocation=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03'
                        sh 'test-native.sh'
                    }
                }
            }
        }
    }
    stage('OpenCL: Test GPU Reductions') {
        steps {
            timeout(time: 5, unit: 'MINUTES') {
                sh 'tornado-test.py -V -J"-Ds0.t0.device=1:1 -Ds0.t1.device=1:1" uk.ac.manchester.tornado.unittests.reductions.TestReductionsFloats'
                sh 'tornado-test.py -V -J"-Ds0.t0.device=1:1 -Ds0.t1.device=1:1" uk.ac.manchester.tornado.unittests.reductions.TestReductionsDoubles'
                sh 'tornado-test.py -V -J"-Ds0.t0.device=1:1 -Ds0.t1.device=1:1" uk.ac.manchester.tornado.unittests.reductions.TestReductionsIntegers'
                sh 'tornado-test.py -V -J"-Ds0.t0.device=1:1 -Ds0.t1.device=1:1" uk.ac.manchester.tornado.unittests.reductions.TestReductionsFloats'
            }
        }
    }
    stage('Benchmarks') {
        steps {
            timeout(time: 10, unit: 'MINUTES') {
                sh 'python assembly/src/bin/tornado-benchmarks.py --printBenchmarks '
                sh 'python assembly/src/bin/tornado-benchmarks.py --medium --skipSequential --iterations 5 '
            }
        }
    }
     stage('Clone & Build KFusion') {
        steps {
            timeout(time: 5, unit: 'MINUTES') {
                sh 'cd /var/lib/jenkins/workspace/Slambench/slambench-tornado-refactor && git fetch && git pull origin master && mvn clean install -DskipTests'
            }
        }
    }
    stage('OpenCL: Run KFusion') {
        steps {
            timeout(time: 5, unit: 'MINUTES') {
                sh 'cd /var/lib/jenkins/workspace/Slambench/slambench-tornado-refactor && kfusion kfusion.tornado.Benchmark /var/lib/jenkins/workspace/Slambench/slambench-tornado-refactor/conf/traj2.settings'
            }
        }
    }
    stage('PTX: Run KFusion') {
        steps {
            timeout(time: 5, unit: 'MINUTES') {
                sh "cd /var/lib/jenkins/workspace/Slambench/slambench-tornado-refactor && sed -i 's/kfusion.tornado.backend=OpenCL/kfusion.tornado.backend=PTX/' conf/kfusion.settings"
                sh 'cd /var/lib/jenkins/workspace/Slambench/slambench-tornado-refactor && kfusion kfusion.tornado.Benchmark /var/lib/jenkins/workspace/Slambench/slambench-tornado-refactor/conf/traj2.settings'
            }
        }
    }
}
