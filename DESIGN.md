# CloudOps — Design System

Версия: 1.0 · Дата: 2026-09-09 · Статус: нормативная спецификация frontend.

## Purpose & Design principles

Цель документа — сделать CloudOps последовательной, доступной и информационно-плотной консолью управления инфраструктурой. Это source of truth для дизайнера, frontend-разработчика и AI-агента: токены определяют внешний вид, компоненты — повторяемое поведение, backend-контракт — смысл данных.

Направление: **precision infrastructure console / современный developer tool**. Пользователь должен быстро понять, что работает, где проблема, насколько свежи данные и какое действие доступно.

Обязательный стек: React + TypeScript + Vite + Tailwind CSS v4 + shadcn/ui на Radix UI + Lucide + TanStack Query/Table + React Hook Form + Zod + Recharts. Конкретные версии закрепляются в frontend lockfile при создании приложения; этот документ не предписывает неподтверждённые версии библиотек.

### Принципы

1. **Данные первичны.** Имена ресурсов, здоровье, задержки и время проверки важнее декоративных карточек и больших KPI.
2. **Спокойная иерархия.** Отличать уровни интерфейса поверхностью, отступом и тонкой границей; тень нужна только плавающим слоям.
3. **Цвет имеет смысл.** Violet/indigo обозначает продуктовые действия и выбор; статусы используют отдельные семантические цвета. Каждый статус подписан.
4. **Плотность без тесноты.** Группировать связанные данные, сохранять читаемую строку и достаточные интерактивные области. Не уменьшать шрифт ради количества строк.
5. **Предсказуемые действия.** Один основной CTA на контекст; одинаковое действие выглядит и называется одинаково во всём продукте.
6. **Честные состояния.** UNKNOWN, отсутствие данных, ошибка загрузки и DOWN — разные ситуации. Старые данные не выдаются за актуальные.
7. **Доступность по умолчанию.** Клавиатура, focus, подписи, контраст и reduced motion входят в контракт компонента.
8. **Общая система вместо локальных исключений.** Новый экран использует готовые токены и бизнес-компоненты; новое правило сначала описывается здесь.

### Референсы и границы

Это веса влияния из принятого направления, а не математическая смесь или утверждение о точных токенах чужих продуктов.

| Референс | Влияние | Что переносим в CloudOps |
| --- | ---: | --- |
| Linear | 45% | Плотность, навигационная иерархия, тихие поверхности, тонкие borders |
| Supabase | 20% | Ясность developer console, технические детали, сдержанность dark UI |
| Raycast | 10% | Command palette, клавиатурные сценарии, состояния взаимодействия |
| Resend | 10% | Читаемость событий, статусов и метаданных |
| Vercel | 5% | Монохромная иерархия и дисциплина light theme |
| SST | 5% | Аккуратное использование технической типографики |
| PostHog | 5% | Организация сложных данных и фильтров |

Не использовать generic SaaS-паттерны с огромными карточками и иллюстрациями, glassmorphism, glow, неоновые градиенты, декоративную терминальную стилизацию или копирование панели Grafana. Зелёный не становится цветом бренда. Ссылки на референсы не заменяют правила этого документа.

### Контекст репозитория

Спецификация размещается в корневом `DESIGN.md` репозитория `stimur1709/cloudops`. На проверенной базе `main` (`eb4cda4`) есть Java/Spring backend в `src/main/java`, Maven и документация API; frontend и `package.json` отсутствуют. Целевое расположение нового приложения — `frontend/`. Указанные далее frontend-пути описывают будущую структуру, а не уже реализованные файлы.

Документ задаёт дизайн, но не меняет backend и не добавляет отсутствующие endpoint. При расхождении UI-модели и API исправлять адаптер или согласовывать отдельную продуктовую задачу. Не подменять данные вымышленными значениями.

Проверенные источники доменных контрактов относительно корня репозитория:

- `src/main/java/com/github/stimur1709/cloudops/resource/api/ResourceResponse.java` — lifecycle `status` отдельно от `healthStatus`.
- `src/main/java/com/github/stimur1709/cloudops/monitoring/ResourceHealthStatus.java` — здоровье ресурса.
- `src/main/java/com/github/stimur1709/cloudops/monitoring/HealthStatus.java` — здоровье отдельного монитора.
- `src/main/java/com/github/stimur1709/cloudops/monitoring/application/ResourceHealthService.java` — серверная агрегация здоровья.
- `src/main/java/com/github/stimur1709/cloudops/monitoring/api/MonitorResponse.java` и `MonitoringResultResponse.java` — последние и исторические результаты.
- `src/main/java/com/github/stimur1709/cloudops/probe/execution/ProbeExecutionResult.java` — успешный/неуспешный результат проверки и ошибка выполнения.
- `src/main/java/com/github/stimur1709/cloudops/task/TaskStatus.java` и `task/api/TaskResponse.java` — жизненный цикл выполнения задач.
- `src/main/java/com/github/stimur1709/cloudops/monitoring/application/ResourceAvailabilityService.java` — формулы availability, uptime и coverage.

## Design Tokens

### Семантические имена и уровни

Использовать три уровня: значения палитры → семантическая роль → вариант компонента. HEX разрешён в централизованном файле токенов и в этом документе; компонент получает `bg-surface`, `text-foreground-muted`, `text-status-down`, а не `bg-zinc-950` или `text-red-400`.

Канонические CSS-переменные имеют имена `--background`, `--surface-raised`, `--status-up`. Tailwind aliases — `--color-background`, `--color-surface-raised`, `--color-status-up`. Имена описывают назначение, не текущий оттенок: не вводить `--purple-button` или `--dark-card`.

Области имён: `surface-*` — слои; `foreground-*` — текст; `border-*` — границы; `product-accent*` — бренд/выбор; `status-*` — состояние; `chart-*` — ряды и оси; `control-*`, `row-*`, `layout-*` — размеры; `motion-*` — движение.

### Нейтральные цвета

| Token | Dark | Light | Применение |
| --- | --- | --- | --- |
| `background` | `#09090b` | `#fafafa` | Фон приложения |
| `surface` | `#0f1012` | `#ffffff` | Sidebar, панели, поля |
| `surface-raised` | `#151619` | `#ffffff` | Dialog, popover, command palette |
| `surface-hover` | `#1b1c20` | `#f4f4f5` | Hover строки/нейтрального control |
| `surface-active` | `#23242a` | `#eaeaee` | Нажатие нейтрального control |
| `border` | `#27282d` | `#e4e4e7` | Декоративные разделители и панели |
| `border-strong` | `#34353b` | `#d4d4d8` | Усиленный разделитель, hover панели |
| `border-control` | `#787884` | `#82828c` | Граница, необходимая для распознавания поля/unchecked control |
| `foreground` | `#f4f4f5` | `#18181b` | Основной текст |
| `foreground-muted` | `#a1a1aa` | `#62626c` | Подписи, вторичная информация |
| `foreground-subtle` | `#9898a3` | `#696974` | Третичный, но читаемый текст |
| `decoration-muted` | `#71717a` | `#a1a1aa` | Только несущественная декорация |

Черновые `foreground-subtle` и light muted уточнены ради контраста маленького текста на hover/elevated поверхностях. `decoration-muted` сохраняет исходные тихие оттенки, но запрещён для timestamps, units, placeholder, helper text и других сведений, которые нужно прочитать. `border`/`border-strong` не обеспечивают контраст интерактивного control: там использовать `border-control` или другую проверенную форму обозначения границы.

### Product accent и взаимодействия

| Token | Dark | Light | Назначение |
| --- | --- | --- | --- |
| `product-accent-base` | `#7c6df2` | `#7c6df2` | Исходный брендовый оттенок, не универсальный цвет текста |
| `product-accent` | `#a89bff` | `#6955cc` | Ссылки, выбранный индикатор, focus ring |
| `product-accent-soft` | `#252039` | `#f0edff` | Выбранная строка/пункт |
| `primary` | `#6d5bd0` | `#6d5bd0` | Заливка основного CTA |
| `primary-hover` | `#6251bd` | `#6251bd` | Hover основного CTA |
| `primary-active` | `#5949ad` | `#5949ad` | Нажатие основного CTA |
| `primary-foreground` | `#ffffff` | `#ffffff` | Текст на основном CTA |
| `destructive` | `#b42332` | `#b42332` | Заливка подтверждения удаления |
| `destructive-hover` | `#9f1e2b` | `#9f1e2b` | Hover удаления |
| `destructive-foreground` | `#ffffff` | `#ffffff` | Текст на destructive CTA |

`#7c6df2` не сочетать автоматически с белым мелким текстом: для залитых кнопок предусмотрен более тёмный `primary`. Не окрашивать акцентом все иконки, заголовки и панели. Акцент обычно занимает малую часть видимого интерфейса: основной CTA, focus и один текущий выбор. Текст выбранного пункта — `foreground`; его индикатор — `product-accent`.

Стандартное поведение controls:

