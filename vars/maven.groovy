import pipeline.*

def call(String chosenStages){
	def utils  = new test.UtilMethods()
	def pipelineType = (utils.isCIorCD().contains('ci')) ? 'IC' : 'Release'

    def pipelineStages = (pipelineType == 'IC') ? ['compile', 'unitTest', 'jar', 'sonar','runJar','test','nexusUpload','gitCreateRelease'] : ['gitDiff', 'nexusDownload','run','test', 'gitMergeMaster','gitMergeDevelop','gitTagMaster']
    def stages = utils.getValidatedStages(chosenStages, pipelineStages)

    env.PIPELINE_TYPE = "${pipelineType}"

    stages.each{
        stage(it){
            env.TAREA = "${it}"

            try {
                "${it}"()
            }
            catch(Exception e) {
                error "Stage ${it} tiene problemas: ${e}"
            }
        }
    }
}

def compile(){
    sh 'mvn -Dmaven.test.skip=true clean compile -e'
}

def unitTest(){
    sh 'mvn test -e'
}

def jar(){
    sh 'mvn -Dmaven.test.skip=true package -e'
}

def sonar(){
    withSonarQubeEnv(installationName: 'sonarqube') {
		def sonar_id = "${env.JOB_MULTI}-${env.RAMA}-${env.BUILD_ID}"
        sh "mvn verify sonar:sonar -Dmaven.test.skip=true -Dsonar.projectKey=${sonar_id} -Dsonar.projectName=${sonar_id}"
    }
}

def runJar(){
    sh "timeout 30 \$(which mvn) spring-boot:run&"
    sleep 20
}

def test(){
    sh "curl -X GET http://localhost:8082/rest/mscovid/test?msg=testing"
}

def nexusUpload(){
	nexusPublisher nexusInstanceId: 'nexus',
	nexusRepositoryId: 'devops-usach-nexus',
	packages: [
		[$class: 'MavenPackage',
			mavenAssetList: [
				[classifier: '',
				extension: 'jar',
				filePath: 'build/DevOpsUsach2020-0.0.1.jar'
			]
		],
			mavenCoordinate: [
				artifactId: 'DevOpsUsach2020',
				groupId: 'com.devopsusach2020',
				packaging: 'jar',
				version: "0.0.1-${env.GIT_BRANCH}"
			]
		]
	]
}

def nexusDownload(){
    sh 'curl -X GET -u $NEXUS_USER:$NEXUS_PASSWORD "http://nexus:8081/repository/devops-usach-nexus/com/devopsusach2020/DevOpsUsach2020/0.0.1-develop/DevOpsUsach2020-0.0.1-develop.jar" -O'
}

def run(){
	sh 'timeout 30 $(which nohup) java -jar DevOpsUsach2020-0.0.1-develop.jar 2>/dev/null>&1 &'
    sleep 20
}

def nexusCD(){
	nexusPublisher nexusInstanceId: 'nexus',
	nexusRepositoryId: 'devops-usach-nexus',
	packages: [
		[$class: 'MavenPackage',
			mavenAssetList: [
				[classifier: '',
				extension: 'jar',
				filePath: 'DevOpsUsach2020-0.0.1-develop.jar'
			]
		],
			mavenCoordinate: [
				artifactId: 'DevOpsUsach2020',
				groupId: 'com.devopsusach2020',
				packaging: 'jar',
				version: "1.0.0"
			]
		]
	]
}

def gitCreateRelease(){
}

def gitMergeMaster(){
}

def gitMergeDevelop(){
}

def gitTagMaster(){
}

def gitDiff(){
}

return this;