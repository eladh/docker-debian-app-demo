server = Artifactory.server "artifactory"
rtFullUrl = server.url
rtIpAddress = rtFullUrl - ~/^http?.:\/\// - ~/\/artifactory$/

buildInfo = Artifactory.newBuildInfo()

podTemplate(label: 'jenkins-pipeline' , cloud: 'k8s' , containers: [
        containerTemplate(name: 'docker', image: 'docker', command: 'cat', ttyEnabled: true , privileged: true)],
        volumes: [hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock')]) {

    node('jenkins-pipeline') {

        stage('Cleanup') {
            cleanWs()
        }

        stage('Clone sources') {
            git url: 'https://github.com/eladh/docker-debian-app-demo.git', credentialsId: 'github'
        }

        stage('Docker build') {
            def rtDocker = Artifactory.docker server: server

            container('docker') {
                docker.withRegistry("https://docker.$rtIpAddress", 'artifactorypass') {
                    sh("chmod 777 /var/run/docker.sock")
                    def dockerImageTag = "docker.$rtIpAddress/debian-app:${env.BUILD_NUMBER}"
                    def dockerImageTagLatest = "docker.$rtIpAddress/debian-app:latest"

                    buildInfo.env.capture = true

                    docker.build(dockerImageTag, "--build-arg DOCKER_REGISTRY_URL=docker.$rtIpAddress .")
                    docker.build(dockerImageTagLatest, "--build-arg DOCKER_REGISTRY_URL=docker.$rtIpAddress .")

                    rtDocker.push(dockerImageTag, "docker-local", buildInfo)
                    rtDocker.push(dockerImageTagLatest, "docker-local", buildInfo)
                    server.publishBuildInfo buildInfo
                }
            }
        }
    }
}