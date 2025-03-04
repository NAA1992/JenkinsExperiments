// Глобальная функция для получения списка разрешенных веток
def getAllowedBranches() {
    return BRANCHES_ALLOWED_FOR_WORK.split(',').collect { it.trim() } // Убираем пробелы
}

pipeline {
    agent { label "uzel" }

    options {
        skipDefaultCheckout(false)
    }

    parameters {
        booleanParam(name: 'checkoutIntoVar', defaultValue: true, description: 'Присваиваем ли переменной checkoutResult результат команды checkout scm?')
    }
    
    environment {
        ENV_FILE=".env.example"
        BRANCHES_ALLOWED_FOR_WORK = 'copy-jenkins-branch, Jenkins_ATOM-1095, development, main'
    }

    stages {
        stage('Print params') {
            steps {
                script {
                    echo "print only 1 param"
                    def shell_param = params.get("checkoutIntoVar")
                    echo "${shell_param}"
                    echo "${params.get('checkoutIntoVar')}"
                    params.each { param_key, param_value ->
                        echo "Ключ: ${param_key}, Значение: ${param_value}"
                    }
                    params.each {param ->
                        echo "param.key: ${param.key}"
                        echo "param.value: ${param.value}"
                    }
                }
            }
        }
        stage('PrAllEnv') {
            steps {
                script {
                    echo sh(script: 'env|sort', returnStdout: true)
                }
            }
        }
        
        stage('NOT EXISTS env') {
            when {
                expression {"${currentBuild.currentResult}" == 'SUCCESS'}
            }
            steps {
                script {
                    echo "NOT EXISTS IS ${env.EXISTS}"
                }
            }
        }
        stage('set env JUSTKEY') {
            when {
                anyOf {
                    expression { params.checkoutIntoVar == true }
                    expression { currentBuild.currentResult == 'SUCCESS' }
                }
            }
            steps {
                script {
                        env.JUSTKEY='JUSTVAL'
                    }
                    
                }
            }
        stage('CheckoutSCM into var') {
            when {
                allOf {
                    expression { params.checkoutIntoVar == true }
                    expression { currentBuild.currentResult == 'SUCCESS' }
                }
            }
            steps {
                script {
                    def checkoutResult = checkout scm
                    echo "Checkout Result: ${checkoutResult.toString()}"
                }
            }
        }
        stage('Check that current branch contains in allowed') {
            when {
                expression {"${currentBuild.currentResult}" == 'SUCCESS'}
            }
            steps {
                script {
                    catchError(buildResult: 'ABORTED', stageResult: 'ABORTED') {
                        env.gitlabTargetBranch = "Jenkins_ATOM-1095"
                        echo "Проверяем, что gitlabTargetBranch = ${gitlabTargetBranch}"
                        allowedBranches = getAllowedBranches()
                        echo "Содержится в этом списке: ${allowedBranches.join(';')}"
                        if (allowedBranches.contains(gitlabTargetBranch)) {
                            echo 'Да, все так, продолжаем работу'
                        } else {
                            error('Прервано, т.к. Merge был произведен в другую ветвь')
                        }
                    }
                }
            }
        }
        stage('Status ABORT') {
            when {
                expression {"${currentBuild.currentResult}" == 'SUCCESS'}
            }
            steps {
                script {
                    catchError(buildResult: 'ABORTED', stageResult: 'ABORTED') {
                        if (1==1) {
                            error('Next прерван, выполнение остановлено.')
                        }
                    }
                }
            }
        }
        stage('It stage forever skip') {
            when {
                expression {"${currentBuild.currentResult}" == 'SUCCESS'}
            }
            steps {
                script {
                    echo "HOME IS ${env.HOME}"
                }
            }
        }
    }
    post {
        always {
            cleanWs(
                cleanWhenNotBuilt: false,
                deleteDirs: true,
                disableDeferredWipeout: true,
                notFailBuild: true
                /* Если убирать комментарий, не забудь добавить запятую выше
                    patterns: [ // задаёт исключения и включения для очистки
                            [pattern: '.gitignore', type: 'INCLUDE'], // удалить .gitignore.
                            [pattern: '.propsfile', type: 'EXCLUDE'] // оставить .propsfile (хз зачем он нужен)
                    ] */
            )
        }
    }
}