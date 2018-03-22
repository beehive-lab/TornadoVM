pipeline {
    agent any
    options {
        timestamps()
        timeout(time: 1, unit: 'HOURS')
	}
	environment {
		 JAVA_HOME="/opt/jenkins/jdk1.8.0_131"
		 GRAAL_ROOT="/opt/jenkins"
		 TORNADO_ROOT="/var/lib/jenkins/workspace/Tornado"
		 TORNADO_REVISION='$(echo `git rev-parse --short HEAD`)'
		 GRAAL_VERSION="0.22"
		 JVMCI_VERSION="1.8.0_131"
		 PATH="/var/lib/jenkins/workspace/Slambench/slambench-tornado/bin:/var/lib/jenkins/workspace/Tornado-pipeline/bin/bin:$PATH"    
		 TORNADO_SDK="/var/lib/jenkins/workspace/Tornado-pipeline/bin/sdk"
		 CMAKE_ROOT="/opt/jenkins/cmake-3.10.2-Linux-x86_64"
		 KFUSION_ROOT="/var/lib/jenkins/workspace/Slambench/slambench-tornado"
	}

	stages {
		stage('checkout-branch') {
			steps {
     				step([$class: 'WsCleanup'])
		    		checkout scm
                		checkout([$class: 'GitSCM', branches: [[name: '*/feature/jenkinsfile/michalis']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '9bca499b-bd08-4fb2-9762-12105b44890e', url: 'https://github.com/beehive-lab/tornado.git']]])
			}
		}
		stage('pre-make') {
			steps {
		        	sh 'currentBranch=`git rev-parse --abbrev-ref HEAD`'
		       		sh 'git checkout master'
		       		sh 'git checkout $currentBranch'
		     }
		}
		stage('build') {
			steps {
				sh 'make'
			}
		}
		stage('tornado-unittests') {
			steps {
				sh 'make tests'
		    }
		}
		stage('build-n-run-kfusion') {
			steps {
				sh 'cd /var/lib/jenkins/workspace/Slambench/slambench-tornado'
				sh 'mvn clean install -DskipTests'
				sh 'kfusion kfusion.java.Benchmark /var/lib/jenkins/workspace/Slambench/slambench-tornado/conf/bm-traj2.settings'
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



