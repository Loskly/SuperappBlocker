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
  "source": "DASHBOARD"
}
```

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

## Compatibility Notes

- Backend хранит данные в SQLite через snake_case-колонки.
- Backend API принимает старые snake_case payloads и новые camelCase payloads.
- Android `SyncApi.RemotePolicy` уже ожидает camelCase-поля для lock/strict
  состояния.
- Dashboard должен отправлять camelCase, чтобы соответствовать Android.
- Legacy pairing по постоянному token временно сохранен только для совместимости.
