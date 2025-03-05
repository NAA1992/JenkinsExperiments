#!/bin/bash

# MARK: set ENV_FILE var
# Присваиваем ENV_FILE переменную из окружения
ENV_FILE=${ENV_FILE:-""}

# В случае, если это значение пусто
if [ -z "$ENV_FILE" ]; then
  echo "Значение ENV_FILE в системе ПУСТ."
  echo "Установите с помощью команды:"
  echo "export ENV_FILE=.env.example"
  echo "Чтобы скрипт больше не спрашивал его значение. Проверка:"
  echo "echo \${ENV_FILE}"
  read -p "Введите значение ENV_FILE: " ENV_FILE
fi
# Проверяем существование ENV_FILE (файла с переменными окружения) и загружаем его
if [ -f "$ENV_FILE" ]; then
    source "$ENV_FILE"
    echo "Файл $ENV_FILE загружен."
    export ENV_FILE=$ENV_FILE
else
    echo "Ошибка: файл $ENV_FILE не найден."
    exit 1
fi


# MARK: usage
function usage {
    cat << EOF
Deploy Tools
Usage:
     ./makeshell.sh command
Command:
     prepare-pgadmin    Create folder for pgadmin data and touch needed file(s)
     gen-env            Adding sensitive data to .env
     update             Update netbox
     wait               Waiting for migration to complete
EOF
}


RELEASE_FILE=./src/release.yaml
LOCAL_RELEASE_FILE=./src/local/release.yaml
OS_NAME=$(uname)
VERSION_ATOM=$(awk -F': *' '/^version/{gsub(/"/, "", $2); print $2}' $RELEASE_FILE)
GIT_COMMIT=$(git rev-parse --short HEAD)
ATOM_COMMIT_DATE=$(git show -s --format=%cd --date=short "$GIT_COMMIT")
LATEST_TAG=$(git tag --sort=-creatordate | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -n 1)

# Определяем переменную SED_INPLACE для команды sed в зависимости от ОС
if [ "$OS_NAME" == "Darwin" ]; then
    # macOS (BSD sed требует пустую строку после -i)
    SED_INPLACE="sed -i ''"
elif [ "$OS_NAME" == "Linux" ]; then
    # Linux (GNU sed)
    SED_INPLACE="sed -i"
else
    echo "Неизвестная операционная система: $OS_NAME"
    exit 1
fi

print-tenant() {
    echo "$TENANT"
}
# MARK: prepare-pgadmin
# Готовит pgadmin. Создает папку для последующего прокидывания в контейнер
# Так же помещает туда файл с паролем от pgadmin
# email см. в ENV_FILE
prepare-pgadmin() {
    mkdir -p "$LOCAL_PGADMIN_DATA"
    chmod -R 777 "$LOCAL_PGADMIN_DATA"
    if [ -z "$LOCAL_PGADMIN_PASSWORD_FILE" ]; then
        echo "Ошибка: переменная LOCAL_PGADMIN_PASSWORD_FILE не задана"
        exit 1
    elif [ -s "$LOCAL_PGADMIN_PASSWORD_FILE" ]; then
        echo "Файл $LOCAL_PGADMIN_PASSWORD_FILE существует и не пустой."
    else
        echo "Создаем локально папку $(dirname "$LOCAL_PGADMIN_PASSWORD_FILE")"
        mkdir -p "$(dirname "$LOCAL_PGADMIN_PASSWORD_FILE")"
        echo "Записываем пароль от БД в файл $LOCAL_PGADMIN_PASSWORD_FILE"
        echo "$DB_PASSWORD" > "$LOCAL_PGADMIN_PASSWORD_FILE"
        chmod 666 "$LOCAL_PGADMIN_PASSWORD_FILE"
    fi
}

# MARK: prepare-pgadmin
# Готовит atom. Создает папку для последующего прокидывания в контейнер
# Так же помещает туда файл с логами и дает права 777
prepare-atom() {
    local log_file="$LOCAL_ATOM_LOGS/django.log"
    if [ ! -f "$log_file" ]; then
        mkdir -p "$LOCAL_ATOM_LOGS"
        touch "$log_file"
        chmod 777 "$log_file"
    fi
}


# MARK: update-environment
# Внутрь используемого environment-файла помещает \ заменяет версию ATOM
update-environment() {
    echo "Текущая версия ATOM: $VERSION_ATOM (взято из файла $RELEASE_FILE)"
    echo "Открываем для редактирования ENVIRONMENT FILE по пути $ENV_FILE"
    if grep -q '^VERSION_ATOM=' "$ENV_FILE"; then
        echo "Строка VERSION_ATOM найдена, обновление значения..."
        sed -r "s/(VERSION_ATOM=)[^#]*(#.*)?/\1$VERSION_ATOM\2/" "$ENV_FILE" > .tmp_file && mv .tmp_file "$ENV_FILE"
        echo "Значение изменено. VERSION_ATOM=$VERSION_ATOM"
    else
        echo "Строка VERSION_ATOM не найдена, добавление новой строки VERSION_ATOM=$VERSION_ATOM"
        echo "VERSION_ATOM=$VERSION_ATOM" >> "$ENV_FILE"
        echo "Добавили"
    fi
}