| Состояние | Оформление |
| --- | --- |
| Default | Surface + foreground + соответствующая граница |
| Hover | Surface-hover либо primary-hover; без движения элемента |
| Pressed | Surface-active либо primary-active |
| Selected | Accent-soft + видимый индикатор/check + `aria-selected` где применимо |
| Focus-visible | Непрозрачный outline `product-accent`, 2 px, offset 2 px |
| Invalid | Граница status-down + текст ошибки + `aria-invalid` |
| Disabled | Уменьшение opacity до 0.5 только на недоступном control; причина вне dimmed области |
| Pending action | Spinner + глагол процесса, фиксированная ширина, повторный submit заблокирован |

### Status language

| Состояние | Dark token | Light token | Подпись и смысл |
| --- | --- | --- | --- |
| UP / success | `#3ad389` | `#13764b` | `UP` — подтверждённое рабочее состояние; success — завершённое успешное действие |
| DEGRADED / warning | `#f5b942` | `#8a5900` | `DEGRADED` — частичная работоспособность ресурса; warning — требующее внимания условие |
| DOWN / failure | `#f87171` | `#ba2737` | `DOWN` — подтверждённая недоступность; failure — неуспешное действие |
| UNKNOWN / neutral | `#a1a1aa` | `#62626c` | `UNKNOWN` — недостаточно подтверждённых данных |
| RUNNING / info | `#60a5fa` | `#245fbb` | `RUNNING` — выполняется операция; info — информационное сообщение |

Канонические переменные: `--status-up`, `--status-degraded`, `--status-down`, `--status-unknown`, `--status-running`. Они подходят для значимого dot, текста и линии графика на нейтральных поверхностях. Мягкие подложки `--status-<name>-soft` вычисляются в токенах: 10% соответствующего status + 90% surface. На них предпочтителен основной `foreground`, статусный цвет остаётся в dot/иконке. Не применять общую opacity ко всему статусу.

Основной формат — маленький dot 6 px + gap 8 px + текст `UP`, `DEGRADED`, `DOWN`, `UNKNOWN`. Dot — декоративный элемент с `aria-hidden`; текст несёт смысл. Самостоятельный dot разрешён только при доступной подписи и дублировании статуса в доступном контексте; его hit area при интерактивности остаётся не меньше размера control.

Большой цветной pill не использовать в обычной таблице или карточке. `emphasis="soft"` с мягкой подложкой допустим для выбранного фильтра и локального предупреждения; не превращать все статусы в badges. Формат не меняется между темами.

Разделять три оси:

- `Resource.status`: `ACTIVE | INACTIVE` — административное состояние; нейтральная подпись Active/Inactive. ACTIVE не означает UP, INACTIVE не означает DOWN.
- `Resource.healthStatus`: `UP | DEGRADED | DOWN | UNKNOWN`. Отображать серверное значение. Сейчас сервер агрегирует совместимые включённые мониторы: смесь UP и DOWN → DEGRADED; только известные UP → UP; только известные DOWN → DOWN; нет известных → UNKNOWN. UNKNOWN рядом с UP сам по себе не даёт DEGRADED. UI не пересчитывает агрегацию.
- `Monitor.healthStatus`: `UP | DOWN | UNKNOWN`; DEGRADED не добавляется к этому API enum. `Task.status`: `PENDING | RUNNING | COMPLETED | FAILED`; это отдельная модель.

`RUNNING` никогда не заменяет health. При обновлении возможно `● DOWN · Проверка выполняется`: прошлый подтверждённый результат и выполнение представлены отдельно. Сам факт HTTP-запроса frontend не доказывает, что probe выполняется на сервере; такой индикатор называется «Обновление данных».

Отсутствие результата → UNKNOWN с причиной «Проверка ещё не выполнялась». Сетевая ошибка загрузки → error state. Старые данные → последнее здоровье + подпись свежести. Новое неизвестное значение enum → нейтральная подпись «Неизвестный статус» с доступным raw value в деталях; не приводить его к UP.

### Spacing, radius, borders, shadows

| Spacing | Tailwind | Назначение |
| ---: | --- | --- |
| 0 | `p-0`, `gap-0` | Сброс, примыкание |
| 4 px | `p-1`, `gap-1` | Тесно связанные строки/label и hint |
| 8 px | `p-2`, `gap-2` | Icon + label, соседние controls |
| 12 px | `p-3`, `gap-3` | Ячейки, компактные группы |
| 16 px | `p-4`, `gap-4` | Panel padding, поля формы |
| 24 px | `p-6`, `gap-6` | Группы полей, page padding |
| 32 px | `p-8`, `gap-8` | Отдельные смысловые секции |
| 48 px | `p-12`, `gap-12` | Редкие крупные разделения |

Разрешены только эти spacing utilities для margin/padding/gap и их responsive варианты. `space-*` следует той же шкале. Дробные utility (`p-2.5`), случайные `gap-5` и arbitrary `p-[17px]` запрещены. Размеры controls, иконок, hairlines и графиков — отдельные размерные токены, а не расширение spacing-шкалы.

| Radius token / utility | Размер | Применение |
| --- | ---: | --- |
| `none` / `rounded-none` | 0 | Внутренние строки таблицы, примыкающие края |
| `sm` / `rounded-sm` | 4 px | Code fragments, маленькие inset блоки |
| `control` / `rounded-control` | 6 px | Button, input, menu item |
| `panel` / `rounded-panel` | 8 px | Card, panel, popover |
| `dialog` / `rounded-dialog` | 12 px | Dialog, command palette |
| `full` / `rounded-full` | 999 px | Dot, avatar; pill лишь по разрешённому варианту |

Граница — 1 px solid. Разделители нейтральные; цветная рамка всей карточки допустима только в явно выбранном/ошибочном интерактивном состоянии. Ошибка здоровья ресурса сама по себе не делает всю карточку красной.

| Shadow token | Dark | Light |
| --- | --- | --- |
| `shadow-none` | `none` | `none` |
| `shadow-popover` | `0 4px 16px rgb(0 0 0 / 0.24)` | `0 4px 16px rgb(24 24 27 / 0.08)` |
| `shadow-dialog` | `0 12px 32px rgb(0 0 0 / 0.32)` | `0 12px 32px rgb(24 24 27 / 0.12)` |

Обычные панели — без тени. Overlays имеют непрозрачную поверхность и scrim `rgb(0 0 0 / 0.56)` dark / `rgb(24 24 27 / 0.32)` light. Scrim не является glassmorphism; backdrop blur запрещён.

## Themes

Обе темы обязательны и функционально равноправны. Dark — основное направление макетов, light — самостоятельная проверенная палитра. Настройка пользователя: `system | light | dark`, по умолчанию `system`; при недоступном media query — light. Хранить только предпочтение, вычисленную тему применять к `html` через `.dark` и `color-scheme` до первого paint. В режиме system реагировать на смену системной темы. Не хранить отдельные цвета в localStorage.

Компоненты обращаются к семантическим aliases без цепочек `dark:bg-...`. Dark variant нужен для редких структурных иллюстраций, а не для раскраски каждой строки. Portal содержимое наследует тему от `html`. Смена темы не анимирует цвета графиков и не сбрасывает фильтры/выбор.

Ниже каноническая цветовая основа будущего `frontend/src/styles/tokens.css`. Все theme overrides находятся здесь; таблицы выше и этот блок должны меняться в одном PR.

