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
$credentialKey = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($credentialKey)
$env:CREDENTIALS_MASTER_KEY = [Convert]::ToBase64String($credentialKey)
./mvnw.cmd spring-boot:run
```

Для macOS или Linux используются те же переменные через `export`; JWT-ключ можно создать
командой `export JWT_SECRET="$(openssl rand -base64 32)"`, а приложение запустить через
`./mvnw spring-boot:run`.

`JWT_SECRET` обязателен, должен быть Base64-представлением ключа длиной не менее 32 байт
и не имеет значения по умолчанию. `JWT_ISSUER` по умолчанию равен `cloudops`,
`JWT_ACCESS_TOKEN_TTL` — `15m`.

`CREDENTIALS_MASTER_KEY` также обязателен и должен быть Base64-представлением ровно 32 случайных
байт. Это master key для AES-GCM шифрования credentials; его нельзя хранить в БД или коммитить.

## Credentials

`ResourceConfig` содержит адреса и несекретные параметры ресурса. `Credential` хранит отдельно
зашифрованный секрет подключения, а `ResourceCredential` связывает credential с ресурсом по
назначению `SSH` или `DATABASE`. Параметры Task и Probe не используются для передачи сохранённых
паролей и приватных ключей. API credentials никогда не возвращает plaintext или ciphertext.

`RABBITMQ_PASSWORD` обязателен. По умолчанию приложение подключается к
`localhost:5672` пользователем `cloudops`. Имена exchange, queue и routing key можно
переопределить через `TASK_EXCHANGE`, `TASK_QUEUE` и `TASK_ROUTING_KEY`. Для необрабатываемых
команд создаются durable DLX `cloudops.tasks.dlx` и DLQ `cloudops.task.execute.dlq` с routing key
`task.execute.dead`; их имена настраиваются через `TASK_DEAD_LETTER_EXCHANGE`,
`TASK_DEAD_LETTER_QUEUE` и `TASK_DEAD_LETTER_ROUTING_KEY`. RabbitMQ Management UI доступен по
адресу `http://localhost:15672` и используется только для локальной диагностики.

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

Допустимые типы: `NETWORK_DEVICE`, `SERVER`, `DATABASE`, `SERVICE`, `OTHER`.
Допустимые статусы: `ACTIVE`, `INACTIVE`.

## API задач

Task — самостоятельная конечная операция над Resource, потенциально долгая, асинхронная или
имеющая side effects. `TaskType` не равен `ProbeType`: диагностические `HTTP_CHECK`,
`PORT_CHECK`, `DNS_CHECK`, `PING`, `TLS_CHECK` и `SSH_CHECK` принадлежат только Monitoring и не
принимаются через `POST /api/resources/{resourceId}/tasks`. `RUN_COMMAND` — асинхронная Task
operation, которая выполняет одну команду по SSH на активном `SERVER` или `NETWORK_DEVICE` с
привязанным SSH credential. Запуск разрешён `OWNER` и `ADMIN`; `MEMBER` получает `403`.

Task capability — вычисляемая доступность operation для Resource и текущего пользователя.
Frontend получает её через `GET /api/resources/{resourceId}/task-capabilities` и не воспроизводит
backend rules самостоятельно. `supported` означает применимость operation к типу и config Resource,
`available` — выполнение текущих prerequisites, а `allowed` — permission пользователя. `reasons`
содержит все причины недоступности в стабильном порядке. Capability вычисляется по актуальным
Resource, credential binding и membership без сохранения в БД и без сетевого подключения.

```shell
curl -i http://localhost:8080/api/resources/1/task-capabilities \
  -H "Authorization: Bearer $TOKEN"
```

Каждый production `TaskType` имеет отдельный capability provider. Тот же provider используется
как pre-flight перед созданием Task, поэтому GET и POST применяют одинаковые operation rules.
Проверка в runtime handler сохраняется, поскольку Resource и credentials могут измениться между
созданием `PENDING` Task и фактическим выполнением.

```shell
curl -i -X POST http://localhost:8080/api/resources/1/tasks \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"RUN_COMMAND","parameters":{"command":"uname -a"}}'
```

