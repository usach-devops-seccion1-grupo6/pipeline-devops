import pipeline.*

def call(String chosenStages){
	def utils  = new test.UtilMethods()
	def pipelineType = (utils.isCIorCD().contains('ci')) ? 'IC' : 'Release'

	def pipelineStages = (pipelineType == 'IC') ? ['compile', 'unitTest', 'jar', 'sonar','runJar','test','nexusUpload', 'nexusDownload', 'md5Jar', 'gitCreateRelease'] : ['gitDiff', 'nexusDownload','run','test', 'gitMergeMaster','gitMergeDevelop','gitTagMaster']
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
	sh 'gradle clean build'
}

def unitTest(){
	sh 'gradle test'
}

def jar(){
	sh 'gradle jar'
}

def sonar(){
	withSonarQubeEnv('sonarqube') {
		def sonar_id = "${env.JOB_MULTI}-${env.RAMA}-${env.BUILD_ID}"
		sh "gradle sonarqube -Dsonar.projectKey=${sonar_id} -Dsonar.projectName=${sonar_id} -Dsonar.java.binaries=build"
	}
}

def runJar(){
	sh "timeout 30 \$(which gradle) bootRun &"
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
				filePath: 'build/libs/DevOpsUsach2020-0.0.1.jar'
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

def gitCreateRelease(){
	sh 'git fetch -p &&	git checkout develop && git pull && git checkout -b release-v1.0.0 && git push origin release-v1.0.0'
}

def gitMergeMaster(){
	sh 'git switch main && git merge --no-ff release-v1.0.0'
}

def gitMergeDevelop(){
	sh 'git switch develop && git merge --no-ff release-v1.0.0'
}

def gitTagMaster(){
	sh 'git switch main && git tag -a v1.0.0'
}

return this;
