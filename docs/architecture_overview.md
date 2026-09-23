# Appbllocker - Architecture Overview

Дата актуализации: 2026-09-19
Репозиторий: `C:\Users\vbrta\ecosentinel\Appbllocker`  
Основные части: Android app (`android/`), backend (`backend/`), dashboard (`dashboard/`)

Этот документ описывает архитектуру приложения, текущее состояние и важные компоненты. Обновляйте его при добавлении новых фич.

Важно: backend и dashboard сейчас лежат в `.gitignore` и считаются локальными/private частями проекта. Они существуют в рабочей папке, но обычные git-команды могут их не показывать как tracked-файлы.

## 1. Что это за приложение

**Appbllocker** - персональное Android-приложение для самоконтроля и блокировки отвлекающих приложений/контента. Главная идея: телефон сам следит за foreground-приложением, статистикой использования, активными правилами и при необходимости показывает overlay-блокировку поверх других приложений.

Продукт в первую очередь разрабатывается для владельца проекта, не как публичный B2B/SaaS. Поэтому приоритеты такие:

- практическая работоспособность на реальном Android-устройстве;
- строгие режимы, которые сложнее обойти с телефона;
- управление лимитами и функциями с ПК;
- сохранение контроля после перезагрузки, энергосбережения и "гибернации";
- быстрый MVP важнее идеальной production-архитектуры, но нельзя ломать базовую защиту.

## 2. Главная архитектурная идея

Проект состоит из трёх частей:

- **Android-приложение** - основной "агент" на телефоне. Оно реально применяет блокировки.
- **Backend FastAPI** - промежуточный сервер для сопряжения, хранения правил, usage, feature controls и истории изменений.
- **Dashboard React/Vite** - UI на ПК для просмотра телефона и управления правилами/функциями.

Сейчас управление с ПК не является настоящим realtime push. Модель такая:

1. Dashboard сохраняет изменения в backend.
2. Телефон периодически или вручную делает sync.
3. Android скачивает изменения и применяет их локально.
4. Android отправляет обратно состояние, список приложений, usage и подтверждение применения.

Ключевая мысль: **backend/dashboard не блокируют приложения сами**. Они только задают конфигурацию. Блокирует всегда Android.

## 3. Текущее состояние Android-приложения

### Навигация

Основной экран: `android/app/src/main/java/com/ecosentinel/appblocker/MainActivity.kt`

Основные вкладки:

- Home (новый дашборд)
- Settings (сопряжение, статусы, настройки)
- Limits
- Stats
- TODO
- Calories
- Features

Из-за 7 вкладок используется кастомная нижняя навигация: `ui/MainBottomNavigationBar.kt`.

### Always-on мониторинг

Ключевые файлы:

- `service/UsageMonitorService.kt`
- `service/MonitorBootstrap.kt`
- `service/MonitorScreenGate.kt`
- `receiver/MonitorScreenReceiver.kt`
- `tracker/UsageTracker.kt`
- `service/TodayUsageSyncCache.kt`

Как работает:

- `UsageMonitorService` работает как foreground service.
- Каждые `5_000 ms` проверяет foreground-приложение.
- Полный sync usage throttled через `TodayUsageSyncCache`: примерно раз в `60_000 ms` или при смене foreground app.
- Если экран выключен, мониторинг уходит в более экономичный режим через `MonitorScreenGate`.
- При включении экрана сервис делает wake-up tick и обновляет состояние.

Это одна из самых чувствительных частей проекта. Нельзя добавлять тяжёлую работу в основной tick-loop без throttling.

### Policy engine

Ключевые файлы:

- `engine/BlockModule.kt`
- `engine/PolicyEngine.kt`
- `engine/BlockModuleRegistry.kt`
- `engine/BlockMode.kt`
- `engine/RuleLockMode.kt`
- `engine/RuleLockPolicy.kt`
- `data/entity/Entities.kt`

