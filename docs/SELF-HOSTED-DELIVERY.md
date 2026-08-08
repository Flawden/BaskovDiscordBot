# Self-hosted delivery

Начиная с `v0.16.0` production delivery выполняется Linux self-hosted GitHub Actions runner-ом.

## Runner selection

Все jobs используют:

```yaml
runs-on: [self-hosted, linux, x64]
```

Runner должен быть зарегистрирован в репозитории BaskovDiscordBot, иметь Linux/x64 default labels и постоянно запущенный runner service. GitHub остаётся orchestration layer, а Maven/Docker вычисления выполняются на собственной машине.

## Persistent runner hygiene

Self-hosted workspace и home переживают jobs, поэтому deploy SSH key больше не записывается в `~/.ssh`. Workflow создаёт отдельный каталог через `mktemp` под `${RUNNER_TEMP}`, передаёт key/known_hosts в `ssh` и `scp` явно и удаляет каталог в `always()` step.

## Delivery path

1. Resolve delivery context on self-hosted runner.
2. Checkout, Java 17, Maven clean verify.
3. Buildx builds and pushes immutable `sha-<commit>` plus channel image to GHCR.
4. Deploy job uses a temporary SSH key to update the VPS.
5. Remote deployment keeps existing rollback/healthcheck behavior.

The runner must have Git, Java-compatible tooling required by actions, Docker Engine/Buildx access, outbound HTTPS to GitHub/GHCR/Maven repositories, and SSH access to the deployment VPS.