```css
:root {
  color-scheme: light;
  --background: #fafafa;
  --surface: #ffffff;
  --surface-raised: #ffffff;
  --surface-hover: #f4f4f5;
  --surface-active: #eaeaee;
  --border: #e4e4e7;
  --border-strong: #d4d4d8;
  --border-control: #82828c;
  --foreground: #18181b;
  --foreground-muted: #62626c;
  --foreground-subtle: #696974;
  --decoration-muted: #a1a1aa;
  --product-accent-base: #7c6df2;
  --product-accent: #6955cc;
  --product-accent-soft: #f0edff;
  --primary: #6d5bd0;
  --primary-hover: #6251bd;
  --primary-active: #5949ad;
  --primary-foreground: #ffffff;
  --destructive: #b42332;
  --destructive-hover: #9f1e2b;
  --destructive-foreground: #ffffff;
  --status-up: #13764b;
  --status-degraded: #8a5900;
  --status-down: #ba2737;
  --status-unknown: #62626c;
  --status-running: #245fbb;
  --chart-1: #6955cc;
  --chart-2: #745c46;
  --chart-3: #62626c;
  --chart-4: #a03d79;
  --overlay: rgb(24 24 27 / 0.32);
  --elevation-popover: 0 4px 16px rgb(24 24 27 / 0.08);
  --elevation-dialog: 0 12px 32px rgb(24 24 27 / 0.12);
}

.dark {
  color-scheme: dark;
  --background: #09090b;
  --surface: #0f1012;
  --surface-raised: #151619;
  --surface-hover: #1b1c20;
  --surface-active: #23242a;
  --border: #27282d;
  --border-strong: #34353b;
  --border-control: #787884;
  --foreground: #f4f4f5;
  --foreground-muted: #a1a1aa;
  --foreground-subtle: #9898a3;
  --decoration-muted: #71717a;
  --product-accent: #a89bff;
  --product-accent-soft: #252039;
  --status-up: #3ad389;
  --status-degraded: #f5b942;
  --status-down: #f87171;
  --status-unknown: #a1a1aa;
  --status-running: #60a5fa;
  --chart-1: #a89bff;
  --chart-2: #c8b39e;
  --chart-3: #a1a1aa;
  --chart-4: #e292c2;
  --overlay: rgb(0 0 0 / 0.56);
  --elevation-popover: 0 4px 16px rgb(0 0 0 / 0.24);
  --elevation-dialog: 0 12px 32px rgb(0 0 0 / 0.32);
}

/* Повторное объявление aliases в обоих scope сохраняет локальное разрешение var. */
:root, .dark {
  --status-up-soft: color-mix(in srgb, var(--status-up) 10%, var(--surface));
  --status-degraded-soft: color-mix(in srgb, var(--status-degraded) 10%, var(--surface));
  --status-down-soft: color-mix(in srgb, var(--status-down) 10%, var(--surface));
  --status-unknown-soft: color-mix(in srgb, var(--status-unknown) 10%, var(--surface));
  --status-running-soft: color-mix(in srgb, var(--status-running) 10%, var(--surface));
  --card: var(--surface);
  --card-foreground: var(--foreground);
  --popover: var(--surface-raised);
  --popover-foreground: var(--foreground);
  --secondary: var(--surface-hover);
  --secondary-foreground: var(--foreground);
  --muted: var(--surface-hover);
  --muted-foreground: var(--foreground-muted);
  /* shadcn accent — нейтральный hover, не продуктовый violet. */
  --accent: var(--surface-hover);
  --accent-foreground: var(--foreground);
  --input: var(--border-control);
  --ring: var(--product-accent);
  --sidebar: var(--surface);
  --sidebar-foreground: var(--foreground);
  --sidebar-primary: var(--primary);
  --sidebar-primary-foreground: var(--primary-foreground);
  --sidebar-accent: var(--product-accent-soft);
  --sidebar-accent-foreground: var(--foreground);
  --sidebar-border: var(--border);
  --sidebar-ring: var(--ring);
  --chart-grid: var(--border);
  --chart-axis: var(--foreground-muted);
}
```

Разница яркости surface не должна быть единственным способом обозначить открытый dialog или выбранную строку. Использовать border/overlay, заголовок, focus и явный индикатор выбора. Disabled opacity не применяется к read-only данным.

## Typography

Основной шрифт — **Geist Sans**, технический — **Geist Mono**. Загружать локальные WOFF2 с `font-display: swap`, сохранять лицензию поставки. Проверять кириллицу и смешанные строки; если выбранный font asset не содержит нужных глифов, использовать согласованный fallback, а не имитировать начертание. Sans fallback: `Inter, "Segoe UI", sans-serif`; Mono: `"Cascadia Code", Consolas, monospace`.

| Роль / semantic class | Размер / line-height | Weight | Назначение |
| --- | --- | ---: | --- |
| `text-page-title` | 24 / 32 px | 600 | Один H1 страницы |
| `text-section-title` | 18 / 24 px | 600 | H2 смысловой секции |
| `text-card-title` | 14 / 20 px | 500 | Заголовок панели/карточки |
| `text-body` | 14 / 20 px | 400 | Основной UI, таблицы, описание |
| `text-label` | 13 / 20 px | 500 | Label поля, header таблицы, control |
| `text-caption` | 12 / 16 px | 400 | Время, метаданные, hint |
| `text-technical` + `font-mono` | 13 / 20 px | 400 | Технические значения |
| `text-technical-sm` + `font-mono` | 12 / 16 px | 400 | Компактные технические метаданные |
| `text-metric` + `font-mono` | 24 / 32 px | 500 | Редкая сводная метрика; не все значения таблицы |

В CSS размеры задавать в rem при базовых 16 px. Не уменьшать root font-size. Page title может иметь tracking -0.02em; остальной UI — normal. Uppercase разрешён для raw кодов и коротких health/task статусов, но не для всех заголовков.

Mono применяется к `HTTP_CHECK`, IP, порту, HTTP status code, latency, timestamps, duration, percentages, командам, UUID и числовым техническим идентификаторам. Имена ресурсов, навигация, кнопки, тексты ошибок, health labels и подписи метрик остаются Sans. Смешанная строка: label Sans + value Mono. Табличные числа — `tabular-nums`, выравнивание вправо; название и статус — влево.

Форматирование выполняется общими функциями с одной выбранной локалью:

- `42 ms`, `1.24 s`, `99.95%`, `443`, `200`, `192.0.2.10`; фиксировать единицу в колонке или рядом с числом. Эти значения — иллюстрации, не данные продукта.
- Latency: целые ms для значений ≥1 ms, до двух знаков для меньших; секунды при ≥1000 ms. Tooltip/детали сохраняют точность исходного измерения.
- Проценты: до двух знаков в обзоре, исходная точность в деталях. Не округлять `99.995%` до идеальных `100%`: показывать `<100%` с точным tooltip. Аналогично малое ненулевое значение — `<0.01%`.
- Ноль — реальное `0`; `null`/отсутствие — `—` с причиной «Нет данных», никогда `0 ms`/`0%`.
- В таблице допустимо относительное время («12 с назад») с точным временем в доступной детали. В истории и графиках показывать timezone, например `UTC+07:00`; tooltip содержит дату, время и offset. API Instant не менять, локализовать только отображение.
- Полный IP/hostname должен быть доступен через details/copy. Длинные имена обрезать только при необходимости; доступное имя не сокращать. Не помещать существенные сведения только в hover tooltip.

## Layout

### Application shell и навигация

Desktop: sidebar слева, контекст организации сверху sidebar, main справа. Sidebar содержит Resources, Monitoring, Tasks; Settings и профиль отделены внизу. Пункт Monitoring может быть ресурсным разделом, пока нет общего API. Не показывать пустые разделы Incidents или отдельный каталог Probes без реализованного сценария; история health events не переименовывается в полноценную incident management систему.

В sidebar: logo/CloudOps нейтрального размера, organization switcher, иконка + текст пункта, активный пункт с accent-soft и узким индикатором. Счётчики — только полезные и достоверные; сбой загрузки счётчика не отображать как ноль. Навигация использует ссылки с настоящими URL и `aria-current="page"`.

| Layout token | Значение | Правило |
| --- | ---: | --- |
| `layout-sidebar` | 224 px | Развёрнутый desktop sidebar |
| `layout-rail` | 64 px | Компактный sidebar с доступными подписями |
| `layout-topbar` | min 48 px | Breadcrumb/context; растёт при переносе |
| `layout-content-max` | 1600 px | Табличные/overview экраны |
| `layout-reading-max` | 960 px | Детали и настройки |
| `layout-form-max` | 640 px | Основная форма |
| `layout-page-gutter` | 24 px desktop, 16 px mobile | Поля main |

PageHeader: breadcrumbs при вложенности → H1 + краткое описание → primary action и secondary actions. Ниже — tabs при наличии самостоятельных разделов, затем toolbar поиска/фильтров и контент. Не повторять имя ресурса в H1, огромном hero и заголовке первой карточки одновременно.

Resource details: сводка имени и health; вкладки Overview, Monitors, Tasks, History, Settings по реально доступным данным. Связанные данные открываются по ссылке; Back восстанавливает фильтры, страницу и по возможности позицию списка. Search, sort, filters, page и time range должны иметь сериализуемое URL-состояние. Названия маршрутов согласуются при реализации; отдельный router не предписывается выбранным стеком.

### Responsive и density

| Диапазон | Поведение |
| --- | --- |
| ≥1280 px | Sidebar 224; многоколоночные summaries; таблица — основной список |
| 1024–1279 px | Rail 64; labels через tooltip и accessible name; сложные панели могут стать одной колонкой |
| 768–1023 px | Навигация в Sheet; main на всю ширину; таблица со скрываемыми вторичными колонками |
| <768 px | Sheet navigation; одна колонка; PageHeader и toolbar переносятся; ResourceCard по умолчанию |

Брейкпоинты — `md: 48rem`, `lg: 64rem`, `xl: 80rem`. Контент должен работать при 320 CSS px. Карточки переключаются по ширине доступной области, если sidebar/split view делает её уже; не полагаться только на физическую ширину монитора.

На телефоне сохранять имя, health, свежесть и доступ к действиям. Вторичные поля переносить в details, не удалять из продукта. Для специальных технических таблиц допустим горизонтальный scroll внутри подписанной области; страница целиком не должна прокручиваться по горизонтали. Чипы фильтров переносятся, controls не сжимаются до неразборчивого размера.

