// Глобальная функция для получения списка разрешенных веток
def AllowedBranchesAsList() {
    // return DEVELOPMENT_BRANCHES.split(',').collect { it.trim() }
    DEVELOPMENT_BRANCHES = env.DEVELOPMENT_BRANCHES.split(',').collect { it.trim() } // Убираем пробелы
    PROD_BRANCHES = env.PROD_BRANCHES.split(',').collect { it.trim() } // Убираем пробелы
}

shell_param_dev = "dev" // глобальная переменная DEV среды () 
shell_param_prod = "prod"

pipeline {
    agent { label "${AGENT}" }

    options {
        skipDefaultCheckout(false)
    }

    parameters {
        booleanParam(name: 'checkoutIntoVar', defaultValue: true, description: 'Присваиваем ли переменной checkoutResult результат команды checkout scm?')
        string(name: 'Enable_Breake_Stage', defaultValue: 'YES', description: 'Enable Break Stage', trim: true)
    }

    environment {
        ENV_FILE=".env.example"
        DEVELOPMENT_BRANCHES = "development, copy-jenkins-branch" // можно указывать через запятую, например "test, dev, qa"
        PROD_BRANCHES = 'main'
        TRY_TO_CHANGE_ME = 'DEFAULT_VALUE'
    }

    stages {
        stage('Environments') {
            when {
                expression {"${currentBuild.currentResult}" == 'SUCCESS'}
            }
            steps {
                script {
                    echo "Try print each ENV_NAME, ENV_VALUE"
                    env.each { env_key, env_value ->
                        echo "Environment variable: ${env_key} = ${env_value}"
                    }
                    echo "Try get value by name. Example, HOME"
                    HOME_VAR = env.get("HOME")
                    echo "${HOME_VAR}"
                    echo "Try change env TRY_TO_CHANGE_ME"
                    env.TRY_TO_CHANGE_ME = 'CHANGED_VALUE'
                    echo "${env.TRY_TO_CHANGE_ME}"
                    echo "${TRY_TO_CHANGE_ME}"
                    echo "Try create global env GLOBAL_ENV_BREAK with value from param Enable_Breake_Stage"
                    GLOBAL_ENV_BREAK="${params.get('Enable_Breake_Stage')}"
                    echo "${GLOBAL_ENV_BREAK}"
                    echo "Try to create env DEV_SREDA with value from variable shell_param_dev"
                    DEV_SREDA = "${shell_param_dev}"
                    echo "${DEV_SREDA}"
                    echo "Original value shell_param_dev is ${shell_param_dev}"
                    echo "Print NOT_EXISTS env: ${NOT_EXISTS}"
                    echo "Print env.NOT_EXISTS env: ${env.NOT_EXISTS}"
                    echo sh(script: 'env|sort', returnStdout: true)
                }
            }
        }
        stage('Print params') {
            when {
                expression {"${currentBuild.currentResult}" == 'SUCCESS'}
            }
            steps {
                script {
                    echo "Check, that environments from previous stage is saved"
                    echo "GLOBAL_ENV_BREAK = ${GLOBAL_ENV_BREAK}"
                    echo "env.TRY_TO_CHANGE_ME  = ${env.TRY_TO_CHANGE_ME}"
                    echo "TRY_TO_CHANGE_ME = ${TRY_TO_CHANGE_ME}"
                    echo "Each param print"
                    params.each { param_key, param_value ->
                        echo "Param Key: ${param_key}, Param Value: ${param_value}"
                    }
                    /* Other way to print each param
                    params.each {param ->
                        echo "param.key: ${param.key}"
                        echo "param.value: ${param.value}"
                    }
                    */
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
                        if (gitlabTargetBranch == null) {
                            gitlabTargetBranch = env.GIT_BRANCH
                            echo "gitlabTargetBranch was null"
                        }
                    } catch (MissingPropertyException e) {
                        gitlabTargetBranch = env.GIT_BRANCH
                        echo "gitlabTargetBranch was NOT EXISTS"
                        }
                    catchError(buildResult: 'ABORTED', stageResult: 'ABORTED') {
                        def gitlabTargetBranch = gitlabTargetBranch.split("/")[-1]
                        echo "Проверяем, что gitlabTargetBranch = ${gitlabTargetBranch}"
                        AllowedBranchesAsList()
                        echo "Содержится в списке, обозначенный как DEV: ${DEVELOPMENT_BRANCHES.join(';')}"
                        echo "Или же в обозначенном как PROD: ${PROD_BRANCHES.join(';')}"
                        if (DEVELOPMENT_BRANCHES.contains(gitlabTargetBranch)) {
                            SHELL_PARAM = params.get("shell_param_dev")
                            echo 'Содержится в DEV'
                        } else if (PROD_BRANCHES.contains(gitlabTargetBranch)) {
                            SHELL_PARAM = params.get("shell_param_prod")
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
                allOf {
                    expression { params.Enable_Breake_Stage == 'YES' }
                    expression { currentBuild.currentResult == 'SUCCESS' }
                }
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
        stage('It stage skip if status abort') {
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