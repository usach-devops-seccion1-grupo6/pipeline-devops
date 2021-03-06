import pipeline.*

def call(String chosenStages){

	def utils  = new test.UtilMethods()
	sh "git pull --all"
    def tags = sh(script: "git tag --sort version:refname | tail -1", returnStdout: true).trim()
	env.CURR_TAG = "${tags}"
    echo "Git current tags: ${env.CURR_TAG}"
	tags = utils.upTagVersion("${tags}")
	env.NEXT_TAG = "${tags}"
	echo "Git new tags: ${env.NEXT_TAG}"
	def pipelineType = (utils.isCIorCD().contains('ci')) ? 'IC' : 'Release'

	def pipelineStages = (pipelineType == 'IC') ? ['compile', 'unitTest', 'jar', 'sonar','runJar','test','nexusUpload', 'gitCreateRelease'] : ['gitDiff', 'nexusDownload','runDownloadedJar','test', 'gitMergeMaster','gitMergeDevelop','gitTagMaster']
	def stages = utils.getValidatedStages(chosenStages, pipelineStages)

	env.PIPELINE_TYPE = "${pipelineType}"
	env.TMP_FOLDER = "/tmp/${env.RAMA}"
	utils.clone("${env.TMP_FOLDER}", "${env.GIT_URL}")
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

def md5Jar(){
	def md5Old = sh(script: "md5sum build/DevOpsUsach2020-0.0.1.jar |awk '{print \$1}'", returnStdout: true).trim()
	def md5New = sh(script: "md5sum DevOpsUsach2020-0.0.1-${env.GIT_BRANCH}.jar |awk '{print \$1}'", returnStdout: true).trim()
    //sh "test \"${md5Old}\" = \"${md5New}\""
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
				filePath: "build/libs/DevOpsUsach2020-${env.NEXT_TAG}.jar"
			]
		],
			mavenCoordinate: [
				artifactId: 'DevOpsUsach2020',
				groupId: 'com.devopsusach2020',
				packaging: 'jar',
				version: "${env.NEXT_TAG}-${env.GIT_BRANCH}"
			]
		]
	]
}

def nexusDownload(){
	sh "curl -X GET -u $NEXUS_USER:$NEXUS_PASSWORD 'http://nexus:8081/repository/devops-usach-nexus/com/devopsusach2020/DevOpsUsach2020/${env.NEXT_TAG}-develop/DevOpsUsach2020-${env.NEXT_TAG}-develop.jar' -O"
}

def runDownloadedJar(){
	sh "timeout 30 \$(which nohup) java -jar DevOpsUsach2020-${env.NEXT_TAG}-${env.GIT_BRANCH}.jar 2>/dev/null>&1 &"
    sleep 20
}

def gitCreateRelease(){
	if("${env.BRANCH_NAME}" == "develop"){
		sh "git fetch -p &&	git checkout develop && git pull && git checkout -b release-${env.NEXT_TAG} && git push origin release-${env.NEXT_TAG}"
	}
}

def gitDiff(){
	sh "cd ${env.TMP_FOLDER} && git diff ${env.GIT_COMMIT} main"
	sh "rm -rf /tmp/${env.BUILD_TAG}"
}

def gitMergeMaster(){
	sh "cd ${env.TMP_FOLDER} && git switch main && git pull && git merge -m 'merge to master' --no-ff origin/release-${env.NEXT_TAG} && git push"
}

def gitMergeDevelop(){
	sh "cd ${env.TMP_FOLDER} && git fetch origin develop && git checkout develop && git merge -m 'merge to develop' --no-ff origin/release-${env.NEXT_TAG} && git push"
}

def gitTagMaster(){
	sh "cd ${env.TMP_FOLDER} && git fetch origin main && git checkout main && git tag -m 'create tag' -a ${env.NEXT_TAG} && git push --tags && git switch release-${env.NEXT_TAG} && git switch main && git branch -d release-${env.NEXT_TAG} && git push -d origin release-${env.NEXT_TAG}"
}

return this;