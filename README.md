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
$env:POSTGRES_PASSWORD = Read-Host "PostgreSQL password"
$env:POSTGRES_PORT = "5432"
docker compose up -d

$env:POSTGRES_HOST = "localhost"
$jwtKey = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($jwtKey)
$env:JWT_SECRET = [Convert]::ToBase64String($jwtKey)
$env:JWT_ISSUER = "cloudops-local"
./mvnw.cmd spring-boot:run
```

Для macOS или Linux используются те же переменные через `export`; JWT-ключ можно создать
командой `export JWT_SECRET="$(openssl rand -base64 32)"`, а приложение запустить через
`./mvnw spring-boot:run`.

`JWT_SECRET` обязателен, должен быть Base64-представлением ключа длиной не менее 32 байт
и не имеет значения по умолчанию. `JWT_ISSUER` по умолчанию равен `cloudops`,
`JWT_ACCESS_TOKEN_TTL` — `15m`.

При старте Liquibase автоматически создаёт схему, а Hibernate только проверяет её.

Остановить локальную базу:

```shell
docker compose down
```

Чтобы также удалить локальные данные PostgreSQL, выполните `docker compose down --volumes`.

## Аутентификация

Зарегистрировать пользователя:

```shell
curl -i -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","displayName":"Example User","password":"correct-horse-battery-staple"}'
```

Получить access token:

```shell
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"correct-horse-battery-staple"}'
```

Кроме регистрации и входа, API требует заголовок `Authorization: Bearer <token>`.
Роли `OWNER`, `ADMIN` и `MEMBER` проверяются по актуальным membership в PostgreSQL
и не сохраняются в JWT.

## API организаций

Создать организацию:

```shell
curl -i -X POST http://localhost:8080/api/organizations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Platform Team"}'
```

Доступны получение, изменение и удаление по `/api/organizations/{id}`, а также поиск через `POST /api/organizations/search`. Удалить организацию с привязанными ресурсами нельзя: API вернёт `409 Conflict`.

## API ресурсов

Создать ресурс:

```shell
curl -i -X POST http://localhost:8080/api/resources \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"router-01","type":"NETWORK_DEVICE","status":"ACTIVE","organizationId":1}'
```

Успешный ответ имеет статус `201 Created`, заголовок `Location: /api/resources/{id}` и тело:

```json
{
  "id": 1,
  "name": "router-01",
  "type": "NETWORK_DEVICE",
  "status": "ACTIVE",
  "organizationId": 1,
  "createdAt": "2026-08-25T10:00:00Z",
  "updatedAt": "2026-08-25T10:00:00Z"
}
```

Получить ресурс:

```shell
curl -i http://localhost:8080/api/resources/1 \
  -H "Authorization: Bearer $TOKEN"
```

Найти ресурсы с фильтрацией, сортировкой и смещением:

```shell
curl -i -X POST http://localhost:8080/api/resources/search \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "start": 0,
    "size": 20,
    "filter": {
      "operator": "AND",
      "conditions": [
        {"field": "type", "operation": "EQ", "value": "SERVER"},
        {"field": "name", "operation": "CONTAINS", "value": "prod"}
      ]
    },
    "sort": [
      {"field": "createdAt", "order": "DESC"}
    ],
    "getTotal": true
  }'
```

Поиски организаций и ресурсов дополнительно ограничиваются membership текущего пользователя.
Серверное ограничение применяется к `items` и `total` и всегда объединяется с клиентским
фильтром через `AND`, поэтому клиентский `OR` не позволяет получить чужие данные.

Поиск участников конкретной организации использует тот же контракт и endpoint
`POST /api/organizations/{organizationId}/members/search`. Фильтр по `organizationId`
добавляется на сервере и всегда объединяется с клиентским фильтром через `AND`, поэтому
клиент не может получить участников другой организации даже с оператором `OR`.

`start` должен быть не меньше `0`, `size` — от `1` до `100`. Ресурсы можно фильтровать и сортировать по полям `id`, `name`, `type`, `status`, `organizationId`, `createdAt`, `updatedAt`; организации — по `id`, `name`, `createdAt`, `updatedAt`. Поддерживаются операции `EQ`, `NE`, `GT`, `GE`, `LT`, `LE`, а для строк также `CONTAINS`. Если `getTotal` равен `false`, поле `total` отсутствует и отдельный запрос подсчёта не выполняется.

Поиск построен на общем framework в `common`: `SearchRequest` и `SearchResponse` задают единый API-контракт, а `JpaSearchService` собирает Criteria-запрос, применяет смещение и ограничение, и при необходимости выполняет `count`. Для каждой новой JPA-сущности нужно создать `JpaSearchDefinition` с явной картой внешних имён на типизированные `JpaSearchField`, выбрать конвертеры значений и поле сортировки по умолчанию. Клиентские имена полей не передаются напрямую в Criteria API, а поля entity не открываются через reflection.

Обновить ресурс:

```shell
curl -i -X PUT http://localhost:8080/api/resources/1 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"router-core-01","type":"NETWORK_DEVICE","status":"INACTIVE","organizationId":1}'
```

Удалить ресурс:

```shell
curl -i -X DELETE http://localhost:8080/api/resources/1 \
  -H "Authorization: Bearer $TOKEN"
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