Поддерживаемые типы целей:

- `APP`
- `CATEGORY`
- `CUSTOM_GROUP`
- `IN_APP_FEATURE`
- `URL_PATTERN`

Поддерживаемые режимы блокировки:

- `PERMANENT`
- `TIME_LIMIT`
- `TIME_OF_DAY`
- `COOLDOWN`

Поддерживаемые модули:

- `APP_LIMIT`
- `FOCUS`
- `IN_APP_FEATURE`
- `ADULT_CONTENT`
- `TASK_GATE` - заглушка, выключено
- `FRIEND_PASSWORD` - заглушка, выключено

`PolicyEngine` специально не блокирует:

- само приложение Appbllocker;
- `com.android.systemui`;
- `com.android.settings`.

Это важно для возможности восстановить доступы и не загнать телефон в тупик.

### Лимиты и строгий режим

Ключевые файлы:

- `ui/LimitsFragment.kt`
- `ui/EditRuleDialog.kt`
- `ui/RuleAdapter.kt`
- `engine/RuleLockPolicy.kt`
- `modules/applimit/AppLimitModule.kt`
- `cooldown/CooldownManager.kt`

Что есть:

- создание локальных правил;
- лимиты на приложения;
- категории;
- custom groups;
- постоянная блокировка;
- time limit;
- time of day;
- cooldown;
- strict mode.

Strict mode:

- `NORMAL` - правило можно менять локально;
- `STRICT` - локальное выключение/удаление/изменение ограничивается условиями.

Условия strict:

- до конца дня;
- до конкретного времени;
- пока блокировка активна;
- после delay/cooldown на изменение;
- strict без условий фактически locked.

Важно: `RuleLockPolicy.evaluate(..., actor = REMOTE_OVERRIDE)` позволяет удалённому контролю обходить strict. Это задумано: ПК/dashboard в будущем должен быть "старшим" источником управления.

### Overlay-блокировка

Ключевые файлы:

- `ui/BlockOverlayManager.kt`
- `ui/BlockOverlayActivity.kt`
- `ui/PasswordOverlayManager.kt`
- `ui/PasswordOverlayActivity.kt`
- `ui/BlockOverlayTexts.kt`
- layouts: `res/layout/overlay_block.xml`, `res/layout/activity_block_overlay.xml`, `res/layout/overlay_password.xml`

Overlay используется для:

- обычной блокировки лимитов;
- фокуса;
- adult URL;
- website rules;
- Shorts/Reels;
- incognito;
- password-gate.

Если `SYSTEM_ALERT_WINDOW` не выдан, код часто fallback-ит в Activity, но это слабее системного overlay.

### Пароль на приложения

Ключевые файлы:

- `modules/apppassword/AppPasswordModule.kt`
- `security/AppPasswordStore.kt`
- `security/PasswordSessionManager.kt`
- `ui/AppPasswordActivity.kt`
- `ui/AddPasswordAppActivity.kt`
- `ui/PasswordOverlayActivity.kt`

Есть PIN на выбранные приложения и режимы повторного запроса PIN.

### Фокус

Ключевые файлы:

- `ui/FocusActivity.kt`
- `ui/FocusPickAppsActivity.kt`
- `focus/FocusManager.kt`
- `focus/FocusConfigStore.kt`
- `focus/FocusExpireScheduler.kt`
- `receiver/FocusExpireReceiver.kt`
- `focus/FocusNotificationHelper.kt`
- `modules/focus/FocusModule.kt`

Есть мягкий и строгий режим фокуса. Для удалённого управления добавлен `stopFocusFromRemote()`, чтобы dashboard мог остановить даже strict focus.

### Adult content / "клубничка"

Ключевые файлы:

- `modules/adult/BrowserUrlMonitorService.kt`
- `modules/adult/AdultContentModule.kt`
- `modules/adult/AdultFilterSettings.kt`
- `modules/adult/UrlBlocklistStore.kt`
- `modules/adult/SupportedBrowsers.kt`
- `modules/adult/BrowserUrlState.kt`
- `res/raw/adult_hosts.txt`
- `ui/AdultFilterActivity.kt`