Task хранит immutable snapshot параметров. Результат `RUN_COMMAND` содержит `exitCode`,
`stdout`, `stderr`, `durationMs` и `outputTruncated`. Ненулевой exit code является нормальным
завершением operation и даёт статус `COMPLETED`. Timeout и максимальный суммарный размер
сохранённого stdout/stderr задаются через `TASK_RUN_COMMAND_TIMEOUT` и
`TASK_RUN_COMMAND_MAX_OUTPUT_BYTES`.

`SSH_CHECK` только диагностирует SSH handshake/authentication в Monitoring. `RUN_COMMAND`
использует тот же низкоуровневый SSH connection/authentication код, но остаётся независимым
Task flow и может иметь внешний side effect.

API атомарно сохраняет Task и команду в PostgreSQL Transactional Outbox, после чего сразу
возвращает `202 Accepted`, `Location: /api/tasks/{id}` и Task в статусе `PENDING`.
Доступность RabbitMQ не влияет на создание Task: пока брокер недоступен, Task остаётся
`PENDING`, а relay периодически пытается опубликовать сохранённую команду. После
восстановления RabbitMQ операция выполняется асинхронно; итоговый статус и результат
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

Для `RUN_COMMAND` at-least-once означает, что после crash/recovery внешняя команда теоретически
может выполниться повторно. CloudOps не может автоматически сделать произвольный side effect
идемпотентным; при необходимости идемпотентность должна обеспечиваться самой командой.

Повторная доставка безопасна для текущего consumer-а: атомарный переход Task из `PENDING`
в `RUNNING` не позволяет повторно запустить handler для `RUNNING`, `COMPLETED` или `FAILED`.
Опубликованные outbox-записи автоматически не удаляются.

### Execution lease и recovery

При захвате Task consumer создаёт внутренний `executionId` и ограниченный по времени lease.
Общий heartbeat-планировщик продлевает lease активных локальных выполнений, включая время
retry backoff. Если процесс завершился после claim или во время handler-а, heartbeat пропадает,
и recovery job выбирает истёкшую `RUNNING` Task через PostgreSQL `FOR UPDATE SKIP LOCKED`.
В одной транзакции Task возвращается в `PENDING`, увеличивает `recoveryCount`, а в outbox
добавляется команда следующего поколения. Поэтому crash после recovery commit, но до RabbitMQ
publish обрабатывается обычным outbox relay.

`executionId` служит fencing token: запись attempt, продление lease и terminal update разрешены
только текущему выполнению. Вернувшийся старый consumer не перезапишет результат нового, однако
fencing защищает только состояние PostgreSQL. Внешний HTTP GET всё ещё может повториться;
гарантия всей цепочки остаётся **at-least-once**, а exactly-once для будущих side effects требует
отдельной бизнес-идемпотентности.

`recoveryCount` в Task API — число восстановлений после потерянного lease и не входит в
`attemptCount`, который считает фактические вызовы handler-а. После исчерпания лимита Task
получает `FAILED / RECOVERY_EXHAUSTED` и безопасное сообщение без новой execution command.

Параметры lease/recovery:

- `TASK_LEASE_ENABLED` (`true`);
- `TASK_LEASE_DURATION` (`30s`);
- `TASK_LEASE_HEARTBEAT_INTERVAL` (`10s`, строго меньше duration);
- `TASK_LEASE_RECOVERY_POLL_INTERVAL` (`15s`);
- `TASK_LEASE_RECOVERY_BATCH_SIZE` (`50`);
- `TASK_LEASE_MAX_RECOVERIES` (`3`).

Параметры relay:

- `TASK_OUTBOX_ENABLED` (`true`) — включает периодический relay;
- `TASK_OUTBOX_POLL_INTERVAL` (`500ms`) — пауза между циклами;
- `TASK_OUTBOX_BATCH_SIZE` (`50`) — максимальное число записей за цикл.

### Retry и Dead Letter Queue

Consumer захватывает Task один раз и выполняет retry только вокруг вызова handler-а. Retry
применяется к явно помеченным временным внутренним ошибкам CloudOps. Контролируемый
отрицательный результат конкретной операции сохраняется как `FAILED` без retry.

Параметры exponential backoff:

- `TASK_RETRY_ENABLED` (`true`);
- `TASK_RETRY_MAX_ATTEMPTS` (`3`) — общее число вызовов handler-а, включая первоначальный;
- `TASK_RETRY_INITIAL_INTERVAL` (`250ms`);
- `TASK_RETRY_MULTIPLIER` (`2.0`);
- `TASK_RETRY_MAX_INTERVAL` (`5s`).