| Density | Control min-height | Table row min-height | Panel padding |
| --- | ---: | ---: | ---: |
| Comfortable (default) | 36 px | 44 px | 16 px |
| Compact (desktop opt-in) | 32 px | 36 px | 12 px |
| Coarse pointer / touch | 44 px | 48 px | 16 px |

Это минимумы: строка с двумя линиями может вырасти до 56 px и выше по содержимому. Compact меняет вертикальную геометрию, но не body font-size, focus и семантику. Touch override имеет приоритет над compact. Размерный токен `control-icon` следует высоте control; иконка внутри остаётся 16 px. Не использовать fixed height, обрезающий текст при масштабировании.

Z-index централизован: base 0, sticky 10, dropdown 20, overlay 40, dialog 50, tooltip 60, toast 70. Popover внутри dialog портируется в слой dialog и получает относительный слой выше его содержимого; глобальный dropdown 20 не должен оказаться под scrim. Sticky header не закрывает focused element; предусмотреть scroll-padding.

## Components

### Базовые primitives

shadcn/ui — принадлежащие проекту UI-компоненты, адаптированные к этим токенам; Radix UI обеспечивает поведение сложных интерактивных элементов. Выбирать Radix-варианты shadcn, не смешивать параллельную библиотеку primitives. Стандартный registry style не является дизайном CloudOps.

| Primitives | Использование и обязательная адаптация |
| --- | --- |
| Button | Варианты primary, secondary, ghost, destructive, link; высота от density token |
| Input, Textarea, Label/Field | Общая геометрия, связанный label, hint/error; интеграция с RHF |
| Select, Checkbox, Switch, RadioGroup | Типизированный выбор; не изобретать аналог на `div` |
| Table | Семантическая разметка для TanStack Table; без собственной бизнес-логики |
| Card, Separator, Tabs | Нейтральные панели, разделение и локальная навигация |
| Dialog, AlertDialog, Sheet | Форма/детали, опасное подтверждение, мобильная навигация |
| DropdownMenu, Popover, Tooltip | Secondary actions, фильтры, дополнительные пояснения |
| Command | Поиск/команды; shadcn Command использует cmdk, Dialog — modal оболочка |
| ScrollArea, Collapsible | Длинные списки и раскрываемые технические детали |
| Skeleton, Alert, Progress | Загрузка, локальные сообщения, подтверждённый прогресс |
| Badge | Нейтральные metadata tags; не базовый health компонент |
| Breadcrumb, Pagination | Иерархия и серверная постраничная навигация |
| Sonner либо выбранный один toast wrapper | Краткий результат пользовательского действия |
| ChartContainer/ChartTooltip | При использовании shadcn Chart адаптировать к Recharts и общим токенам |

Не каждый shadcn компонент основан на Radix: Table/Card/Input остаются преимущественно HTML и стилями, Command использует cmdk, Chart — Recharts. Не устанавливать дублирующие реализации одной функции. Не добавлять React Aria, MUI, Ant Design или другую UI-систему ради единичного элемента.

### Buttons и iconography

Primary — одно главное действие текущей страницы или открытого dialog. Secondary — bordered neutral; ghost — вторичное действие в toolbar; destructive — только опасное действие. Ссылки в тексте имеют underline, а не только violet. Иконка без текста допустима для устоявшихся действий с `aria-label` и tooltip: «Скопировать IP», «Действия для prod-api».

Lucide: 16 px в строках и controls, 20 px в PageHeader и крупных controls, 24 px в EmptyState; stroke width 1.75, одинаковый во всём приложении. Разрешённые геометрические исключения: status dot 6 px, icon stroke 1.75 px, border 1 px, focus outline 2 px. Иконки используют `currentColor`. Не смешивать emoji, filled icon packs и случайные SVG со своим стилем.

Семантическая карта: Server/Database/Network/Globe — тип ресурса; Search — поиск; Plus — создание; Ellipsis — действия; Copy — копирование; ExternalLink — внешний переход; CircleCheck/TriangleAlert/CircleX/CircleHelp — расширенное объяснение статуса; LoaderCircle — выполнение. Значение иконки подтверждается текстом или доступным именем.

### Tables

Таблица — основной desktop способ сравнивать ресурсы. Использовать настоящий `table`, заголовки `th scope="col"`, доступный caption. Название ресурса — ссылка; row click может быть только дополнительным удобством и не перехватывает ссылки, checkbox, copy и menu. Не делать всю строку кнопкой с вложенными кнопками.

Header: label 13/500, secondary text, фон surface, border-bottom. Body: 14/400, технические значения 13 Mono. Горизонтальные разделители 1 px, без вертикальной сетки и zebra по умолчанию. Hover — surface-hover; selection — accent-soft + checkbox. Название выбранного ресурса остаётся foreground. Sticky header с непрозрачным фоном.

Числа выровнены вправо, единицы согласованы; строки — влево. Sort через кнопку в header и `aria-sort` на th; иконка показывает направление, доступное имя — «Сортировать по задержке». Null значения упорядочиваются по контракту сервера, не трактуются как ноль.

TanStack Table управляет columns, sorting, selection, visibility; данные и запросы находятся в TanStack Query. При серверной пагинации включать manual pagination/sorting/filtering. Не сортировать только текущую страницу, выдавая её за полный отсортированный набор. SearchRequest использует `start`, `size`, `filter`, `sort`, `getTotal`; размеры страниц 20/50/100 укладываются в текущий лимит API 100. При неизвестном `total` не показывать «из 0» или вымышленную последнюю страницу; доступность Next определяется контрактом ответа, пограничная пустая страница обрабатывается явно.

Поиск с debounce 250 ms, Enter применяет немедленно; этот таймер — поведенческий token, не motion. Изменение фильтра сбрасывает страницу, сохраняет сортировку. Показывать активные фильтры и «Сбросить фильтры». Selection действует на явно выбранные записи; checkbox в header выбирает текущую страницу и сообщает это. «Выбрать все результаты» допустимо только с реализованным серверным bulk-сценарием и явным количеством.

Column sizing — централизованная схема таблицы: name min 200 px, health min 128 px, type min 144 px, technical metric min 112 px, timestamp min 160 px, actions 44 px; checkbox 44 px при наличии. Это разрешённые размерные токены, не inline spacing. Пользовательский resize может задавать runtime width в px; сохранять по id колонки и не ломать minimums. Для списка с ограниченной пагинацией не добавлять виртуализацию без измеренной потребности.

### Forms

React Hook Form хранит состояние формы, Zod проверяет структуру и клиентские ограничения, backend остаётся источником бизнес-валидации. RHF Controller использовать для управляемых Radix controls. Не создавать второй локальный state для каждого поля без необходимости.

Label всегда видимый, над полем; между label и control 4 px, между полями 16 px, группами 24 px. Основная форма до 640 px, одна колонка; связанные короткие пары можно разместить в две колонки на desktop. Placeholder — пример, не замена label. Обязательность обозначается текстом или согласованной звёздочкой с объяснением; необязательные поля — «Необязательно».

Проверять при blur/submit, после первой ошибки — при исправлении; не красить нетронутое поле на первом символе. Ошибка рядом с полем связана через `aria-describedby`, control получает `aria-invalid`. Общая ошибка сохранения показывается внутри формы; при submit с ошибками фокус переходит на первое ошибочное поле или summary с ссылками на поля.

При pending сохранить введённые значения и контекст, кнопка «Сохранение…» предотвращает двойной submit. Успех закрывает dialog только после подтверждения API; ошибка не стирает форму. `409` показывать как конфликт с понятным действием обновления, не как неверный формат поля. При закрытии изменённой формы подтверждать потерю ввода. Read-only данные читаемы, копируемы и не оформлены как disabled input.

Конфигурация ресурса зависит от type: отображать только поддерживаемые поля, скрытое несовместимое значение не отправлять. Secrets в UI маскированы; наличие сохранённого credential показывается метаданными, без попытки прочитать секрет. В Task/Probe не добавлять поля сохранённых паролей, противоречащие backend-разделению credentials.

### Cards, panels и dialogs

Panel = surface + border + radius-panel + padding по density. Header отделяется gap 16 px, а не обязательной второй рамкой. Сетка карточек — только если пользователь изучает отдельные объекты; для массового сравнения использовать таблицу. Не вкладывать три карточки друг в друга ради отступов.

Dialog: min(viewport − 32 px, 480 px) для подтверждения, 640 px для формы, до 800 px для сложных деталей; максимальная высота viewport − 32 px, scroll внутри содержимого. Это `dialog-sm/md/lg` layout tokens. Header содержит title и при необходимости description, footer — primary/secondary action. На узком экране dialog растёт по доступной ширине; сложная длинная форма может открываться отдельной страницей. Sheet используется для навигации/кратких деталей, не для вложенной цепочки настроек.

Radix отвечает за focus trap, Escape и возврат focus. У AlertDialog первичный focus — безопасное действие. Подтверждение удаления называет объект и последствие: «Удалить ресурс prod-api?». Не использовать безымянное «Вы уверены?». Разрушительная команда из меню или palette сначала открывает подтверждение. Если trigger удалён вместе с объектом, focus возвращается на логический следующий control/заголовок списка.