Работает через AccessibilityService: приложение читает URL/адресную строку браузеров и блокирует домены из списка.

Ограничения:

- зависит от структуры UI браузера;
- WebView внутри приложений может обходить фильтр;
- приватные режимы/нестандартные браузеры могут вести себя непредсказуемо;
- нужно выданное Accessibility-разрешение.

### Shorts/Reels

Ключевые файлы:

- `modules/inapp/InAppFeatureModule.kt`
- `modules/inapp/InAppFeatureState.kt`
- `modules/inapp/InAppFeatureDetector.kt`
- `modules/inapp/YouTubeShortsDetector.kt`
- `modules/inapp/InstagramReelsDetector.kt`
- `modules/inapp/YouTubeUiSignatures.kt`
- `modules/inapp/InstagramUiSignatures.kt`
- `modules/inapp/AccessibilityTreeScanner.kt`
- `modules/inapp/SupportedInAppApps.kt`
- `ui/InAppFeatureBlockActivity.kt`

Статус:

- YouTube Shorts блокировка считалась рабочей.
- Instagram Reels блокировка хрупкая и требует тестов после каждого изменения Instagram UI.
- Был важный баг/риск: блокировка Reels может задевать Stories. Обязательно регрессионно проверять Stories отдельно.

### Incognito blocker

Ключевые файлы:

- `modules/inapp/BrowserIncognitoDetector.kt`
- `ui/IncognitoBlockActivity.kt`
- `res/layout/activity_incognito_block.xml`
- `res/layout/item_incognito_browser.xml`
- изменения в `modules/adult/BrowserUrlMonitorService.kt`
- настройки в `modules/inapp/InAppFeatureSettings.kt`

Фича работает через Accessibility. Пользователь выбирает браузеры, в которых нужно блокировать приватный режим. Поддержка зависит от того, видны ли признаки incognito/private mode в accessibility tree.

Фича новая, требует практического теста на Chrome/Firefox и возможной донастройки сигнатур.

### Survival mode

Ключевые файлы:

- `survival/SurvivalManager.kt`
- `survival/AppHealthChecker.kt`
- `survival/AppHealthSnapshot.kt`
- `survival/SurvivalNotificationHelper.kt`
- `survival/SurvivalSettings.kt`
- `survival/SurvivalAccessibilityOverlayManager.kt`
- `survival/SurvivalReceiver.kt`
- `ui/SurvivalModeActivity.kt`
- `ui/SurvivalAccessibilityOverlayActivity.kt`
- `res/layout/activity_survival_mode.xml`
- `res/layout/activity_survival_accessibility_overlay.xml`

Что есть:

- проверка health-состояния приложения;
- восстановление notification channels;
- восстановление workers;
- восстановление мониторинга;
- восстановление focus notification/scheduler;
- восстановление super alarms в некоторых сценариях;
- warning notification, если защита не готова;
- отдельная функция во вкладке Features;
- настройка "Требовать спец возможности";
- выбор поведения: notification или overlay;
- overlay с кнопкой открытия Accessibility settings.

Текущее слабое место:

- overlay может быть слишком агрессивным по UX;
- сейчас добавлена серия recheck после открытия настроек: `1.5s`, `4s`, `8s`, `15s`, `30s`;
- overlay не должен появляться поверх `com.android.settings`, чтобы не мешать реально включить Accessibility;
- если поведение раздражает, первым делом тюнить `RECHECK_DELAYS_MS` в `SurvivalAccessibilityOverlayManager.kt`.

### Device Owner

Ключевые файлы:

- `admin/DeviceOwnerManager.kt`
- `admin/AppBlockerDeviceAdminReceiver.kt`
- `res/xml/device_admin.xml`
- `docs/device-owner-setup.md`