# MARK: clean-local-release-yaml
# Удаляет локальный release.yaml
clean-local-release-yaml() {
    echo "Пытаемся удалить $LOCAL_RELEASE_FILE"
    if [ -f "$LOCAL_RELEASE_FILE" ]; then
        rm "$LOCAL_RELEASE_FILE"
        echo "Файл удален $LOCAL_RELEASE_FILE"
    else
        echo "Файл $LOCAL_RELEASE_FILE не существует."
    fi
}

#MARK: update-global-release
# редактируем глобальный release.yaml: меняем версию и дату релиза
update-global-release() {
    clean-local-release-yaml
    echo "Редактируем release.yaml по пути $RELEASE_FILE"
    if grep -q '^published:' "$RELEASE_FILE"; then
        echo "Строка published найдена, обновление значения..."
        sed -r "s/(published:)[^#]*(#.*)?/\1 \"$ATOM_COMMIT_DATE\"\2/" "$RELEASE_FILE" > .tmp_file && mv .tmp_file "$RELEASE_FILE"
    else
        echo "Строка published не найдена, добавление новой строки..."
        echo "published: \"published $ATOM_COMMIT_DATE\"" >> "$RELEASE_FILE"
    fi
    if grep -q '^version:' "$RELEASE_FILE"; then
        echo "Строка version найдена, обновление значения..."
        sed -r "s/(version:)[^#]*(#.*)?/\1 \"$VERSION_ATOM\"\2/" "$RELEASE_FILE" > .tmp_file && mv .tmp_file "$RELEASE_FILE"
    else
        echo "Строка version не найдена, добавление новой строки..."
        echo "version: \"version $VERSION_ATOM\"" >> "$RELEASE_FILE"
    fi
    echo "Редактирование release.yaml завершено"
}

#MARK: update-local-release
# после глобального редактируем локальный - добавляем git commit и прочее
update-local-release() {
    mkdir -p "$(dirname "$LOCAL_RELEASE_FILE")"
    echo "Копирование файла $RELEASE_FILE в $LOCAL_RELEASE_FILE..."
    cp "$RELEASE_FILE" "$LOCAL_RELEASE_FILE"
    echo "Редактируем LOCAL release.yaml по пути $LOCAL_RELEASE_FILE"
    if grep -q '^git_commit:' "$LOCAL_RELEASE_FILE"; then
        echo "Строка git_commit найдена, обновление значения..."
        sed -r "s/(git_commit:)[^#]*(#.*)?/\1 \"$GIT_COMMIT\"\2/" "$LOCAL_RELEASE_FILE" > .tmp_file && mv .tmp_file "$LOCAL_RELEASE_FILE"
    else
        echo "Строка git_commit не найдена, добавление новой строки..."
        echo "git_commit: \"$GIT_COMMIT\"" >> "$LOCAL_RELEASE_FILE"
    fi
    echo "Редактирование LOCAL release.yaml завершено"
}

#MARK: 
down() {
    echo "$ENV_FILE"
    docker compose --env-file "$ENV_FILE" down
}

up() {
    local sreda=${1:-"not_dev"} 
    prepare-atom
    prepare-pgadmin
    update-environment
    update-global-release
    if [[ "$sreda" == "dev" ]]; then
        update-local-release
    fi
    docker compose --env-file "$ENV_FILE" down
    docker compose --env-file "$ENV_FILE" up -d
}

devup() {
    # поднимаем так же, как up, просто +1 команда
    up "dev"
}

build() {
    clean-local-release-yaml
    update-environment
    update-global-release
    get_atom_image_name
    docker build -t "$ATOM_IMAGE_NAME" -f Dockerfile_atom .
}

get_atom_image_name() {
    ATOM_IMAGE_NAME=$(awk '/^[[:space:]]*atom:/{found=1} found && /^[[:space:]]*image:/{print $2; exit}' docker-compose.yml)
    ATOM_IMAGE_NAME=$(eval echo "$ATOM_IMAGE_NAME")  # Раскрытие переменной
    echo "ATOM Image Name: $ATOM_IMAGE_NAME"
}

