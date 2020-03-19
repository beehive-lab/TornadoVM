pipeline {
    agent any
    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }
    environment {
        JAVA_HOME="/opt/jenkins/graal-jvmci-8/openjdk1.8.0_242/linux-amd64/product"
        TORNADO_ROOT="/var/lib/jenkins/workspace/Tornado-pipeline"
        PATH="/var/lib/jenkins/workspace/Slambench/slambench-tornado-refactor/bin:/var/lib/jenkins/workspace/Tornado-pipeline/bin/bin:$PATH"    
        TORNADO_SDK="/var/lib/jenkins/workspace/Tornado-pipeline/bin/sdk" 
        CMAKE_ROOT="/opt/jenkins/cmake-3.10.2-Linux-x86_64"
        KFUSION_ROOT="/var/lib/jenkins/workspace/Slambench/slambench-tornado-refactor"
    }
    stages {
        stage('checkout-branch') {
            steps {
                step([$class: 'WsCleanup'])
                checkout scm
                sh 'git checkout master'
                checkout([$class: 'GitSCM', branches: [[name: '**']], doGenerateSubmoduleConfigurations: false, extensions:[[$class: 'LocalBranch']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '9bca499b-bd08-4fb2-9762-12105b44890e', url: 'https://github.com/beehive-lab/TornadoVM-Internal.git']]])
           }
        }
        stage('build') {
            steps {
                sh 'make'
                sh 'bash bin/bin/tornadoLocalInstallMaven'
            }
        }
        stage('tornado-unittests') {
        	steps {
				timeout(time: 5, unit: 'MINUTES') {
               		sh 'make tests'
            	}
			}
        }       
		stage('tornado-benchmarks') {
        	steps {
				timeout(time: 10, unit: 'MINUTES') {
                	sh 'python assembly/src/bin/tornado-benchmarks.py --medium --skipSequential --iterations 5 '
            	}
			}
        }
         stage('clone-n-build-kfusion') {
        	steps {
				timeout(time: 5, unit: 'MINUTES') {
                	sh 'cd /var/lib/jenkins/workspace/Slambench/slambench-tornado-refactor && git fetch && git pull origin master && mvn clean install -DskipTests'
            	}
			}
        }
        stage('run-kfusion') {
        	steps {
				timeout(time: 5, unit: 'MINUTES') {
                	sh 'cd /var/lib/jenkins/workspace/Slambench/slambench-tornado-refactor && kfusion kfusion.tornado.Benchmark /var/lib/jenkins/workspace/Slambench/slambench-tornado-refactor/conf/traj2.settings'
            	}
			}
        }
       
    }
    post {
        success {
            slackSend color: '#00CC00', message: "SUCCESSFUL: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})"
            deleteDir() /* clean up our workspace */
        }   
       failure {
            slackSend color: '#CC0000', message: "FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})"
        }
    }
}
