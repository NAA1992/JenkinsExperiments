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

// Глобальная функция для получения списка разрешенных веток
def AllowedBranchesAsList() {
    // return DEVELOPMENT_BRANCHES.split(',').collect { it.trim() }
    env.DEVELOPMENT_BRANCHES = DEVELOPMENT_BRANCHES.split(',').collect { it.trim() } // Убираем пробелы
    env.PROD_BRANCHES = PROD_BRANCHES.split(',').collect { it.trim() } // Убираем пробелы
}

pipeline {
    agent { label "${AGENT}" }

    options {
        skipDefaultCheckout(false)
    }
    environment {
        ENV_FILE=".env.example"
        DEVELOPMENT_BRANCHES = "development, copy-jenkins-branch" // можно указывать через запятую, например "test, dev, qa"
        PROD_BRANCHES = 'main'
    }

    parameters {
        string(defaultValue: 'dev', description: 'Argument for shell command DEV stand', name: 'shell_param_dev', trim: true)
        string(defaultValue: 'prod', description: 'Argument for shell command PROD stand', name: 'shell_param_prod', trim: true)
    }

    stages {
        stage('Check gitlabTargetBranch') {
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
        stage('Push as commit New Version') {
            when {
                expression {"${currentBuild.currentResult}" == 'SUCCESS'}
            }
            steps {
                script {
                    sh '''
                        git fetch --all
                        git switch ${gitlabTargetBranch}
                        ./makeshell.sh push-commit-version ${shell_param}
                    '''
                }
            }
        }
        stage('Push Tag with New Version') {
            when {
                expression {"${currentBuild.currentResult}" == 'SUCCESS'}
            }
            steps {
                script {
                    sh '''
                        ./makeshell.sh push-tag-version ${shell_param}
                    '''
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