find-up-version-patch-and-minor() {
    IV_MAJOR=$(echo "$VERSION_ATOM" | cut -d. -f1)
    IV_MINOR=$(echo "$VERSION_ATOM" | cut -d. -f2)
    IV_PATCH=$(echo "$VERSION_ATOM" | cut -d. -f3)
    if [ -z "$LATEST_TAG" ]; then
        UP_VER_PATCH="$((IV_MAJOR)).$((IV_MINOR)).$((IV_PATCH+1))"
        UP_VER_MINOR="$((IV_MAJOR)).$((IV_MINOR+1)).0"
        echo "Upgrade VERSION_ATOM (we no have tags): PATCH: $UP_VER_PATCH MINOR: $UP_VER_MINOR"
    else
        LT_MAJOR=$(echo "$LATEST_TAG" | cut -d. -f1)
        LT_MINOR=$(echo "$LATEST_TAG" | cut -d. -f2)
        LT_PATCH=$(echo "$LATEST_TAG" | cut -d. -f3)
        if [ "$LT_MAJOR" -gt "$IV_MAJOR" ] || \
           { [ "$LT_MAJOR" -eq "$IV_MAJOR" ] && [ "$LT_MINOR" -gt "$IV_MINOR" ]; } || \
           { [ "$LT_MAJOR" -eq "$IV_MAJOR" ] && [ "$LT_MINOR" -eq "$IV_MINOR" ] && [ "$LT_PATCH" -gt "$IV_PATCH" ]; }; then
            UP_VER_PATCH="$((LT_MAJOR)).$((LT_MINOR)).$((LT_PATCH+1))"
            UP_VER_MINOR="$((IV_MAJOR)).$((IV_MINOR+1)).0"
            echo "Upgrade VERSION_ATOM (used tag): PATCH: $UP_VER_PATCH MINOR: $UP_VER_MINOR"
        else
            UP_VER_PATCH="$((LT_MAJOR)).$((LT_MINOR)).$((LT_PATCH+1))"
            UP_VER_MINOR="$((IV_MAJOR)).$((IV_MINOR+1)).0"
            echo "Upgrade VERSION_ATOM (used release.yaml): PATCH: $UP_VER_PATCH MINOR: $UP_VER_MINOR"
        fi
    fi
    #echo "$NEW_VERSION_ATOM" > .tmp_file
}

upgrade-version() {
    local sreda=${1:-""}
    find-up-version-patch-and-minor
    if [[ "$sreda" == "dev" ]]; then
        VERSION_ATOM=$UP_VER_PATCH
        echo "среда $sreda , поднимаем версию патча $VERSION_ATOM"
    elif [[ "$sreda" == "prod" ]]; then
        VERSION_ATOM=$UP_VER_MINOR
        echo "среда $sreda , поднимаем минорную версию $VERSION_ATOM"
    else
        echo "Ошибка: Недопустимое значение sreda ($sreda). Допустимые значения: dev, prod"
        return 1  # Выход из функции с ошибкой
    fi
    update-global-release
    update-environment
}

get_last_commit_comment() {
    # Получаем последний хеш коммита слияния
    commit_mr_hash=$(git log --oneline --merges -n 1 | awk '{print $1}')
    # Получаем сообщение коммита (merge request'a)
    commit_mr_message=$(git show -s --format=%B $commit_mr_hash | sed -n '3p')
    # Возможно нужно будет использовать gitlabMergeRequestDescription
}

push-tag-version(){
    local sreda=${1:-""}
    local tag_for_create=${1:-""}
    if [[ "$sreda" == "dev" ]]; then
        tag_for_create="d$VERSION_ATOM"
    elif [[ "$sreda" == "prod" ]]; then
        tag_for_create="m$VERSION_ATOM"
    else
        echo "Ошибка: Недопустимое значение sreda ($sreda). Допустимые значения: dev, prod"
        return 1  # Выход из функции с ошибкой
    fi
    git tag -a "$tag_for_create" -m "$commit_mr_message"
    git push origin "$tag_for_create"
}

push-commit-version(){
    local sreda=${1:-""}
    get_last_commit_comment
    upgrade-version $sreda
    git add .
    git commit -m "Update release.yaml: v.$VERSION_ATOM, published: $ATOM_COMMIT_DATE"
    git push
}



# Артефакт, вдруг понадобится. Ищет env_file и следующую строку изменяет на значение $(ENV_FILE) с оригинальным количеством пробелов
replace_env_file() {
    echo "Начинаем обработку docker-compose.yml"
    FOUND=0
    LINE_NUM=0
    while IFS= read -r line; do
        LINE_NUM=$((LINE_NUM + 1))
        if echo "$line" | grep -q '^\s*env_file:'; then
            FOUND=1
            echo "Нашел 'env_file' на строке $LINE_NUM"
            IFS= read -r next_line
            LINE_NUM=$((LINE_NUM + 1))
            echo "Заменяем значение env_file на $ENV_FILE"
            NUM_SPC=$(echo "$next_line" | grep -o "^\s*" | wc -c)
            NEW_LINE=$(echo "$next_line" | sed "s/^\s*- .*/$(printf '%*s' "$NUM_SPC")- $ENV_FILE/")
            $SED_INPLACE "$((LINE_NUM)) s|.*|$NEW_LINE|" docker-compose.yml
        fi
    done < docker-compose.yml
    if [ "$FOUND" -eq 0 ]; then
        echo "Не нашел 'env_file'"
    fi
}

# Пример вызова функций
$@
