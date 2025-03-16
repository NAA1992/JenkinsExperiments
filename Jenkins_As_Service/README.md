# Jenkins As Service

## Ubuntu 24.04.2 LTS (Noble Numbat)

### Подготовка

* Установить openjdk не менее 17 версии
```bash
sudo apt-get install openjdk-17-jdk
```

* Убедиться что рабочая директория, указанная в узле (агенте), создана и доступна для пользователя, от имени которого будет запускаться Jenkins
Пример: рабочая директория /jenkins, тогда
```bash
sudo mkdir /jenkins
chmod 777 /jenkins
```

### Установка и запуск Jenkins службы
```bash
# Копируем jenkins.service в папку с нашими службами
sudo cp ./jenkins.service /etc/systemd/system/jenkins.service
# редактируем службу Jenkins
sudo vim jenkins.service
```
Менять нужно следующие строки:
* ExecStart - брать команду нужно из Jenkins, из узла (агента), изменяя путь к agent.jar
* User - на вашего пользователя
* Description - описание службы (опционально)

Далее выполняем команды
```bash
# перезагрузим все данные о службах
sudo systemctl daemon-reload
# запустим jenkins
sudo systemctl start jenkins
```