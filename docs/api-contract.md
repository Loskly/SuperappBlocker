# Appbllocker API Contract

Этот документ фиксирует внешний формат данных между Android-приложением,
backend и dashboard. Внутри backend Python-код может использовать snake_case,
но JSON API должен отдавать и принимать camelCase.

## Что такое контракт

Контракт - это договор между частями системы о том, какие endpoint'ы существуют,
какие поля они принимают, какие поля возвращают и что эти поля означают.

Пример: Android ждет `dailyLimitMinutes`, значит backend и dashboard должны
использовать именно это имя во внешнем JSON. Если одна сторона отправляет
`daily_limit_minutes`, а другая ждет `dailyLimitMinutes`, данные могут не
примениться, хотя смысл у поля один и тот же.

## Правило именования

- External JSON API: `camelCase`
- Backend Python/SQLAlchemy fields: `snake_case`
- Android Kotlin models: `camelCase`
- Dashboard React state/API payloads: `camelCase`

Backend временно принимает оба варианта (`camelCase` и `snake_case`), чтобы не
сломать старые локальные запросы, но возвращать должен `camelCase`.

## PolicyItem

Canonical JSON shape:

```json
{
  "id": "app:com.instagram.android",
  "moduleType": "APP_LIMIT",
  "targetType": "APP",
  "packageName": "com.instagram.android",
  "featureId": null,
  "dailyLimitMinutes": 30,
  "blockMode": "TIME_LIMIT",
  "enabled": true,
  "scheduleJson": null,
  "metadataJson": null,
  "lockMode": "STRICT",
  "lockUntilDayEndMillis": null,
  "lockUntilCustomMillis": null,
  "lockOnBlockActive": false,
  "lockDelayMinutes": null,
  "lockDelayStartedAtMillis": null,
  "source": "DASHBOARD",
  "updatedAt": "2026-07-20T12:00:00",
  "appliedAt": null
}
```

## Dashboard Limit Control

Dashboard-owned edits are sent through:

```http
PUT /api/v1/devices/{deviceId}/policies
```

```json
{
  "policies": [
    {
      "id": "app:com.instagram.android:TIME_LIMIT",
      "moduleType": "APP_LIMIT",
      "targetType": "APP",
      "packageName": "com.instagram.android",
      "featureId": null,
      "dailyLimitMinutes": 30,
      "blockMode": "TIME_LIMIT",
      "enabled": true,
      "scheduleJson": null,
      "metadataJson": null,
      "lockMode": "STRICT",
      "lockUntilDayEndMillis": null,
      "lockUntilCustomMillis": null,
      "lockOnBlockActive": false,
      "lockDelayMinutes": null,
      "lockDelayStartedAtMillis": null,
      "source": "DASHBOARD"
    }
  ]
}
```

Important behavior:

- `PUT /devices/{deviceId}/policies` is an upsert, not a replacement delete.
- Dashboard must not delete a phone rule by omitting it from the payload.
- To stop a rule from working, dashboard sets `enabled=false`.
- To disable strict mode, dashboard sets `lockMode=NORMAL`.
- After dashboard saves a rule, backend sets `updatedAt` and clears `appliedAt`.
- Unchanged rules from a full dashboard save keep their previous `updatedAt` and `appliedAt`.
- Android confirms application by syncing state back to backend; backend then sets `appliedAt`.
- Dashboard shows `pending` while `appliedAt` is missing or older than `updatedAt`.

Android sends its current app list and local policy snapshot through:

```http
POST /api/v1/devices/sync/state
X-Device-Secret: secret-from-backend
```

```json
{
  "deviceToken": "local-device-token",
  "installedApps": [
    {
      "packageName": "com.instagram.android",
      "label": "Instagram"
    }
  ],
  "policies": []
}
```

Dashboard reads phone apps and dashboard change history through:

```http
GET /api/v1/devices/{deviceId}/apps
GET /api/v1/devices/{deviceId}/changes
```

Android reads recent dashboard changes for the in-app notification dialog through:

```http
GET /api/v1/devices/changes?deviceToken=local-device-token
X-Device-Secret: secret-from-backend
```

## Dashboard Feature Control

Dashboard controls Appbllocker feature toggles through a separate feature-control
stream. These are not app limits and should not be stored as `PolicyItem`.

Supported `featureId` values:

- `ADULT_CONTENT` - adult URL filter.
- `YOUTUBE_SHORTS` - YouTube Shorts blocking.
- `INSTAGRAM_REELS` - Instagram Reels blocking.
- `BROWSER_INCOGNITO` - incognito blocking for selected browser packages.
- `FOCUS_SESSION` - remote focus start/stop command.

Dashboard reads and saves feature controls through:

```http
GET /api/v1/devices/{deviceId}/features
PUT /api/v1/devices/{deviceId}/features
```

```json
{
  "features": [
    {
      "featureId": "BROWSER_INCOGNITO",
      "enabled": true,
      "metadataJson": "{\"packages\":[\"com.android.chrome\"]}",
      "source": "DASHBOARD"
    },
    {
      "featureId": "FOCUS_SESSION",
      "enabled": true,
      "metadataJson": "{\"commandId\":\"focus-start-1\",\"durationMinutes\":25,\"mode\":\"STRICT\",\"blockedPackages\":[\"com.instagram.android\"],\"blockedCategoryIds\":[]}",
      "source": "DASHBOARD"
    }
  ]
}
```

Android pulls remote feature controls through:

```http
GET /api/v1/devices/features?deviceToken=local-device-token
X-Device-Secret: secret-from-backend
```

Android also includes its local feature snapshot in:

```http
POST /api/v1/devices/sync/state
```

The `sync/state` payload can now include:

```json
{
  "features": [
    {
      "featureId": "ADULT_CONTENT",
      "enabled": true,
      "metadataJson": null,
      "source": "PHONE"
    }
  ]
}
```

`FOCUS_SESSION` metadata must include a stable `commandId`. Android uses it to
avoid restarting the same focus command on every sync.

## Current Source Of Truth

На этапе 1 backend/dashboard считаются источником истины для remote policies.
Android получает remote policies и применяет их локально. Локальные Android-only
правила, которые пока не умеют полноценно редактироваться с ПК, не должны
случайно ломаться при синхронизации.

Поле `source` сейчас фиксирует происхождение правила. Для правил, созданных в
dashboard, значение по умолчанию: `DASHBOARD`.

## Pairing MVP

Pairing code больше не должен быть постоянным `deviceToken`.

Телефон начинает сопряжение:

```http
POST /api/v1/pairing/start
```

```json
{
  "deviceToken": "local-device-token",
  "deviceName": "Samsung SM-G991B"
}
```

Backend возвращает одноразовый код:

```json
{
  "pairingCode": "ABCD-2345",
  "expiresAt": "2026-07-18T10:15:00",
  "status": "PENDING"
}
```

Dashboard подтверждает код через существующий authenticated endpoint:

```http
POST /api/v1/devices/register
```

```json
{
  "pairingCode": "ABCD-2345",
  "name": "Android Device"
}
```

Телефон проверяет статус:

```http
GET /api/v1/pairing/status?pairingCode=ABCD-2345&deviceToken=local-device-token
```

Если dashboard подтвердил код, backend возвращает:

```json
{
  "status": "PAIRED",
  "deviceId": 1,
  "deviceSecret": "secret-from-backend"
}
```

Android сохраняет `deviceId` и `deviceSecret` в encrypted storage. После этого
обычная синхронизация добавляет заголовок:

```http
X-Device-Secret: secret-from-backend
```

### Connection UX

Dashboard reads local backend candidates from:

```http
GET /api/v1/dashboard/connection-info
```

Response:

```json
{
  "localUrl": "http://127.0.0.1:8000",
  "recommendedPhoneUrl": "http://10.49.13.82:8000",
  "phoneUrls": ["http://10.49.13.82:8000"]
}
```

The phone still pairs by short code only. QR is intentionally not part of the MVP.
Android saves the backend URL in `DeviceTokenStore` before starting pairing. After
`POST /api/v1/pairing/start`, the Home screen polls `pairing/status` only while a
pending code exists and the screen lifecycle is active. This is a setup-only
poll, not a background control loop.

## Compatibility Notes

- Backend хранит данные в SQLite через snake_case-колонки.
- Backend API принимает старые snake_case payloads и новые camelCase payloads.
- Android `SyncApi.RemotePolicy` уже ожидает camelCase-поля для lock/strict
  состояния.
- Dashboard должен отправлять camelCase, чтобы соответствовать Android.
- Legacy pairing по постоянному token временно сохранен только для совместимости.
