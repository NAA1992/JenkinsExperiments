// Глобальная функция для получения списка разрешенных веток
def AllowedBranchesAsList() {
    // return DEVELOPMENT_BRANCHES.split(',').collect { it.trim() }
    DEVELOPMENT_BRANCHES = env.DEVELOPMENT_BRANCHES.split(',').collect { it.trim() } // Убираем пробелы
    PROD_BRANCHES = env.PROD_BRANCHES.split(',').collect { it.trim() } // Убираем пробелы
}

// Определяем агента. В приоритете - из параметра, потом из Environment, а затем уже агент DEFAULT_AGENT
// Позже я переделал. DEFAULT_AGENT'a не будет - будет ошибка
def determineAgent() {
    if (params.AGENT != null && params.AGENT != "") {
        return params.AGENT
    } else if (env.AGENT != null && env.AGENT != "") {
        return env.AGENT
    } else {
        error('Прервано, т.к. ни переменной, ни параметра AGENT не существует или они пустые')
        echo "DEFAULT_AGENT"
        return "DEFAULT_AGENT"
    }
}

// глобальные переменные для SHELL команды
shell_param_dev = "dev"
shell_param_prod = "prod"
CONFLICT_WITH_GLOBAL = "GLOBAL"

pipeline {

    // Приоритет - environment, затем param, а затем ошибка будет если не установлено
    // А мне нужно было наоборот реализовать - в приоритете сначала param, поэтому отдельная функция
    // agent { label AGENT_as_param }
    // Метки внутри Jenkins должны быть без запятой, просто через пробел
    agent { label determineAgent() }

    options {
        skipDefaultCheckout(false)
    }


    // parameters includes into Environment
    parameters {
        booleanParam(name: 'checkoutIntoVar', defaultValue: true, description: 'Присваиваем ли переменной checkoutResult результат команды checkout scm?')
        string(name: 'Enable_Breake_Stage', defaultValue: 'YES', description: 'Enable Break Stage', trim: true)
        string(name: 'CONFLICT_WITH_GLOBAL', defaultValue: 'PARAM', description: 'Enable Break Stage', trim: true)
        // string(name: 'AGENT', defaultValue: 'localhost', description: 'Agent (host, computer) where runs groovy', trim: true)
    }

    environment {
        ENV_FILE=".env.example"
        DEVELOPMENT_BRANCHES = "development, jenkins-branch" // можно указывать через запятую, например "test, dev, qa"
        PROD_BRANCHES = 'main'
        TRY_TO_CHANGE_ME = 'DEFAULT_VALUE'
        CHANGE_ME_VIA_ENVIRONMENTS = 'DEFAULT_VALUE'
        TENANT = 'hz_kakoy'
        Enable_Breake_Stage = "NO" // подсвечивается как параметр, посмотрим что изменится
        CONFLICT_WITH_GLOBAL = 'ENV'
    }

    stages {
        stage('Environments') {
            environment {
                CHANGE_ME_VIA_ENVIRONMENTS = 'CHANGED_VALUE'
            }
            when {
                expression {"${currentBuild.currentResult}" == 'SUCCESS'}
            }
            steps {
                script {
                    /* IT's allowed via file, but slowly
                    echo "Try print each ENV_NAME, ENV_VALUE"
                    sh 'env > env.txt'
                    for (String line : readFile('env.txt').split("\r?\n")) {
                        def split = line.split('=', 2)

                        // Проверяем, что строка была успешно разбита
                        if (split.size() == 2) {
                            def envName = split[0]
                            def envValue = split[1]
                            println "env_name: ${envName} env_val: ${envValue}"
                        } else {
                            println "Invalid line: ${line}"
                        }
                    }
                    */
                    // echo "Try get value by name. Example, HOME"
                    // BELOW CODE IS ERROR
                    //HOME_VAR = env.get("HOME")
                    // And if variable not exists (because of upper) it will be error
                    //echo "${HOME_VAR}"
                    echo "Just CONFLICT_WITH_GLOBAL is $CONFLICT_WITH_GLOBAL" // GLOBAL
                    CONFLICT_WITH_GLOBAL = "Stage 1"
                    echo "Changed CONFLICT_WITH_GLOBAL is $CONFLICT_WITH_GLOBAL" // Stage 1
                    echo "Try change env TRY_TO_CHANGE_ME"
                    // So not changed
                    //env.TRY_TO_CHANGE_ME = 'CHANGED_VALUE'
                    TRY_TO_CHANGE_ME = 'CHANGED_VALUE' // yes, it change and you must use without env. prefix
                    echo "env.TRY_TO_CHANGE_ME = ${env.TRY_TO_CHANGE_ME}" // val from ENV
                    echo "TRY_TO_CHANGE_ME = ${TRY_TO_CHANGE_ME}" // CHANGED_VALUE
                    echo "Try create global env GLOBAL_ENV_BREAK with value from param Enable_Breake_Stage"
                    GLOBAL_ENV_BREAK="${params.get('Enable_Breake_Stage')}" // val from PARAM
                    echo "GLOBAL_ENV_BREAK is ${GLOBAL_ENV_BREAK}" // val from PARAM
                    ONE_MORE_WAY_TO_GET_FROM_PARAM = "$params.get('Enable_Breake_Stage')"
                    echo "$ONE_MORE_WAY_TO_GET_FROM_PARAM" // output: null('Enable_Breake_Stage')
                    ONE_MORE_WAY_TO_GET_FROM_PARAM = Enable_Breake_Stage
                    echo "$ONE_MORE_WAY_TO_GET_FROM_PARAM" // val from PARAM
                    ONE_MORE_WAY_TO_GET_FROM_PARAM = '${params.get("Enable_Breake_Stage")}'
                    echo "$ONE_MORE_WAY_TO_GET_FROM_PARAM" // PRINT AS IS
                    echo "Try to create env DEV_SREDA with value from variable shell_param_dev"
                    DEV_SREDA = "${shell_param_dev}" // val from VAR
                    echo "${DEV_SREDA}" // val from VAR
                    echo "Original value shell_param_dev is ${shell_param_dev}" // val from VAR
                    echo "Print env.NOT_EXISTS env: ${env.NOT_EXISTS}" // output: null
                    echo """echo 'Содержится в DEV, SHELL_PARAM = "${DEV_SREDA}" ' """ // val from VAR
                    echo 'Содержится в DEV, SHELL_PARAM = "${DEV_SREDA}" // one quote' // PRINT AS IS
                    echo """echo "Содержится в DEV, SHELL_PARAM = '${DEV_SREDA}' " // changed quotes""" // val from VAR
                    echo "Содержится в DEV, SHELL_PARAM = '${DEV_SREDA}' " // val from VAR
                    echo "That's works: $DEV_SREDA" // val from VAR
                    sh '''
                        echo 'sh command'
                        echo 'DEV_SREDA IS ${DEV_SREDA}'
                        echo $DEV_SREDA
                        echo "AGAIN TRY, WITH OTHER QUOTES: ${DEV_SREDA}"
                        echo "MAYBE THIS WORK? '${DEV_SREDA}'"
                        echo 'THAT EXACTLY WORKS: "${DEV_SREDA}"'
                    ''' // ${DEV_SREDA} printed as is, $DEV_SREDA - nothing, AGAIN - nothing, MAYBE - nothing, EXACTLY - as is
                    sh """
                        echo 'sh command with two quotes'
                        echo 'two quotes DEV_SREDA IS ${DEV_SREDA}'
                        echo $DEV_SREDA
                        echo "two quotes AGAIN TRY, WITH OTHER QUOTES: ${DEV_SREDA}"
                        echo "two quotes MAYBE THIS WORK? '${DEV_SREDA}'"
                    """ // ALWAYS CHANGED !!!
                    // echo $DEV_SREDA // ERROR
                    // echo ${DEV_SREDA} // ERROR
                    echo 'OK, one quotes: $DEV_SREDA' // print AS IS
                    echo "OK, two quotes: $DEV_SREDA" // changes
                    echo "We added env CHANGE_ME_VIA_ENVIRONMENTS, check that value changed: $CHANGE_ME_VIA_ENVIRONMENTS" // CHANGED_VALUE, but not forever
                    echo sh(script: 'env|sort', returnStdout: true)
                    // Если внутри shell скрипта env переопределяется, то оно и будет использоваться (переопределенное)
                    echo "makeshell.sh debug"
                    sh "./makeshell.sh debug" // выведет содержимое .env.example
                    echo "From env Enable_Breake_Stage: $env.Enable_Breake_Stage" // like in ENV
                    echo "From param: Enable_Breake_Stage:  $params.Enable_Breake_Stage" // like in params
                    echo "Just Enable_Breake_Stage: $Enable_Breake_Stage" // like in ENV (!!!) ENV UPPER THAN PARAMS (!!!)
                    Enable_Breake_Stage = "ANY OTHER"
                    echo "We tried change Enable_Breake_Stage, value: $Enable_Breake_Stage" // Yes, it's changed and changed in other stages
                }
            }
        }
        stage('Print params') {
            when {
                expression {"${currentBuild.currentResult}" == 'SUCCESS'}
            }
            steps {
                script {
                    echo "Previous stage changed CHANGE_ME_VIA_ENVIRONMENTS, check that: $CHANGE_ME_VIA_ENVIRONMENTS" // DEFAULT_VALUE
                    echo "Check, that environments from previous stage is saved"
                    echo "CONFLICT_WITH_GLOBAL = $CONFLICT_WITH_GLOBAL" // Stage 1
                    echo "GLOBAL_ENV_BREAK = ${GLOBAL_ENV_BREAK}" // YES
                    sh "echo 'GLOBAL_ENV_BREAK = $GLOBAL_ENV_BREAK'" // YES
                    echo "env.TRY_TO_CHANGE_ME  = ${env.TRY_TO_CHANGE_ME}" // DEFAULT_VALUE
                    echo "TRY_TO_CHANGE_ME = ${TRY_TO_CHANGE_ME}" // CHANGED_VALUE
                    echo "Enable_Breake_Stage = $Enable_Breake_Stage" // ANY_OTHER (changed from previous stage)
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
                    echo "env.HOME IS ${env.HOME}"
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