Перед каждым вызовом handler-а Task атомарно увеличивает `attemptCount` и обновляет
`lastAttemptAt`. После исчерпания попыток Task получает `FAILED / RETRY_EXHAUSTED` с безопасным
общим сообщением, а исходная RabbitMQ-команда отклоняется без requeue и попадает в DLQ.
Non-retryable poison messages также сразу направляются в DLQ; terminal duplicate подтверждается
без повторного вызова handler-а.

Посмотреть сообщения можно в RabbitMQ Management UI: откройте `Queues and Streams`, затем очередь
`cloudops.task.execute.dlq` (или имя из `TASK_DEAD_LETTER_QUEUE`) и используйте `Get messages`.
Сообщение сохраняет исходный payload, outbox UUID в `message_id` и стандартный заголовок
`x-death`. Автоматического consumer-а и replay для DLQ пока нет: сообщения предназначены для
диагностики и остаются в очереди до ручного удаления.

## Monitoring

При создании ресурса приложение автоматически создаёт Monitor для каждого совместимого
`ProbeType`. При изменении type/config выполняется reconciliation: новые совместимые Monitor
добавляются, несовместимые больше не запускаются, а накопленная история не удаляется.
Ручной `POST /api/resources/{resourceId}/monitors` больше не используется.

Полные настройки каждого probe разрешаются одним уровнем в порядке
`Resource -> Organization -> Application`; значения разных уровней не смешиваются. Например,
задать policy для HTTP-проверок организации можно так:

```shell
curl -i -X PUT http://localhost:8080/api/organizations/1/monitoring-settings/HTTP_CHECK \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"enabled":true,"intervalSeconds":60,"failureThreshold":3,"recoveryThreshold":2,"storageMode":"HISTORY","retentionDays":30,"timeoutMs":5000}'
```

Полностью переопределить эту policy для одного ресурса:

```shell
curl -i -X PUT http://localhost:8080/api/resources/2/monitoring-settings/HTTP_CHECK \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"enabled":false,"intervalSeconds":120,"failureThreshold":4,"recoveryThreshold":2,"storageMode":"LATEST_ONLY","retentionDays":null,"timeoutMs":3000}'
```

`GET /api/organizations/{organizationId}/monitoring-settings` и
`GET /api/resources/{resourceId}/monitoring-settings` возвращают все probe types, источник
effective settings и совместимость с ресурсом. `DELETE` соответствующего endpoint удаляет
override и возвращает наследование с родительского уровня. Для `DNS_CHECK` поле `timeoutMs`
должно отсутствовать; для остальных probes оно обязательно.

`DNS_CHECK` и `PING` используют host у `SERVER`, `NETWORK_DEVICE`, `DATABASE` или host из URL
у `SERVICE`. `TLS_CHECK` использует настроенный порт host-ресурса либо HTTPS URL (`443` по
умолчанию); HTTP URL не поддерживается.

`PORT_CHECK` подтверждает доступность TCP endpoint. `SSH_CHECK` поддерживает `SERVER` и
`NETWORK_DEVICE`, выполняет SSH handshake и authentication через credential с purpose `SSH`,
открывает и закрывает session channel, но не запускает shell или произвольную команду. Для
`SERVER` используется отдельный `config.sshPort` (по умолчанию `22`), для `NETWORK_DEVICE` —
`managementPort` либо `22`, если он не задан. Password и private key никогда не сохраняются в
monitoring result. `SSH_CHECK` является только Probe и не является Task operation.

По умолчанию SSH host key принимается без проверки (`SSH_HOST_KEY_VERIFICATION=ACCEPT_ALL`),
как в ECCM, поэтому предварительно добавлять ресурс в `known_hosts` не требуется. Для строгой
проверки задайте `SSH_HOST_KEY_VERIFICATION=KNOWN_HOSTS`; путь к OpenSSH-файлу задаётся
`SSH_KNOWN_HOSTS_PATH` (по умолчанию `${user.home}/.ssh/known_hosts`).

Runtime/state и последний результат возвращает `GET /api/resources/{resourceId}/monitors`.
История в effective режиме `HISTORY` доступна через `POST /api/monitors/{id}/results/search`.

