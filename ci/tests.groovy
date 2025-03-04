// Глобальная функция для получения списка разрешенных веток
def AllowedBranchesAsList() {
    // return DEVELOPMENT_BRANCHES.split(',').collect { it.trim() }
    env.DEVELOPMENT_BRANCHES = DEVELOPMENT_BRANCHES.split(',').collect { it.trim() } // Убираем пробелы
    env.PROD_BRANCHES = PROD_BRANCHES.split(',').collect { it.trim() } // Убираем пробелы
}

pipeline {
    agent { label "uzel" }

    options {
        skipDefaultCheckout(false)
    }

    parameters {
        booleanParam(name: 'checkoutIntoVar', defaultValue: true, description: 'Присваиваем ли переменной checkoutResult результат команды checkout scm?')
        string(defaultValue: 'dev', description: 'Argument for shell command DEV stand', name: 'shell_param_dev', trim: true)
        string(defaultValue: 'prod', description: 'Argument for shell command PROD stand', name: 'shell_param_prod', trim: true)
    }

    environment {
        ENV_FILE=".env.example"
        DEVELOPMENT_BRANCHES = "development, copy-jenkins-branch" // можно указывать через запятую, например "test, dev, qa"
        PROD_BRANCHES = 'main'
    }

    stages {
        stage('Print params') {
            when {
                expression {"${currentBuild.currentResult}" == 'SUCCESS'}
            }
            steps {
                script {
                    echo "Create var shell_param (value = shell_param_dev)"
                    def shell_param = params.get("shell_param_dev")
                    echo "${shell_param}"
                    echo "Directly get param (checkoutIntoVar)"
                    echo "${params.get('checkoutIntoVar')}"
                    echo "Each param print"
                    params.each { param_key, param_value ->
                        echo "Ключ: ${param_key}, Значение: ${param_value}"
                    }
                    /* Other way to print each param
                    params.each {param ->
                        echo "param.key: ${param.key}"
                        echo "param.value: ${param.value}"
                    }
                    */
                }
            }
        }
        stage('Set New Env (ME_EXISTS)') {
            when {
                anyOf {
                    expression { params.checkoutIntoVar == true }
                    expression { currentBuild.currentResult == 'SUCCESS' }
                }
            }
            steps {
                script {
                        env.ME_EXISTS='YES, OF COURSE'
                    }
                    
                }
        }
        stage('PrAllEnv') {
            when {
                anyOf {
                    expression { currentBuild.result == 'SUCCESS' } // here is null
                    expression { currentBuild.currentResult == 'SUCCESS' }
                }
            }
            steps {
                script {
                    echo "Print from previous stage var shell_param"
                    sh "echo ${shell_param}"
                    echo "Print from previos ENV var (ME_EXISTS)"
                    echo "NOT EXISTS IS ${env.ME_EXISTS}"
                    echo "Try print environment var, who is not exists"
                    echo "NOT EXISTS IS ${env.ME_NOT_EXISTS}"
                    echo "print all env"
                    echo sh(script: 'env|sort', returnStdout: true)
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
                    try {
                        if (gitlabTargetBranch != null) {
                            // классно, продолжаем работу
                        } else {
                            gitlabTargetBranch = env.GIT_BRANCH
                        }
                    } catch (MissingPropertyException e) {
                        gitlabTargetBranch = env.GIT_BRANCH
                        }
                    catchError(buildResult: 'ABORTED', stageResult: 'ABORTED') {
                        def gitlabTargetBranch = gitlabTargetBranch.split("/")[-1]
                        echo "Проверяем, что gitlabTargetBranch = ${gitlabTargetBranch}"
                        AllowedBranchesAsList()
                        echo "Содержится в списке, обозначенный как DEV: ${DEVELOPMENT_BRANCHES.join(';')}"
                        echo "Или же в обозначенном как PROD: ${PROD_BRANCHES.join(';')}"
                        if (DEVELOPMENT_BRANCHES.contains(gitlabTargetBranch)) {
                            def shell_param = params.get("shell_param_dev")
                            echo 'Содержится в DEV'
                        } else if (PROD_BRANCHES.contains(gitlabTargetBranch)) {
                            def shell_param = params.get("shell_param_prod")
                            echo 'Содержится в PROD'
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
                    echo "Try print shell_param"
                    sh "echo ${shell_param}"
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