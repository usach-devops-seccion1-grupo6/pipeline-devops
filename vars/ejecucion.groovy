def call(){
    def utils  = new test.UtilMethods()
    def tags = sh(script: "git tag --sort version:refname | tail -1", returnStdout: true).trim()
    pipeline {
        agent any

		environment {
			NEXUS_USER		 = credentials('NEXUS-USER')
			NEXUS_PASSWORD	 = credentials('NEXUS-PASS')
		}

        parameters {
            choice(name: 'buildtool', choices: ['maven','gradle'], description: 'Elección de herramienta de construcción para aplicación covid')
            string(name: 'stages', defaultValue: '' , description: 'Escribir stages a ejecutar en formato: stage1;stage2;stage3. Si stage es vacío, se ejecutarán todos los stages.')
        }
        
        stages {
            stage('Pipeline') {
                steps {
                    script{
                        
                        sh 'env'
                        env.TAREA = ""
                        env.CURR_TAG = "${tags}"
                        echo "Git current tags: ${env.CURR_TAG}"
                        tags = utils.upTagVersion("${tags}")
                        env.NEXT_TAG = "${tags}"
                        echo "Git new tags: ${env.NEXT_TAG}"
                        def parsed = "${env.JOB_NAME}".tokenize('/')
                        env.JOB_MULTI = "${parsed[0]}"
                        env.RAMA = "${parsed[1]}"

                        figlet params.buildtool
                        def archivo = (params.buildtool == 'gradle') ? 'build.gradle' : 'pom.xml'

                        if (fileExists(archivo)){
                            "${params.buildtool}" "${params.stages}"
                        } else {
                            error "archivo ${archivo} no existe. No se puede construir pipeline basado en ${params.buildtool}"
                        }
                    }
                }

				post{
					success{
						slackSend color: 'good', message: "[Grupo6] [Pipeline ${env.PIPELINE_TYPE}][Rama: ${env.RAMA}][Stage: ${env.TAREA}][${env.BUILD_DISPLAY_NAME}][${params.buildtool}][Resultado: OK]"
					}
					failure{
						slackSend color: 'danger', message: "[Grupo6] [Pipeline ${env.PIPELINE_TYPE}][Rama: ${env.RAMA}][Stage: ${env.TAREA}][${env.BUILD_DISPLAY_NAME}][${params.buildtool}][Resultado: No OK]"
					}
			 	}
            }
        }
    }
}

return this;
