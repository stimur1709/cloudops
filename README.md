# CloudOps

Учебный backend-проект для управления инфраструктурными ресурсами на Java 25 и Spring Boot.

## Требования

- JDK 25;
- Docker с поддержкой Docker Compose.

Устанавливать Maven отдельно не нужно: в репозитории есть Maven Wrapper.

## Локальный запуск

Задайте параметры PostgreSQL и RabbitMQ. Пример для PowerShell:

```powershell
$env:POSTGRES_DB = "cloudops"
$env:POSTGRES_USER = "cloudops"
$env:POSTGRES_PASSWORD = Read-Host "PostgreSQL password"
$env:POSTGRES_PORT = "5432"
$env:RABBITMQ_USER = "cloudops"
$env:RABBITMQ_PASSWORD = Read-Host "RabbitMQ password"
$env:RABBITMQ_PORT = "5672"
$env:RABBITMQ_MANAGEMENT_PORT = "15672"
docker compose up -d

$env:POSTGRES_HOST = "localhost"
$env:RABBITMQ_HOST = "localhost"
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

`RABBITMQ_PASSWORD` обязателен. По умолчанию приложение подключается к
`localhost:5672` пользователем `cloudops`. Имена exchange, queue и routing key можно
переопределить через `TASK_EXCHANGE`, `TASK_QUEUE` и `TASK_ROUTING_KEY`. RabbitMQ Management UI
доступен по адресу `http://localhost:15672` и используется только для локальной диагностики.

При старте Liquibase автоматически создаёт схему, а Hibernate только проверяет её.

Остановить локальную инфраструктуру:

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

## API задач

Запустить HTTP-проверку активного ресурса типа `SERVICE`:

```shell
curl -i -X POST http://localhost:8080/api/resources/1/tasks \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"HTTP_CHECK"}'
```

API атомарно сохраняет Task и команду в PostgreSQL Transactional Outbox, после чего сразу
возвращает `202 Accepted`, `Location: /api/tasks/{id}` и Task в статусе `PENDING`.
Доступность RabbitMQ не влияет на создание Task: пока брокер недоступен, Task остаётся
`PENDING`, а relay периодически пытается опубликовать сохранённую команду. После
восстановления RabbitMQ HTTP-проверка выполняется асинхронно; итоговый статус и результат
нужно читать отдельно:

```shell
curl -i http://localhost:8080/api/tasks/1 \
  -H "Authorization: Bearer $TOKEN"
```

Возможные переходы статуса: `PENDING -> RUNNING -> COMPLETED` или
`PENDING -> RUNNING -> FAILED`. Поиск задач остаётся доступен через `POST /api/tasks/search`.

### Transactional Outbox

Relay читает неопубликованные записи ограниченными batch-ами в порядке `created_at, id`.
Каждая запись захватывается отдельной короткой транзакцией через PostgreSQL
`FOR UPDATE SKIP LOCKED`, поэтому несколько экземпляров приложения не публикуют одну строку
одновременно, а ошибка одной записи не блокирует остальные записи batch-а. Запись получает
`published_at` только после положительного RabbitMQ publisher confirm; exception, nack и
unroutable message оставляют её неопубликованной для следующего цикла.

Доставка имеет семантику **at-least-once**. Возможны следующие crash-сценарии:

- до publish транзакция освобождает блокировку, и relay повторяет попытку;
- после publish, но до сохранения `published_at`, команда может быть доставлена повторно;
- после commit `published_at` relay больше не выбирает запись.

Повторная доставка безопасна для текущего consumer-а: атомарный переход Task из `PENDING`
в `RUNNING` не позволяет повторно запустить handler для `RUNNING`, `COMPLETED` или `FAILED`.
Опубликованные outbox-записи автоматически не удаляются.

Параметры relay:

- `TASK_OUTBOX_ENABLED` (`true`) — включает периодический relay;
- `TASK_OUTBOX_POLL_INTERVAL` (`500ms`) — пауза между циклами;
- `TASK_OUTBOX_BATCH_SIZE` (`50`) — максимальное число записей за цикл.

## Тесты

Интеграционные тесты сами запускают PostgreSQL и RabbitMQ в Testcontainers, поэтому Docker должен быть доступен.

Windows:

```powershell
./mvnw.cmd verify
```

macOS или Linux:

```shell
./mvnw verify
```
