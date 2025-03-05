#!/usr/bin/env groovy

// Ссылка на issues по gitlab plugin
// https://github.com/jenkinsci/gitlab-plugin/issues?q=is%3Aissue%20%20tag%20
// Ссылка на триггеры 
// https://www.jenkins.io/doc/pipeline/steps/params/pipelinetriggers/
// триггеры добавляются внутри pipeline, но они же заменяют триггеры установленные в Jenkins
/*
    triggers {
        gitlab(
            // trigger pipeline when push commits. IT DISABLE AND TAG_PUSH TOO!
            triggerOnPush: false,
            // trigger pipeline when create a mr
            triggerOnMergeRequest: false,
            triggerOpenMergeRequestOnPush: "never",
            setBuildDescription: false,
            // trigger pipeline when merge a mr
            triggerOnAcceptedMergeRequest: true,
            triggerOnPipelineEvent: false,
            triggerOnClosedMergeRequest: false,
            triggerOnApprovedMergeRequest: false,
            // trigger pipeline when comment on open MR
            addNoteOnMergeRequest: false,
            triggerOnNoteRequest: false,
            // trigger on target MR branch name
            targetBranchRegex: 'Jenkins_ATOM-1095',
        )
    }
*/
// pipeline generator
// https://jenkinspipelinegenerator.octopus.com/#/

// глобальные переменные для SHELL команды 
shell_param_dev = "dev"
shell_param_prod = "prod"

// Глобальная функция для получения списка разрешенных веток
def AllowedBranchesAsList() {
    // return DEVELOPMENT_BRANCHES.split(',').collect { it.trim() }
    DEVELOPMENT_BRANCHES = DEVELOPMENT_BRANCHES.split(',').collect { it.trim() } // Убираем пробелы ['development', 'ATOM-697']
    PROD_BRANCHES = PROD_BRANCHES.split(',').collect { it.trim() } // Убираем пробелы
}

// Определяем агента. В приоритете - из параметра, потом из Environment, а затем уже агент DEFAULT_AGENT
def determineAgent() {
    if (params.AGENT != null) {
        return params.AGENT
    } else if (env.AGENT != null) {
        return env.AGENT
    } else {
        error('Прервано, т.к. ни переменной, ни параметра AGENT не существует')
        echo "DEFAULT_AGENT"
        return "DEFAULT_AGENT"
    }
}

pipeline {
    agent { label determineAgent() }

    options {
        skipDefaultCheckout(false)
    }
    environment {
        ENV_FILE=".env.example"
        DEVELOPMENT_BRANCHES = "development" // можно указывать через запятую, например "test, dev, qa"
        PROD_BRANCHES = 'main'
    }

    // AGENT по умолчанию лежит в функции
    // parameters {
    //    string(name: 'AGENT', defaultValue: 'cms-netbox-dev', description: 'Agent (host, computer) where runs groovy', trim: true)
    // }

    stages {
        stage('Check gitlabTargetBranch') {
            when {
                expression {"${currentBuild.currentResult}" == 'SUCCESS'}
            }
            steps {
                script {
                    try {
                        if (gitlabTargetBranch == null) {
                            echo "gitlabTargetBranch = env.GIT_BRANCH"
                            gitlabTargetBranch = env.GIT_BRANCH
                        }
                    } catch (MissingPropertyException e) {
                        echo "gitlabTargetBranch = env.GIT_BRANCH"
                        gitlabTargetBranch = env.GIT_BRANCH
                        }
                    echo "Проверяем, что gitlabTargetBranch = ${gitlabTargetBranch}"
                    AllowedBranchesAsList()
                    echo "Содержится в списке, обозначенный как DEV: ${DEVELOPMENT_BRANCHES.join(';')}"
                    echo "Или же в обозначенном как PROD: ${PROD_BRANCHES.join(';')}"
                    catchError(buildResult: 'ABORTED', stageResult: 'ABORTED') {
                        if (DEVELOPMENT_BRANCHES.contains(gitlabTargetBranch)) {
                            env.SHELL_PARAM = params.get("shell_param_dev")
                            echo "Содержится в DEV, SHELL_PARAM = $SHELL_PARAM "
                        } else if (PROD_BRANCHES.contains(gitlabTargetBranch)) {
                            env.SHELL_PARAM = params.get("shell_param_prod")
                            echo "Содержится в PROD, SHELL_PARAM = $SHELL_PARAM "
                        } else {
                            error('Прервано, т.к. Merge был произведен в другую ветвь')
                        }
                    }
                }
            }
        }
        stage('Push as commit New Version') {
            when {
                expression {"$currentBuild.currentResult" == 'SUCCESS'}
            }
            steps {
                script {
                    sh """
                        git fetch --all
                        git switch $gitlabTargetBranch
                        ./makeshell.sh push-commit-version $SHELL_PARAM
                    """
                }
            }
        }
        stage('Push Tag with New Version') {
            when {
                expression {"${currentBuild.currentResult}" == 'SUCCESS'}
            }
            steps {
                script {
                    sh """
                        ./makeshell.sh push-tag-version $SHELL_PARAM
                    """
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