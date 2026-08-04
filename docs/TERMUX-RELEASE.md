# Выпуск релиза из Termux

Эта инструкция повторяет безопасный релизный процесс PowerShell, но выполняется на Android. Основная ветка BaskovDiscordBot — `master`, репозиторий в Termux рекомендуется держать в `~/BaskovDiscordBot`.

## Однократная подготовка телефона

```bash
pkg update
pkg install git openssh openjdk-17 coreutils
termux-setup-storage
```

После `termux-setup-storage` файлы Android Downloads доступны по пути:

```text
/storage/emulated/0/Download/
```

Настрой имя автора коммитов:

```bash
git config --global user.name "Flawden"
git config --global user.email "YOUR_GITHUB_EMAIL"
```

Для push по SSH создай отдельный ключ телефона:

```bash
ssh-keygen -t ed25519 -C "termux-baskov-discord-bot"
cat ~/.ssh/id_ed25519.pub
```

Добавь только содержимое файла `~/.ssh/id_ed25519.pub` в GitHub SSH keys. Приватный файл `~/.ssh/id_ed25519` никому не отправляй.

Проверка подключения и первое клонирование:

```bash
ssh -T git@github.com
git clone git@github.com:Flawden/BaskovDiscordBot.git ~/BaskovDiscordBot
cd ~/BaskovDiscordBot
git switch master
```

## Применение очередного patch

В примере замени имя файла на patch текущего релиза:

```bash
PATCH="/storage/emulated/0/Download/baskov-discord-bot-vX.Y.Z-release-name.patch"
cd ~/BaskovDiscordBot
```

Подготовь чистую актуальную ветку:

```bash
git status
git switch master
git pull --ff-only origin master
```

Проверь SHA-256 по значению из релизного ответа:

```bash
sha256sum "$PATCH"
```

Обязательная проверка применимости:

```bash
git apply --check "$PATCH"
```

Только после успешной проверки:

```bash
git apply "$PATCH"
git diff --check
git diff --stat
git status --short
```

## Проверки и push

```bash
chmod +x ./mvnw
./mvnw --batch-mode --no-transfer-progress clean verify
```

После зелёных тестов:

```bash
git add -A
git diff --cached --check
git diff --cached --stat
git status

git commit -m "Release vX.Y.Z: Release Name"
git push origin master
```

После зелёного production deployment:

```bash
git tag -a vX.Y.Z -m "Baskov Discord Bot vX.Y.Z — Release Name"
git push origin vX.Y.Z
```

## Откат до commit

Когда patch уже применён, но commit ещё не создан:

```bash
git apply -R "$PATCH"
git status
```

Если есть только изменения этого релиза и их можно полностью выбросить:

```bash
git reset --hard HEAD
```

## Откат после push

Не переписывай историю `master`. Создай обратный commit:

```bash
cd ~/BaskovDiscordBot
git pull --ff-only origin master
git revert --no-edit HEAD
git push origin master
```

Delivery workflow соберёт обратный commit и вернёт production к предыдущему содержимому. Для точечного отката более старого релиза передай `git revert` его commit SHA.