### Command palette

Открывается по Ctrl+K / Cmd+K и видимой кнопке поиска. Центрированный Dialog до 640 px, верхняя часть viewport; высота списка ограничена 60vh. Внутри input, группы «Переходы», «Ресурсы», «Действия», результаты и подсказки клавиш. Имя результата Sans; тип/идентификатор Mono только при необходимости.

Стрелки перемещают active option, Enter выбирает, Escape закрывает. Результат содержит иконку, имя, контекст организации/раздела. Активная строка — accent-soft с явным индикатором, не цветной glow. Remote search имеет loading, no results и error; старый ответ не заменяет результаты более нового запроса. Никаких скрытых глобальных запросов ко всем чужим организациям. Недоступное действие не выполняется и показывает причину, если оно значимо для контекста.

### Бизнес-компоненты CloudOps

Компоненты ниже — единая публичная UI-поверхность. Они принимают типизированные view models, не вызывают endpoint самостоятельно; feature containers получают данные и передают команды. В компонентах нет универсального `color`/`statusColor` prop, произвольного `badgeClassName` или собственного словаря статусов.

| Компонент | Вход и ответственность | Визуальный/поведенческий контракт |
| --- | --- | --- |
| **HealthBadge** | `status: ResourceHealth`, `size: sm/md`, `emphasis: plain/soft`, необязательная `reason` | По умолчанию dot 6 px + Sans label 12/13 px; plain, без pill и тени. Значения только health. Причина доступна в деталях, не только hover |
| **StatusDot** | Семантический `tone`, accessible label для отдельного использования | Внутри HealthBadge скрыт от screen reader; отдельно — `role="img"` + label и текстовый эквивалент в контексте. Не пульсирует для UP/DOWN |
| **ResourceTable** | Rows, sorting/filter/page state, column visibility, selection и callbacks | Колонки name, health, type, lifecycle, actions; показатели и свежесть добавлять только при наличии данных. Использует HealthBadge и MetricValue. Header sort, loading, empty/error, responsive-приоритеты едины |
| **ResourceCard** | Resource view model, href, разрешённые actions | Header: type icon + name link + menu; следующая строка health и lifecycle; затем 2–3 существенных метаданных, свежесть если доступна. Body без giant KPI. Не вся card — вложенная интерактивная ссылка |
| **MonitorCard** | Monitor type, health, lastCheckedAt, nextRunAt, lastResult; effective settings отдельно при наличии | Имя типа например `HTTP_CHECK` Mono, описание Sans; ProbeStatus, последнее измерение, последняя/следующая проверка. Enabled/disabled — отдельная нейтральная настройка, не выводится из nextRunAt=null |
| **ProbeStatus** | `health: UP/DOWN/UNKNOWN`, необязательный подтверждённый execution state | HealthBadge для текущего health; blue RUNNING отображается отдельной подписью только если источник подтверждает исполнение. Не допускает DEGRADED для monitor health |
| **ProbeResult** | Discriminated view model из monitor type + result + checkedAt | Компактные key/value: latency, HTTP code, resolved addresses, port или TLS данные по типу. `success=false` виден как неуспех даже у завершённой проверки; error code Mono + понятная причина Sans. Null ≠ ошибка |
| **MetricValue** | `value: number/null`, `unit`, `kind`, precision policy, optional time/reason | Value Mono + tabular-nums, label/unit семантически связаны; размер body по умолчанию, metric только в summary. `—` для отсутствия; trend только с определённой базой сравнения |
| **AvailabilityChart** | From/to/timezone, summary из API, реальные intervals либо отдельное состояние unavailable | Timeline UP/DEGRADED/DOWN/UNKNOWN + легенда + coverage; без RUNNING в health timeline. Не строит историю по одному summary. Есть таблица интервалов и доступный summary |
| **PageHeader** | Title, optional description/breadcrumbs/meta, primary и secondary actions | Один H1, title 24/600, actions справа desktop и ниже mobile. Health рядом с именем, описание muted; не помещать toolbar фильтров внутрь H1 |
| **EmptyState** | `kind: first-use/filtered/no-history`, title, description, optional action | Небольшая нейтральная иконка 24 px, title 14/500, конкретный следующий шаг. Для filtered — сброс фильтров; для first-use — создать объект при наличии прав |
| **TaskStatus** | `status: PENDING/RUNNING/COMPLETED/FAILED`, attempts и времена при наличии | PENDING gray + Clock; RUNNING blue + LoaderCircle; COMPLETED green + CircleCheck; FAILED red + CircleX. Text обязательный; plain inline, без больших pills |

Детальные ограничения:

- ResourceResponse не содержит latency и lastCheckedAt. ResourceTable не выдумывает их из updatedAt; дополнительные данные получать согласованным источником без неограниченного N+1 запросов. Пока такого источника нет, не включать эти колонки по умолчанию. `updatedAt` означает изменение записи, а не свежесть probe.
- Monitor не создаётся автоматически в UI как произвольная сущность: действия согласовать с provisioning и effective settings API. Не добавлять кнопку «Создать монитор» без поддержанного сценария.
- ProbeResult адаптируется по `type`; один тип результата не трактовать как другой. `Completed(success=false, data)` означает проверку с неуспешным результатом, а `Failed(error)` — ошибку выполнения. Показать полезные полученные данные в обоих допустимых случаях; не объявлять Completed безусловным успехом.
- Task COMPLETED — «Выполнено», не UP; FAILED — «Ошибка», не DOWN ресурса. Тип операции `RUN_COMMAND` — Mono. PENDING может означать ожидание/повторную попытку; не рисовать линейный необратимый progress stepper и не выдумывать процент выполнения. attemptCount, recoveryCount показывать как технические детали.
- Удаление/запуск задачи зависит от прав и capability API. Не показывать Cancel/Retry/Run now, если операция отсутствует в контракте. Повторное создание задачи — отдельное пользовательское действие, не автоматический retry mutation.

Минимальные UI-типы (в будущем `frontend/src/lib/status.ts`; API-типы генерируются или выводятся отдельно):

```ts
export type ResourceHealth = "UP" | "DEGRADED" | "DOWN" | "UNKNOWN";
export type MonitorHealth = Exclude<ResourceHealth, "DEGRADED">;
export type TaskExecutionStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";
export type StatusTone = "up" | "degraded" | "down" | "unknown" | "running";

export const healthPresentation = {
  UP: { label: "UP", tone: "up", className: "text-status-up" },
  DEGRADED: { label: "DEGRADED", tone: "degraded", className: "text-status-degraded" },
  DOWN: { label: "DOWN", tone: "down", className: "text-status-down" },
  UNKNOWN: { label: "UNKNOWN", tone: "unknown", className: "text-status-unknown" },
} as const satisfies Record<ResourceHealth, {
  label: ResourceHealth;
  tone: StatusTone;
  className: string;
}>;
```

Словарь статических className позволяет Tailwind обнаруживать классы; не строить `text-status-${status}` во время исполнения. UI-тексты держать в общем locale dictionary. Примеры этого документа используют русские действия и английские технические enum; реальный экран следует выбранной локали и не смешивает два языка для одного действия.

## Data visualization

### Palette и типы графиков

Здоровье всегда использует status palette. Обычные ряды метрик — отдельную categorical palette, чтобы зелёная latency line не обещала UP, а синяя произвольная серия не означала running/info.

| Token | Dark / Light | Дополнительное различение |
| --- | --- | --- |
| `chart-1` | `#a89bff` / `#6955cc` | Violet, solid, circle |
| `chart-2` | `#c8b39e` / `#745c46` | Taupe, dash 6 3, square |
| `chart-3` | `#a1a1aa` / `#62626c` | Gray, dash 2 3, triangle |
| `chart-4` | `#e292c2` / `#a03d79` | Mauve, dash 8 3 2 3, diamond |

Идентичность ряда стабильна между экранами, фильтрами и темами: хранить mapping по metric key, не брать цвет из текущего индекса ответа. До четырёх одновременно видимых рядов; для большего числа — фильтр или small multiples с общей шкалой. Gray metric series отличается от UNKNOWN контекстом/легендой; не смешивать categorical series и health intervals в одной непонятной легенде.

График — средство ответа на вопрос, а не фон карточки. Для latency использовать line; area только с token fill-opacity 0.08 и сохранённой читаемой линией. Для сравнения категорий — bars от нулевой оси. Для health — ступенчатая временная полоса. Не использовать 3D, gauges без необходимости, gradient fills, donut вместо простого значения, сглаживание, создающее несуществующие пики.

Геометрические tokens: line stroke 2 px, grid 1 px, active marker 4 px, timeline height 32 px, chart min-height 240 px, compact chart 160 px. ResponsiveContainer получает родителя с определённой минимальной высотой и `min-width: 0`. Декоративная grid использует chart-grid; текст осей — chart-axis 12 px; числовые ticks/timestamps Mono, названия осей Sans.

