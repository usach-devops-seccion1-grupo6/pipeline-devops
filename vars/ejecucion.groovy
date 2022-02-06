def call(){

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
						slackSend color: 'good', message: "[Grupo6] [Pipeline ${env.PIPELINE_TYPE}][Rama: ${env.RAMA}][Stage: ${env.TAREA}][${env.BUILD_DISPLAY_NAME}][${params.buildtool}][Resultado: OK]", channel: "lab-pipeline-mod3-seccion1-status"
					}
					failure{
						slackSend color: 'danger', message: "[Grupo6] [Pipeline ${env.PIPELINE_TYPE}][Rama: ${env.RAMA}][Stage: ${env.TAREA}][${env.BUILD_DISPLAY_NAME}][${params.buildtool}][Resultado: No OK]", channel: "test6"
					}
			 	}
            }
        }
    }
}

return this;
