import pipeline.*

def call(String chosenStages){

	def utils  = new test.UtilMethods()
	def pipelineType = (utils.isCIorCD().contains('ci')) ? 'IC' : 'Release'
	def tags = sh(script: "git tag --sort version:refname | tail -1", returnStdout: true)
	
	echo "Git new tags: ${tags}"
	def pipelineStages = (pipelineType == 'IC') ? ['buildAndTest','sonar','runJar','rest','nexusCI','gitCreateRelease'] : ['downloadNexus','runDownloadedJar','rest','nexusCD','gitMergeMaster','gitMergeDevelop','gitTagMaster']
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

def buildAndTest(){
	sh 'gradle clean build'
}

def sonar(){
	withSonarQubeEnv('sonarqube') {
		sh 'gradle sonarqube -Dsonar.projectKey=ejemplo-gradle -Dsonar.java.binaries=build'
	}
}

def runJar(){
	sh "timeout 30 \$(which gradle) bootRun &"
	sleep 20
}

def rest(){
	sh "curl -X GET http://localhost:8082/rest/mscovid/test?msg=testing"
}

def nexusCI(){
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

def downloadNexus(){
	sh 'curl -X GET -u $NEXUS_USER:$NEXUS_PASSWORD "http://nexus:8081/repository/devops-usach-nexus/com/devopsusach2020/DevOpsUsach2020/0.0.1-develop/DevOpsUsach2020-0.0.1-develop.jar" -O'
}

def runDownloadedJar(){
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