X — реальное время с явным timezone; Y — единица и смысл агрегации (`Latency p95, ms`). Для latency не начинать ось с отрицательных значений. Если линия использует ненулевой минимум для изучения вариаций, диапазон явно виден; сравниваемые small multiples имеют одинаковый domain. Доли — 0–100%. Не совмещать разные единицы на скрытой второй оси.

Tooltip имеет surface-raised, border и shadow-popover: точное время, имя ряда, значение с единицей, статус/coverage при необходимости. Tooltip доступен по клавиатуре и tap, не выходит за viewport. Legend с текстовыми названиями и pattern; при переключении ряда использовать button с `aria-pressed`, не кликабельный span. Не делать сотни отдельных tab stops: фокус на график, перемещение по точкам стрелками.

Пропущенные значения — `null`, `connectNulls={false}`; не превращать пропуск в ноль или непрерывную линию. Реальное значение 0 рисуется. Не интерполировать health. При downsampling сохранять outages и локальные экстремумы, показывать bucket/aggregation в tooltip. Пустой диапазон имеет подпись «Нет измерений за выбранный период», а не идеальную ровную линию.

### AvailabilityChart: точная семантика

Backend возвращает summary, включая `upSeconds`, `degradedSeconds`, `downSeconds`, `unknownSeconds`, `knownSeconds`, `periodSeconds` и три процента:

```text
knownSeconds        = upSeconds + degradedSeconds + downSeconds
uptimePercent       = upSeconds / knownSeconds × 100
availabilityPercent = (upSeconds + degradedSeconds) / knownSeconds × 100
coveragePercent     = knownSeconds / periodSeconds × 100
```

Отображать серверные значения; формулы здесь объясняют подписи, а не требуют второй независимой реализации. При knownSeconds=0 uptime/availability равны null → `—`, coverage показывает действительную долю. «Доступность» включает DEGRADED, «Полная работоспособность» соответствует uptime; эти метрики нельзя подписывать одинаковым Uptime.

Рядом с availability всегда доступны период и coverage. Например, 100% availability при 10% coverage не означает 100% наблюдаемость: показывать «Покрытие данными: 10%». Не красить любое значение ≥99% зелёным без согласованного SLO. SLO/threshold annotation имеет явную подпись и известную конфигурацию, не придуманный порог.

Timeline представляет интервалы `[from, to)`: UP — solid green, DEGRADED — amber с диагональным pattern, DOWN — red с cross pattern, UNKNOWN — gray с dot pattern. Pattern реализуется общими SVG defs с уникальными id; chart/table legend повторяет образец и текст. RUNNING не относится к историческому health и не появляется в полосе availability.

Ширина сегмента пропорциональна длительности. Минимальная кликабельная область может быть больше узкого сегмента без изменения отображаемой длительности. Bucket со смешанными состояниями сохраняет их доли либо явно помечается «Смешанный интервал»; не присваивать ему средний статус. Tooltip содержит границы, duration, статус и источник данных.

Summary endpoint сам по себе не даёт хронологию. История строится только из полного набора health transitions за период и подтверждённого состояния на его начало, с учётом retention/границ доступности истории. Если начальный статус неизвестен, участок до первого подтверждённого события — UNKNOWN. Нельзя заполнять прошлое текущим UP. Если API не позволяет получить непротиворечивые интервалы, показать только summary и «История за период недоступна»; расширение API — отдельная задача.

Каждый график имеет текстовый summary и переключение к таблице исходных точек/интервалов с units и timezone. Экспорт данных допустим только в пределах прав и действительно загруженных/полученных данных; не заявлять полный экспорт по одной странице.

## States

### Матрица состояний данных

| Состояние | Представление | Поведение |
| --- | --- | --- |
| Initial loading | Skeleton повторяет геометрию ожидаемого блока | `aria-busy` на регионе, краткая доступная подпись; placeholder не выглядит как настоящая метрика |
| Background fetching | Последние данные + тихое «Обновление…» | Без замены таблицы skeleton и без сброса selection/scroll |
| First use empty | EmptyState «Пока нет ресурсов» | Создать ресурс при наличии прав |
| Filtered empty | «Ничего не найдено» + активные фильтры | «Сбросить фильтры», не повторять onboarding |
| No measurements/history | «Проверка ещё не выполнялась» / «Нет данных за период» | UNKNOWN либо отсутствие значения; пояснить источник |
| Initial request error | Локальный error panel + повторить | Не показывать empty/success; остальная shell доступна |
| Background error | Старые данные + сообщение о неудачном обновлении | Сохранять последнее известное health с временем |
| Partial error | Ошибка конкретной панели | Независимые панели продолжают работать |
| Offline | Нейтральное/информационное сообщение о соединении | Кэш только с подписью времени; данные не перекрашиваются в DOWN |
| Permission denied | «Недостаточно прав» с понятным контекстом | Без утечки скрытых данных; запросить доступ, только если есть реальный путь |
| Session expired | Сохранить безопасный контекст возврата, вход | Не выполнять бесконечный refresh/retry loop |
| Not found | «Ресурс не найден или недоступен» | Ссылка к списку без подтверждения чужого существования |

Skeleton: 5–8 строк для таблицы, ширины колонок соответствуют финальному контенту; цвет surface-hover, без яркой волны. Статичный вариант по умолчанию, допустимое мягкое изменение opacity только с reduced-motion fallback. Не показывать фиктивные имена, проценты и зелёные health dots при загрузке.

Freshness описывать отдельно: `fetchedAt` — когда ответ получен клиентом; `checkedAt/lastCheckedAt` — когда выполнялась проверка. Не смешивать их с resource updatedAt. Просроченность измерений определяется согласованной политикой effective interval + grace; пока политика не задана, выводить точное «Последняя проверка…», без самостоятельного изменения health. Порог обновления query cache не является порогом бизнес-деградации.

Ошибки: конкретная причина и действие, технический code/correlation id копируемы в details, stack traces и SQL не отображаются. Toast служит кратким подтверждением намеренного действия, например «IP скопирован»; ошибка формы остаётся внутри формы. Автообновление здоровья не порождает toast на каждом poll. Критичные сообщения не исчезают только по таймеру.

Разрешения проверяются сервером; скрытие кнопки не является защитой. Если действие полезно обнаружить, показать disabled control с соседней доступной причиной; неизвестные/неположенные разделы скрывать по политике продукта. При смене организации не оставлять на экране кэш другой организации; отменить старые запросы, сбросить selection, использовать scoped query keys.

### Motion

| Token | Значение | Использование |
| --- | --- | --- |
| `motion-fast` | 120 ms | Hover/focus decoration без задержки фокуса |
| `motion-base` | 160 ms | Popover, menu, мягкое появление |
| `motion-slow` | 200 ms | Dialog/Sheet |
| `motion-ease` | `cubic-bezier(0.2, 0, 0, 1)` | Все спокойные переходы |

Не использовать bounce, spring, бесконечное свечение статусов, движение карточек на hover и пересчитывающиеся цифры KPI. Анимировать opacity и при необходимости смещение до 4 px из общего motion token. Chart animation выключена при live refresh; смена данных не двигает оси без причины. `prefers-reduced-motion: reduce` отключает декоративные transitions, shimmer, transform и вращение spinner; текст «Выполняется» остаётся. Цвет статуса меняется без мигания.

## Accessibility