Что умеет:

- определить, является ли приложение Device Owner;
- `setUninstallBlocked` для самого Appbllocker;
- user restrictions вроде `DISALLOW_INSTALL_UNKNOWN_SOURCES`, `DISALLOW_FACTORY_RESET`;
- auto-grant permission policy при поддержке;
- `STAY_ON_WHILE_PLUGGED_IN`.

Что не делать без отдельного решения:

- не завязывать новые фичи на Device Owner как обязательное условие;
- не ломать non-root/non-device-owner сценарий;
- root-приложение пока не реализуется.

### TODO

Ключевые файлы:

- `ui/TodoFragment.kt`
- `ui/TodoEditDialog.kt`
- `data/entity/TodoEntity.kt`

Статус:

- базовый список задач есть;
- due date/time хранится;
- напоминания и связь с блокировками пока не реализованы.

### Calories

Ключевые файлы:

- `ui/CaloriesFragment.kt`
- `calories/CalorieTrackerRepository.kt`
- `calories/FoodPhotoStorage.kt`
- `data/entity/FoodEntryEntity.kt`
- `res/layout/fragment_calories.xml`

Статус:

- ручной ввод еды/ккал;
- дневная сумма;
- фото еды через camera/gallery;
- AI-анализ еды по фото не реализован.

### Super alarm

Ключевые файлы:

- `ui/SuperAlarmActivity.kt`
- `ui/SuperAlarmEditDialog.kt`
- `ui/AlarmChallengeActivity.kt`
- `service/AlarmRingingService.kt`
- `alarm/SuperAlarmManager.kt`
- `alarm/AlarmScheduler.kt`
- `alarm/MathChallengeGenerator.kt`
- `alarm/AlarmSoundHelper.kt`

Статус:

- будильники есть;
- для выключения нужен arithmetic challenge;
- есть работа со звуком и хранением выбранной мелодии;
- после survival/hibernation сценариев нужно отдельно тестировать, что будильники пересоздаются.

## 4. Room database

Ключевые файлы:

- `data/AppDatabase.kt`
- `data/DatabaseMigrations.kt`
- `data/dao/Daos.kt`
- `data/entity/*`

Текущая версия Room DB: `15`.

Основные таблицы:

- `policy_rules`
- `usage_daily`
- `unlock_grants`
- `override_state`
- `password_protected_apps`
- `focus_sessions`
- `cooldown_states`
- `super_alarms`
- `app_groups`
- `app_group_members`
- `todo_items`
- `food_entries`

Правило: любые изменения схемы требуют аккуратной миграции в `DatabaseMigrations.kt` и bump версии в `AppDatabase.kt`.

## 5. Backend

Ключевые файлы:

- `backend/app/main.py`
- `backend/app/models.py`
- `backend/app/config.py`
- `backend/requirements.txt`
- runtime DB: `backend/appblocker.db`

Технологии:

- FastAPI
- SQLAlchemy
- SQLite по умолчанию
- JWT auth
- local dashboard login

Важные модели backend:

- `User`
- `Device`
- `PairingSession`
- `Policy`
- `InstalledApp`
- `FeatureControl`
- `DeviceChangeEvent`
- `UsageSnapshot`
- `UnlockGrant`
- `OverrideEvent`

Основные endpoints:

- `POST /api/v1/auth/local-dashboard`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/dashboard/connection-info`
- `POST /api/v1/pairing/start`
- `GET /api/v1/pairing/status`
- `GET /api/v1/devices`
- `POST /api/v1/devices/register`
- `GET/PUT /api/v1/devices/{device_id:int}/policies`
- `GET /api/v1/devices/{device_id:int}/apps`
- `GET/PUT /api/v1/devices/{device_id:int}/features`
- `GET /api/v1/devices/{device_id:int}/changes`
- `GET /api/v1/devices/policies?deviceToken=...`
- `GET /api/v1/devices/features?deviceToken=...`
- `POST /api/v1/devices/sync/usage`
- `POST /api/v1/devices/sync/state`
- `GET /api/v1/devices/changes?deviceToken=...`
- `GET /api/v1/devices/{device_id:int}/usage`
- `POST /api/v1/devices/{device_id:int}/unlock`
- `GET /api/v1/devices/{device_id:int}/status`

Важно: dynamic routes используют `{device_id:int}`, чтобы `/api/v1/devices/policies` и `/api/v1/devices/features` не ловились как `device_id`.

Текущее ограничение backend:

- это MVP, не production;
- `secret_key = "change-me-in-production"`;
- CORS открыт на `*`;
- SQLite без полноценной миграционной системы;
- local-dashboard login разрешён только с `127.0.0.1` / `::1`;
- backend/dashboard intentionally local/private.

## 6. Dashboard

Ключевые файлы:

- `dashboard/src/App.jsx`
- `dashboard/src/api.js`
- `dashboard/src/styles.css`
- `dashboard/vite.config.js`
- `dashboard/package.json`

Что умеет:

- auto-login в local dashboard через `/auth/local-dashboard`;
- показать и выбрать сопряжённое устройство;
- показать backend URL для телефона;
- ввести pairing code с телефона;
- показать online/offline по `lastSyncAt`;
- показать список приложений, пришедший с телефона;
- создать dashboard-owned правило для приложения;
- выбрать режим правила: обычный `TIME_LIMIT`, `PERMANENT`, `COOLDOWN`;
- выбрать `NORMAL`/`STRICT`;
- включать/выключать правила;
- видеть pending/applied состояние через `updatedAt` / `appliedAt`;
- видеть usage;
- видеть историю dashboard changes;
- управлять функциями:
  - adult content;
  - YouTube Shorts;
  - Instagram Reels;
  - browser incognito;
  - remote focus start/stop.

Важное ограничение dashboard:

- dashboard **не удаляет** лимиты с телефона;
- чтобы "убрать" правило, dashboard выставляет `enabled=false`;
- dashboard может снять strict, выставив `lockMode=NORMAL`;
- изменения применяются только после sync на телефоне;
- emergency unlock endpoint есть, но Android sync сейчас не скачивает `UnlockGrant` из backend, поэтому эта кнопка/endpoint может не иметь реального эффекта на телефоне.

## 7. Сопряжение телефона и ПК

Текущая модель:

1. В dashboard открыть локальную панель.
2. Dashboard получает URL backend для телефона через `/dashboard/connection-info`.
3. На телефоне в Settings вводится backend URL и сохраняется в `DeviceTokenStore`.
4. Телефон создаёт pairing code через `/pairing/start`.
5. Пользователь вводит code в dashboard.
6. Dashboard подтверждает code через `/devices/register`.
7. Телефон polling-ом проверяет `/pairing/status`.
8. При `PAIRED` Android сохраняет `deviceId` и `deviceSecret`.
9. Дальше Android добавляет `X-Device-Secret` в sync-запросы.

Polling сопряжения:

- только пока есть pending code;
- только пока Settings screen активен;
- интервал `3_000 ms`.

Автосинхронизация:

- WorkManager sync каждые 15 минут (`AppBlockerApplication.scheduleSyncWorker`);
- вручную через кнопку Sync на Settings;
- `UsageMonitorService` может enqueue one-shot sync при старте.

Если виден `401 Unauthorized` на `/api/v1/devices/sync/usage`, почти всегда причина:

- телефон использует старый `deviceSecret`;
- backend DB была пересоздана;
- устройство нужно пересопрячь;
- это не проблема dashboard-login.

## 8. Как запускать локально

### Backend

Без активации PowerShell-venv, чтобы не упираться в Execution Policy:

```powershell
cd C:\Users\vbrta\ecosentinel\Appbllocker\backend
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Если venv нет:

```powershell
cd C:\Users\vbrta\ecosentinel\Appbllocker\backend
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### Dashboard

В PowerShell лучше запускать через `npm.cmd`, если `npm.ps1` блокируется политикой выполнения:

```powershell
cd C:\Users\vbrta\ecosentinel\Appbllocker\dashboard
npm.cmd run dev
```

Dashboard обычно доступен на:

```text
http://localhost:5173/
```

Vite proxy отправляет `/api` на:

```text
http://127.0.0.1:8000
```

### Android

Сборка debug APK:

```powershell
cd C:\Users\vbrta\ecosentinel\Appbllocker\android
.\gradlew.bat assembleDebug
```

Установка на устройство:

```powershell
cd C:\Users\vbrta\ecosentinel\Appbllocker\android
.\gradlew.bat installDebug
```

Для реального телефона backend URL должен быть не `localhost`, а адрес ПК в сети, например:

- Wi-Fi LAN: `http://<IPv4 ПК>:8000`
- Tailscale: `http://<Tailscale IP ПК>:8000`

Backend должен слушать `--host 0.0.0.0`, иначе телефон не достучится.

## 9. Разрешения Android

Важные разрешения/доступы:

- Usage Access - читать usage events и foreground app;
- Display over other apps - overlay-блокировки;
- Notifications - foreground service и survival warnings;
- Battery optimization ignore - меньше шансов, что Android убьёт приложение;
- Autostart/OEM settings - вручную на некоторых прошивках;
- Accessibility - URL filter, Shorts/Reels, incognito;
- Device Owner - опциональное усиление;
- Camera - фото еды в calories tracker.

Permissions UI:

- `ui/PermissionsActivity.kt`
- `util/PermissionHelper.kt`

## 10. Главные недоработки и риски

### 1. Android build нужно регулярно проверять

В рабочем дереве много незакоммиченных изменений: backend sync, dashboard, incognito, survival mode. После перехода в Antigravity первым делом стоит собрать Android и устранить compile errors.

### 2. Backend/dashboard не tracked git-ом

`.gitignore` содержит:

- `backend/`
- `dashboard/`

Это сделано как "keep server/admin code local only". Но для разработки в Antigravity нужно помнить, что эти папки всё равно важны.

### 3. Remote control не realtime

Сейчас телефон сам тянет изменения. Dashboard может сказать "сохранено", но правило не появится на телефоне до sync.

Нужно улучшить:

- UX "ожидает sync";
- ручную кнопку "sync now" уже есть на телефоне, но не с dashboard;
- возможно добавить push/Firebase/WebSocket позже;
- возможно сделать foreground sync чаще при активном dashboard, но аккуратно с батареей.

### 4. Emergency unlock не замкнут end-to-end

Backend имеет `/devices/{id}/unlock`, dashboard вызывает его, но Android `SyncApi` сейчас не подтягивает unlock grants. Это выглядит как недоделанная фича.

### 5. Survival overlay требует UX-тюнинга

Функция полезна, но может быть раздражающей. Текущие recheck intervals могут быть слишком частыми. Не превращать в бесконечный агрессивный loop без throttling.

### 6. Accessibility detectors хрупкие

Shorts/Reels/incognito/adult URL зависят от UI других приложений. Нужно тестировать на реальных версиях Chrome, Firefox, YouTube, Instagram.

Особенно важно:

- YouTube Shorts не ломать;
- Instagram Stories не должны блокироваться блокировкой Reels;
- Reels должны блокироваться только когда реально открыт Reels-context.

### 7. Strict mode нужно тестировать на обходы

Проверить сценарии:

- выключение strict-лимита локально;
- изменение правила локально;
- удаление правила локально;
- strict с delay;
- strict до конца дня;
- strict при active block;
- dashboard remote override.

### 8. Security не production

Для личного MVP ок, но не для публичного сервиса:

- default secret key;
- CORS `*`;
- SQLite;
- нет rate limiting;
- нет HTTPS requirement;
- local auto-login;
- нет полноценного управления пользователями/сессиями.

## 11. Запланированные и будущие функции

### Ближайший приоритет

1. Стабилизировать Android build после всех текущих изменений.
2. Протестировать core blocking на реальном телефоне.
3. Довести dashboard control of limits до стабильного end-to-end.
4. Улучшить UX сопряжения и статуса sync.
5. Отшлифовать Survival mode.

### Remote control с ПК

Нужно довести:

- dashboard action "sync now" или "request phone sync";
- понятные статусы pending/applied/failed;
- отображение последнего sync и последней ошибки;
- remote feature control для Survival mode;
- полноценный remote unlock/override, если он действительно нужен;
- отдельный экран "История изменений с ПК" на телефоне уже частично есть через `DashboardChangeStore`.

### Мотивация / motivation screen

Пока не реализовано. Идея: использовать мотивационный экран как способ ослабить/разблокировать строгий режим не просто кнопкой, а через осознанное действие.

Возможные варианты:

- экран причины: "зачем ты хочешь открыть?";
- delay + reflection;
- задачи перед разблокировкой;
- дневные цели;
- связка с TODO/TaskGate.

### TaskGate

Сейчас `TaskGateModule` выключен и возвращает `null`.

Идея:

- перед разблокировкой нужно выполнить задачу;
- задача может быть из TODO/day plan;
- можно использовать для строгих лимитов вместо полного запрета.

### Friend password / accountability partner

Сейчас `FriendPasswordModule` выключен и возвращает `null`.

Идея:

- запросить разрешение у доверенного человека;
- dashboard/remote approval;
- временное unlock grant после подтверждения.

### AI calories

Сейчас calories tracker ручной. Идея:

- фото еды;
- backend endpoint для анализа изображения;
- AI предлагает название/ккал;
- пользователь правит и сохраняет.

Важно: API keys хранить только на backend, не в Android.

### Root / усиленная защита

Root-приложение обсуждалось как идея, но не реализовано и не является текущим планом. Сложность высокая, риски большие:

- разные устройства/прошивки;
- Magisk/root detection;
- Play Protect/security warnings;
- поддержка будет тяжелее обычного Android-приложения.

Текущий путь: non-root + Accessibility + overlay + foreground service + battery optimization + optional Device Owner.

## 12. Что новому агенту делать первым

Рекомендуемый порядок:

1. Прочитать этот документ.
2. Прочитать `docs/api-contract.md`.
3. Проверить `git status --short` и не откатывать чужие изменения.
4. Собрать Android:

```powershell
cd C:\Users\vbrta\ecosentinel\Appbllocker\android
.\gradlew.bat assembleDebug
```

5. Если build падает, исправлять только реальные compile/runtime ошибки, не делать большой refactor.
6. Запустить backend и dashboard.
7. Проверить на телефоне:
   - сохранение backend URL;
   - pairing by code;
   - manual sync;
   - список приложений в dashboard;
   - создание лимита в dashboard;
   - появление лимита на телефоне после sync;
   - applied/pending status;
   - feature toggles.

## 13. Мини-чеклист регрессии

Core:

- мониторинг стартует после запуска приложения;
- usage access распознаётся;
- overlay permission распознаётся;
- foreground app определяется;
- лимит на приложение блокирует приложение;
- permanent block блокирует приложение;
- strict rule нельзя выключить локально;
- dashboard может выключить strict через sync;
- приложение не блокирует Settings/SystemUI.

Content:

- YouTube Shorts блокируются;
- обычный YouTube не блокируется;
- Instagram Reels блокируются;
- Instagram Stories не блокируются;
- adult URL блокируется в поддерживаемом браузере;
- incognito блокируется только в выбранных браузерах.

Remote:

- pairing code создаётся на телефоне;
- code принимается dashboard;
- `deviceSecret` сохраняется;
- `/sync/usage` не даёт 401 после pairing;
- `/sync/state` отправляет apps/policies/features;
- dashboard показывает apps;
- dashboard-created policy появляется на телефоне после sync;
- dashboard feature toggle применяется на телефоне после sync.

Survival:

- warning notification появляется при проблемах;
- режим notification не показывает overlay;
- режим overlay показывает экран при выключенном Accessibility;
- кнопка открывает Accessibility settings;
- overlay не мешает включить Accessibility;
- если выйти из настроек без включения, overlay возвращается;
- если включить Accessibility, overlay исчезает.

## 14. Важные файлы для быстрого входа

Android:

- `android/app/src/main/java/com/ecosentinel/appblocker/MainActivity.kt`
- `android/app/src/main/java/com/ecosentinel/appblocker/AppBlockerApplication.kt`
- `android/app/src/main/java/com/ecosentinel/appblocker/service/UsageMonitorService.kt`
- `android/app/src/main/java/com/ecosentinel/appblocker/engine/PolicyEngine.kt`
- `android/app/src/main/java/com/ecosentinel/appblocker/engine/BlockModuleRegistry.kt`
- `android/app/src/main/java/com/ecosentinel/appblocker/data/AppDatabase.kt`
- `android/app/src/main/java/com/ecosentinel/appblocker/data/DatabaseMigrations.kt`
- `android/app/src/main/java/com/ecosentinel/appblocker/sync/SyncApi.kt`
- `android/app/src/main/java/com/ecosentinel/appblocker/sync/PairingApi.kt`
- `android/app/src/main/java/com/ecosentinel/appblocker/sync/RemoteFeatureControlBridge.kt`
- `android/app/src/main/java/com/ecosentinel/appblocker/ui/HomeFragment.kt`
- `android/app/src/main/java/com/ecosentinel/appblocker/ui/LimitsFragment.kt`
- `android/app/src/main/java/com/ecosentinel/appblocker/ui/FeaturesFragment.kt`

Backend:

- `backend/app/main.py`
- `backend/app/models.py`
- `backend/app/config.py`
- `backend/requirements.txt`

Dashboard:

- `dashboard/src/App.jsx`
- `dashboard/src/api.js`
- `dashboard/src/styles.css`
- `dashboard/vite.config.js`

Docs:

- `docs/api-contract.md`
- `docs/device-owner-setup.md`
- `docs/project-handoff.md` - старый handoff, может быть полезен как исторический контекст, но этот документ новее.

## 15. Рабочие договорённости для будущей разработки

- Не переписывать архитектуру сразу: сначала стабилизировать MVP.
- Не добавлять тяжёлые операции в `UsageMonitorService` tick без throttle/cache.
- Не делать Device Owner обязательным для обычной работы.
- Не делать root обязательным.
- Не ломать Settings/SystemUI allowlist.
- Для sync держать один внешний JSON-контракт в camelCase.
- При изменении backend/dashboard помнить, что они могут быть ignored git-ом.
- Любую новую таблицу Room добавлять через migration.
- Любую новую remote-фичу проводить через три места: Android bridge, backend feature/policy model, dashboard UI.
- После каждой правки Accessibility-детекторов тестировать реальные приложения, потому что UI-сигнатуры ломкие.

## 16. Недавние изменения (Changelog)

- **2026-09-19 (позже):** Исправлено ложное срабатывание блокировки Instagram Reels на Instagram Stories. Убраны сигнатуры `reel_viewer`, `reels_viewer` и `reel` из `InstagramUiSignatures.kt`, так как Instagram технически использует слово "reel" для Stories, а для коротких видео использует "clips".
- **2026-09-19:** Удалены функции уведомления о критическом заряде батареи и режиме энергосбережения из `AppHealthChecker`, `SurvivalManager` и `PermissionsActivity`. Логика восстановления после энергосбережения (`RecoveryWorker`) полностью убрана как избыточная.