Probe — способ диагностической проверки Resource, а Monitor хранит runtime/state периодического
Probe. `POST /api/monitors/{id}/run` асинхронно ставит включённый Monitor на ближайший запуск:
атомарно выставляет отдельный `run_requested_at` и возвращает `202 Accepted` без тела. Будущий
периодический `nextRunAt` сохраняется. Повторные запросы до захвата объединяются в один;
при совпадении с наступившим периодическим запуском выполняется одна проверка. Probe
выполняется обычным scheduler через `MonitorExecutionService`, поэтому используются тот же
handler, настройки и правила хранения результата. Endpoint не создаёт Task, outbox-запись или
RabbitMQ-команду и не добавляет в результат признаки manual/source.

Несовпадение HTTP status сохраняется как завершённый probe с
`success=false`; timeout, DNS, connection и TLS ошибки сохраняются как failed probe result.
Планировщик сначала забирает ручные запросы, затем заполняет оставшуюся часть batch периодическими.
Захват атомарно очищает ручной маркер и сдвигает `nextRunAt` только для наступившего периодического
запуска. Отключение или потеря совместимости отменяет ожидающий ручной запрос.
Планировщик координирует экземпляры приложения через PostgreSQL
`FOR UPDATE SKIP LOCKED`. `enabled`, interval, thresholds, storage, retention и timeout на каждом
запуске берутся из effective settings; пропущенные во время downtime интервалы не воспроизводятся.

Settings сохраняются в PostgreSQL; runtime index обновляется после commit. При ошибке синхронизации
приложение логирует `monitoring_settings_synchronization_failed` и сохраняет затронутый ключ для
повторной попытки каждые `MONITORING_SETTINGS_RECOVERY_INTERVAL` (по умолчанию `30s`). Retry читает
актуальные settings из БД, восстанавливает индекс, расписание и health; уже существующее активное
расписание сохраняется. Повторный сбой оставляет ключ для следующей попытки. После restart settings
загружаются из БД, а monitors проходят startup reconciliation. Синхронизация и recovery сериализованы
в пределах одного процесса; invalidation между несколькими экземплярами приложения не реализован.

Scheduler использует partial indexes для совместимых monitors с `next_run_at IS NOT NULL`,
а для ручных запросов — дополнительно с `run_requested_at IS NOT NULL`. Интеграционный тест
`MonitorScheduleIndexIntegrationTest` проверяет `EXPLAIN` фактических claim-запросов на PostgreSQL
с 20 000 monitors, из которых только 10 доступны для запуска, без принудительного отключения sequential scan.

Обязательные Application defaults для всех `ProbeType` находятся в
`cloudops.monitoring.defaults` файла `application.yml` и валидируются при старте.

Параметры monitoring:

- `MONITORING_SCHEDULER_ENABLED` (`true`);
- `MONITORING_POLL_INTERVAL` (`5s`);
- `MONITORING_BATCH_SIZE` (`50`);
- `MONITORING_MINIMUM_INTERVAL_SECONDS` (`30`);
- `MONITORING_RETENTION_POLL_INTERVAL` (`1h`);
- `MONITORING_RETENTION_BATCH_SIZE` (`500`).
- `SSH_HOST_KEY_VERIFICATION` (`ACCEPT_ALL`);
- `SSH_KNOWN_HOSTS_PATH` (`${user.home}/.ssh/known_hosts`).

## Тесты

Интеграционные тесты сами запускают PostgreSQL и RabbitMQ в Testcontainers, поэтому Docker должен быть доступен.

Java-код форматируется через Spotless:

```powershell
./mvnw.cmd spotless:apply
./mvnw.cmd spotless:check
```

На macOS и Linux используйте `./mvnw` вместо `./mvnw.cmd`. Проверка форматирования также
выполняется в фазе `verify` и в CI.

Spotless использует Palantir Java Format 2.96.0. Чтобы `Ctrl+Alt+L` в IntelliJ IDEA создавал
тот же результат, установите плагин `palantir-java-format` из Marketplace и включите его для
проекта в `Settings | palantir-java-format Settings | Enable palantir-java-format`.

Windows:

```powershell
./mvnw.cmd verify
```

macOS или Linux:

```shell
./mvnw verify
```