Цель — WCAG 2.2 AA. Базовые нормы: обычный текст ≥4.5:1, крупный ≥3:1, значимые границы controls и графические элементы ≥3:1 к соседнему фону. Минимальная pointer target — 24×24 CSS px с учётом применимых исключений стандарта; CloudOps по умолчанию задаёт более удобные controls 32/36 px и 44 px для touch. Эти требования проверяются на конечном фоне, включая hover/selected. [WCAG 2.2 Quick Reference](https://www.w3.org/WAI/WCAG22/quickref/).

Обязательные правила CloudOps:

- Цвет дополняет текст/форму/pattern. Status dot скрыт от screen reader, если рядом есть label. Статусы различимы в grayscale и при нарушениях цветовосприятия.
- Heading hierarchy: один H1, последовательные H2/H3; landmarks `header`, `nav`, `main`; skip link к контенту.
- Все действия доступны с клавиатуры. Настоящие button/link, явный focus-visible, логичный Tab order. Не ставить положительный tabindex.
- Focus outline 2 px с offset 2 px не обрезается overflow и не скрывается sticky/header/overlay. Не уменьшать opacity focus ring.
- Dialog имеет title/description, trap и возврат focus; меню, select и tabs сохраняют ожидаемую клавиатурную модель. Radix обеспечивает основу, но подписи и правильная композиция остаются обязанностью проекта. [Radix accessibility](https://www.radix-ui.com/primitives/docs/overview/accessibility).
- Form label программно связан с control. Placeholder, tooltip и цветная рамка не заменяют label/error. Tooltip открывается по focus и hover, закрывается Escape; важное объяснение есть и без него.
- Live regions — выборочно: результат явного действия `polite`, блокирующая ошибка при необходимости `alert`. Не объявлять каждую изменённую ячейку polling-таблицы.
- Таблицы сохраняют headers/caption, sorting state и текстовые названия action buttons. Выделение строки имеет checkbox; focus не сбрасывается при refetch.
- Charts получают доступный title/summary, keyboard navigation и таблицу данных. Включать `accessibilityLayer` явно в общей Recharts-обёртке и проверять фактическое поведение с custom tooltip; встроенная поддержка не заменяет альтернативу. [Recharts accessibility](https://github.com/recharts/recharts/wiki/Recharts-and-accessibility).
- Проверять 200% масштаб текста, reflow при 400% zoom и ширине 320 CSS px, длинные локализованные строки, reduced motion, forced colors. В forced colors разрешить системные цвета и outline; декоративные branded цвета уступают читаемости.
- UI с auto-refresh должен позволять приостановить живое обновление/смену видимых данных и явно показывать время снимка. После возобновления обновление не крадёт focus и не переставляет активную строку неожиданно.

## Implementation rules

### Структура и ответственность

При создании frontend использовать следующую структуру, адаптируя имена к уже принятой архитектуре, если приложение появилось после этой спецификации:

```text
DESIGN.md                         # единственная нормативная спецификация
frontend/
  components.json                 # shadcn, cssVariables: true, Radix-варианты
  vite.config.ts                  # React и Tailwind v4 Vite plugins
  src/
    app/                          # providers, shell, навигация
    styles/
      tokens.css                  # значения обеих тем
      globals.css                 # imports, @theme aliases, base, motion/density
    components/
      ui/                         # адаптированные shadcn primitives
      cloudops/                   # HealthBadge, MetricValue, PageHeader и др.
      charts/                     # единая Recharts-обёртка, tooltip, legend
    features/
      resources/                  # ResourceTable/Card, страницы и view adapters
      monitoring/                 # MonitorCard, ProbeResult, AvailabilityChart
      tasks/                      # TaskStatus и сценарии задач
    lib/
      api/                        # клиент API, DTO, error adapter
      status.ts                   # словари статусов, runtime fallback
      format.ts                   # единицы, точность, даты/timezone
```

Не создавать вторую копию DESIGN.md в frontend. При необходимости README приложения ссылается на `../DESIGN.md`. Токены в CSS — исполняемое представление спецификации; изменение одного требует синхронного изменения другого. UI primitives не импортируют feature logic; feature containers не копируют markup бизнес-компонентов ради иного цвета.

React Query хранит server state; RHF — ввод формы; TanStack Table — состояние представления таблицы; URL — разделяемый navigation/filter state. Локальный React state — открытый dialog, активная панель и прочие временные UI-состояния. Не добавлять глобальный store для дублирования query cache.

Query keys включают organization/resource scope и параметры запроса. Использовать cancel/AbortSignal при смене фильтра/организации, не подменять новый результат запоздавшим старым. Не сохранять access token в localStorage: текущий backend ожидает memory access token и HttpOnly refresh cookie. После logout очищать чувствительный query cache. Mutation запуска инфраструктурной операции не повторяется автоматически без гарантии идемпотентности.

### Tailwind CSS v4 и shadcn mapping

Vite использует `@tailwindcss/vite`; CSS подключает `@import "tailwindcss"`. Для theme variables и aliases применять CSS-first конфигурацию v4, без копирования инструкций `@tailwind base/components/utilities` из v3. [Tailwind with Vite](https://tailwindcss.com/docs/installation/using-vite).

Переменные `:root/.dark` связываются с utilities через `@theme inline`, поскольку значения ссылаются на другие переменные. [Tailwind theme variables](https://tailwindcss.com/docs/theme). shadcn настраивается через semantic CSS variables; стандартное имя `accent` оставляем для нейтрального hover, а product violet получает отдельный `product-accent`. [shadcn theming](https://ui.shadcn.com/docs/theming), [shadcn Tailwind v4](https://ui.shadcn.com/docs/tailwind-v4).

Базовая спецификация `globals.css` (оба import должны предшествовать обычным правилам):

```css
@import "tailwindcss";
@import "./tokens.css";

@custom-variant dark (&:where(.dark, .dark *));

@theme inline {
  /* Цвета shadcn и CloudOps */
  --color-background: var(--background);
  --color-foreground: var(--foreground);
  --color-foreground-muted: var(--foreground-muted);
  --color-foreground-subtle: var(--foreground-subtle);
  --color-decoration-muted: var(--decoration-muted);
  --color-surface: var(--surface);
  --color-surface-raised: var(--surface-raised);
  --color-surface-hover: var(--surface-hover);
  --color-surface-active: var(--surface-active);
  --color-border: var(--border);
  --color-border-strong: var(--border-strong);
  --color-border-control: var(--border-control);
  --color-card: var(--card);
  --color-card-foreground: var(--card-foreground);
  --color-popover: var(--popover);
  --color-popover-foreground: var(--popover-foreground);
  --color-primary: var(--primary);
  --color-primary-hover: var(--primary-hover);
  --color-primary-active: var(--primary-active);
  --color-primary-foreground: var(--primary-foreground);
  --color-secondary: var(--secondary);
  --color-secondary-foreground: var(--secondary-foreground);
  --color-muted: var(--muted);
  --color-muted-foreground: var(--muted-foreground);
  --color-accent: var(--accent);
  --color-accent-foreground: var(--accent-foreground);
  --color-product-accent: var(--product-accent);
  --color-product-accent-soft: var(--product-accent-soft);
  --color-destructive: var(--destructive);
  --color-destructive-hover: var(--destructive-hover);
  --color-destructive-foreground: var(--destructive-foreground);
  --color-input: var(--input);
  --color-ring: var(--ring);
  --color-overlay: var(--overlay);
  --color-status-up: var(--status-up);
  --color-status-degraded: var(--status-degraded);
  --color-status-down: var(--status-down);
  --color-status-unknown: var(--status-unknown);
  --color-status-running: var(--status-running);
  --color-status-up-soft: var(--status-up-soft);
  --color-status-degraded-soft: var(--status-degraded-soft);
  --color-status-down-soft: var(--status-down-soft);
  --color-status-unknown-soft: var(--status-unknown-soft);
  --color-status-running-soft: var(--status-running-soft);
  --color-sidebar: var(--sidebar);
  --color-sidebar-foreground: var(--sidebar-foreground);
  --color-sidebar-primary: var(--sidebar-primary);
  --color-sidebar-primary-foreground: var(--sidebar-primary-foreground);
  --color-sidebar-accent: var(--sidebar-accent);
  --color-sidebar-accent-foreground: var(--sidebar-accent-foreground);
  --color-sidebar-border: var(--sidebar-border);
  --color-sidebar-ring: var(--sidebar-ring);
  --color-chart-1: var(--chart-1);
  --color-chart-2: var(--chart-2);
  --color-chart-3: var(--chart-3);
  --color-chart-4: var(--chart-4);
  --color-chart-grid: var(--chart-grid);
  --color-chart-axis: var(--chart-axis);

  --font-sans: "Geist Sans", "Inter", "Segoe UI", sans-serif;
  --font-mono: "Geist Mono", "Cascadia Code", Consolas, monospace;

  /* Замена default typography scale на роли CloudOps */
  --text-*: initial;
  --text-page-title: 1.5rem;
  --text-page-title--line-height: 2rem;
  --text-page-title--font-weight: 600;
  --text-section-title: 1.125rem;
  --text-section-title--line-height: 1.5rem;
  --text-section-title--font-weight: 600;
  --text-card-title: 0.875rem;
  --text-card-title--line-height: 1.25rem;
  --text-card-title--font-weight: 500;
  --text-body: 0.875rem;
  --text-body--line-height: 1.25rem;
  --text-body--font-weight: 400;
  --text-label: 0.8125rem;
  --text-label--line-height: 1.25rem;
  --text-label--font-weight: 500;
  --text-caption: 0.75rem;
  --text-caption--line-height: 1rem;
  --text-caption--font-weight: 400;
  --text-technical: 0.8125rem;
  --text-technical--line-height: 1.25rem;
  --text-technical--font-weight: 400;
  --text-technical-sm: 0.75rem;
  --text-technical-sm--line-height: 1rem;
  --text-technical-sm--font-weight: 400;
  --text-metric: 1.5rem;
  --text-metric--line-height: 2rem;
  --text-metric--font-weight: 500;

  --spacing: 0.25rem;
  --spacing-control: var(--control-height);
  --spacing-row: var(--row-height);
  --spacing-status-dot: 0.375rem;
  --spacing-icon: 1rem;
  --spacing-icon-lg: 1.25rem;
  --spacing-icon-empty: 1.5rem;

  --radius-*: initial;
  --radius-sm: 0.25rem;
  --radius-control: 0.375rem;
  --radius-panel: 0.5rem;
  --radius-dialog: 0.75rem;
  --radius-full: 999px;

  --shadow-*: initial;
  --shadow-popover: var(--elevation-popover);
  --shadow-dialog: var(--elevation-dialog);

  --breakpoint-*: initial;
  --breakpoint-md: 48rem;
  --breakpoint-lg: 64rem;
  --breakpoint-xl: 80rem;
}

@layer base {
  :root {
    --control-height: 2.25rem;
    --row-height: 2.75rem;
    --panel-padding: 1rem;
    --motion-fast: 120ms;
    --motion-base: 160ms;
    --motion-slow: 200ms;
    --motion-ease: cubic-bezier(0.2, 0, 0, 1);
    --motion-distance: 0.25rem;
  }
  :root[data-density="compact"] {
    --control-height: 2rem;
    --row-height: 2.25rem;
    --panel-padding: 0.75rem;
  }
  @media (pointer: coarse) {
    :root, :root[data-density="compact"] {
      --control-height: 2.75rem;
      --row-height: 3rem;
      --panel-padding: 1rem;
    }
  }
  * { border-color: var(--border); }
  body { @apply bg-background text-foreground font-sans text-body; }
  :focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
  }
  @media (prefers-reduced-motion: reduce) {
    :root {
      --motion-fast: 0ms;
      --motion-base: 0ms;
      --motion-slow: 0ms;
      --motion-distance: 0px;
    }
    *, *::before, *::after {
      animation: none !important;
      transition: none !important;
      scroll-behavior: auto !important;
    }
  }
}
```

Это основа, а не установщик frontend. При переносе добавить централизованные layout/column/chart/z-index tokens из соответствующих таблиц, `@font-face` для реально поставленных файлов и обёртки компонентов. Не считать отсутствие этих файлов разрешением на inline literals. В проекте, использующем animation utilities shadcn, подключить их фактическую зависимость и привести durations/easing к этой шкале.

При добавлении primitives заменить default `rounded-md/lg/xl`, `text-sm/xs`, `shadow-sm/lg`, `sm:` breakpoint и `ring-ring/50` на роли CloudOps; reset namespaces в примере намеренно не сохраняет эти defaults. Не оставлять шадcn-формулы `calc(var(--radius) - 4px)`: controls/panels/dialogs используют явные semantic radii. `rounded-none`, `rounded-full` и `shadow-none` — разрешённые структурные значения. Default цветовые utility Tailwind технически доступны, но запрещены правилами ниже.

### Запреты и оговорённые исключения

**В компонентах запрещены:** HEX/RGB/HSL/OKLCH literals, palette utilities (`text-green-500`, `bg-zinc-900`), собственные gradients/shadows, произвольные margin/padding/gap/radius, ручные light/dark цвета, динамическая сборка utility names, локальные копии status map. Запрет распространяется и на скопированные shadcn primitives после адаптации.

Допустимые исключения:

1. `transparent`, `currentColor`, `inherit`, `none`, `0`, `auto`, относительные размеры `100%`, flex/grid ratios — структурные значения, не альтернативная палитра.
2. Централизованные размерные tokens для border, icon, focus, control, layout, column width и chart geometry, явно перечисленные в документе.
3. Runtime данные: ширина колонки после resize, координаты графика, размер viewport, процент подтверждённого Progress. Они не меняют дизайн-шкалу и проходят clamping/validation.
4. SVG/Recharts props и inline style могут ссылаться на `var(--status-up)`/`var(--chart-1)`; raw color literal остаётся запрещённым. Если библиотеке нужен resolved color, читать theme token общим adapter и обновлять при смене темы.
5. Token-backed arbitrary syntax, например `p-[var(--panel-padding)]` или `max-w-[var(--layout-form-max)]`, допускается только в общей обёртке, когда нет semantic utility. Это ссылка на зарегистрированный token, не `p-[17px]`.
6. System colors в `forced-colors`, тестовые fixtures и сами определения tokens — явные технические исключения. Новый визуальный token вносится в этот документ и общий CSS до использования.

### Do / Don't

| Do | Don't |
| --- | --- |
| `● UP` рядом с именем ресурса | Зелёная карточка целиком и большой SUCCESS pill |
| Health DOWN + отдельное «Обновление данных» | Заменить DOWN на RUNNING во время fetch |
| `UNKNOWN · Проверок ещё не было` | Показать UP, потому что ошибок пока нет |
| Latency `—` с причиной | Latency `0 ms` при null |
| Имя Sans, `192.0.2.10:443` Mono | Вся страница и меню Mono |
| `border-border`, `rounded-panel`, `p-4` | `border-[#303139]`, `rounded-[10px]`, `p-[17px]` |
| Light status-down из токена | Перенести светло-красный dark color на белый текстовый фон |
| Error message + icon + действие | Только красный outline без текста |
| Availability + coverage + период | «100% uptime» при полностью неизвестном периоде |
| Пропуск в линии и UNKNOWN pattern | Соединить пропуск и показать беспрерывную доступность |
| Одна primary кнопка «Добавить ресурс» | Все toolbar-кнопки violet |
| Scroll внутри длинной таблицы | Горизонтальный scroll всей страницы |

Пример использования семантических utilities:

```tsx
<section className="rounded-panel border border-border bg-surface p-4">
  <div className="flex items-center justify-between gap-4">
    <h2 className="text-card-title text-foreground">prod-api</h2>
    <HealthBadge status="UP" />
  </div>
  <dl className="mt-4 flex items-baseline justify-between gap-4">
    <dt className="text-label text-foreground-muted">Задержка</dt>
    <dd className="font-mono text-technical tabular-nums text-foreground">
      42 ms
    </dd>
  </dl>
</section>
```

Здесь фиксированные данные иллюстрируют внешний вид; production получает status/value из view model. `HealthBadge` реализуется один раз и использует общую карту. Для типового интерфейса ресурса предпочитать ResourceCard, а не повторять этот пример вручную на каждом экране.

### Проверка и изменение системы

В PR с новым экраном нужны screenshots обеих тем и перечень проверенных состояний. Не добавлять snapshot tests, которые только повторяют className. Автоматически проверять поведение с риском ошибки: status adapters, null/zero formatting, unknown enum fallback, query scoping, конфликтующие ответы поиска, keyboard dialog flow, отсутствие скрытых ошибок форм. Для текущего документа это требования к будущей реализации, а не заявление о существующем frontend test suite.

Внедрить lint/CI-проверки literals и запрещённых utility вне файлов токенов/fixtures; исключения — по пути и назначению, без безусловного disable правила на feature. Проверка контраста выполняется по foreground/background парам и интерактивным состояниям, а не по названию цвета.

При добавлении нового token/component: указать роль, обе темы, responsive/density, states, accessibility, пример использования и причину, почему существующие варианты не подходят. Сначала обновить эту спецификацию и shared implementation, затем экраны. Не распространять изменение из случайного локального override. Если API пока не поддерживает нужный сценарий, явно показать ограничение и оформить отдельную задачу, не менять backend в рамках визуального исправления.

## Checklist для новых экранов

- [ ] Понятны основная задача страницы, H1, primary action и контекст организации/ресурса.
- [ ] Использованы общие primitives и бизнес-компоненты; нет локальных status maps.
- [ ] Colors/spacing/radius/shadows/typography соответствуют tokens; исключения зарегистрированы.
- [ ] Dark и light проверены отдельно, включая hover, selected, focus, invalid и disabled.
- [ ] Health, lifecycle и task execution не смешаны; DEGRADED/RUNNING применены к правильной модели.
- [ ] Каждый статус имеет текст/форму; Mono ограничен техническими значениями.
- [ ] Null, 0, UNKNOWN, no history, request error и stale data визуально различаются.
- [ ] Реализованы initial loading, refetch, empty, filtered empty, partial error, offline и permission state по применимости.
- [ ] Таблица использует достоверные колонки, серверную сортировку/пагинацию и понятную область selection.
- [ ] Формы имеют labels, hints, field errors, pending, конфликт и сохранение ввода при ошибке.
- [ ] Все действия доступны клавиатурой; focus видим и восстанавливается после dialog/удаления.
- [ ] Достаточны контраст и target size; экран работает при zoom/reflow и с длинным текстом.
- [ ] Mobile сохраняет имя, health, свежесть и действия; нет overflow всей страницы.
- [ ] Comfortable/compact/touch не ломают текст и интерактивные области.
- [ ] Charts имеют units, timezone, устойчивую palette, legend, gap semantics и таблицу данных.
- [ ] Availability сопровождается coverage; timeline не выдуман из summary или текущего состояния.
- [ ] Motion соблюдает durations и reduced motion; live update можно приостановить.
- [ ] Query keys и permissions учитывают organization scope; кэш не раскрывает чужие данные.
- [ ] UI использует существующие API/capabilities; нет декоративных неработающих команд.
- [ ] Документация и tokens синхронизированы; screenshots и применимые проверки приложены к изменению.
