// Requires a Jenkins agent with JDK 21 and a Docker daemon reachable from the agent (needed by
// both the "Integration Tests" stage - Testcontainers spins up real Postgres - and the "Docker
// Build" stage). No image registry is wired up yet: "Docker Build" only builds and tags locally,
// and "Deploy" is a placeholder until a real target environment exists.
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        IMAGE_NAME = 'commerce-core'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Compile') {
            steps {
                sh './mvnw -B clean compile test-compile'
            }
        }

        stage('Unit Tests') {
            steps {
                // Surefire's default includes (**/*Test.java, **/*Tests.java) never match *IT.java,
                // so this only runs the plain Mockito unit tests - no Docker needed here.
                sh './mvnw -B test'
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Integration Tests') {
            steps {
                // Failsafe's default includes are the complement of Surefire's (**/*IT.java) -
                // OrderControllerIT and CommerceCoreApplicationIT, both Testcontainers-backed.
                sh './mvnw -B failsafe:integration-test failsafe:verify'
            }
            post {
                always {
                    junit testResults: 'target/failsafe-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Package') {
            steps {
                // Tests already ran and were verified in their own stages above; don't repeat them.
                sh './mvnw -B package -DskipTests'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh "docker build -t ${IMAGE_NAME}:${env.BUILD_NUMBER} -t ${IMAGE_NAME}:latest ."
            }
        }

        stage('Deploy') {
            when {
                branch 'main'
            }
            steps {
                echo 'No deploy target configured yet. Wire this stage to wherever the app actually ' +
                     'runs (docker compose up -d on a host, a registry push + orchestrator rollout, etc.) ' +
                     'once one exists - not fabricating one here.'
            }
        }
    }

    post {
        always {
            cleanWs()
        }
    }
}
