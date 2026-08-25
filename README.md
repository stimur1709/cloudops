# CloudOps

Учебный backend-проект для управления инфраструктурными ресурсами на Java 25 и Spring Boot.

## Требования

- JDK 25;
- Docker с поддержкой Docker Compose.

Устанавливать Maven отдельно не нужно: в репозитории есть Maven Wrapper.

## Локальный запуск

Задайте параметры PostgreSQL. Пример для PowerShell:

```powershell
$env:POSTGRES_DB = "cloudops"
$env:POSTGRES_USER = "cloudops"
$env:POSTGRES_PASSWORD = "cloudops"
$env:POSTGRES_PORT = "5432"
docker compose up -d

$env:DB_URL = "jdbc:postgresql://localhost:5432/cloudops"
$env:DB_USERNAME = $env:POSTGRES_USER
$env:DB_PASSWORD = $env:POSTGRES_PASSWORD
./mvnw.cmd spring-boot:run
```

Для macOS или Linux используются те же переменные через `export` и команда `./mvnw spring-boot:run`.
При старте Liquibase автоматически создаёт схему, а Hibernate только проверяет её.

Остановить локальную базу:

```shell
docker compose down
```

Чтобы также удалить локальные данные PostgreSQL, выполните `docker compose down --volumes`.

## API ресурсов

Создать ресурс:

```shell
curl -i -X POST http://localhost:8080/api/resources \
  -H "Content-Type: application/json" \
  -d '{"name":"router-01","type":"NETWORK_DEVICE","status":"ACTIVE"}'
```

Успешный ответ имеет статус `201 Created`, заголовок `Location: /api/resources/{id}` и тело:

```json
{
  "id": 1,
  "name": "router-01",
  "type": "NETWORK_DEVICE",
  "status": "ACTIVE",
  "createdAt": "2026-08-25T10:00:00Z",
  "updatedAt": "2026-08-25T10:00:00Z"
}
```

Получить ресурс:

```shell
curl -i http://localhost:8080/api/resources/1
```

Допустимые типы: `NETWORK_DEVICE`, `SERVER`, `DATABASE`, `OTHER`.
Допустимые статусы: `ACTIVE`, `INACTIVE`.

## Тесты

Интеграционные тесты сами запускают PostgreSQL в Testcontainers, поэтому Docker должен быть доступен.

Windows:

```powershell
./mvnw.cmd verify
```

macOS или Linux:

```shell
./mvnw verify
```
