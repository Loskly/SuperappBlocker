import { useEffect, useMemo, useState } from 'react'
import { api, localDashboardLogin } from './api.js'

const blockModes = ['TIME_LIMIT', 'PERMANENT', 'COOLDOWN']
const lockModes = ['NORMAL', 'STRICT']

const modeLabels = {
  TIME_LIMIT: 'Обычный лимит',
  PERMANENT: 'Постоянная блокировка',
  COOLDOWN: 'Cooldown',
}

const featureDefinitions = [
  { id: 'ADULT_CONTENT', title: 'Блокировка клубнички' },
  { id: 'YOUTUBE_SHORTS', title: 'YouTube Shorts' },
  { id: 'INSTAGRAM_REELS', title: 'Instagram Reels' },
  { id: 'BROWSER_INCOGNITO', title: 'Инкогнито в браузерах' },
]

const incognitoBrowserPackages = new Set([
  'com.android.chrome',
  'com.chrome.beta',
  'com.chrome.dev',
  'com.chrome.canary',
  'org.mozilla.firefox',
  'org.mozilla.firefox_beta',
  'org.mozilla.fenix',
])

function normalizeFeature(feature) {
  return {
    featureId: feature.featureId ?? feature.feature_id,
    enabled: feature.enabled ?? false,
    metadataJson: feature.metadataJson ?? feature.metadata_json ?? null,
    source: feature.source ?? 'DASHBOARD',
    updatedAt: feature.updatedAt ?? feature.updated_at ?? null,
    appliedAt: feature.appliedAt ?? feature.applied_at ?? null,
  }
}

function defaultFeature(featureId) {
  return {
    featureId,
    enabled: false,
    metadataJson: null,
    source: 'DASHBOARD',
    updatedAt: null,
    appliedAt: null,
  }
}

function parseMetadata(json) {
  if (!json) return {}
  try {
    return JSON.parse(json) || {}
  } catch {
    return {}
  }
}

function normalizePolicy(policy) {
  return {
    id: policy.id,
    moduleType: policy.moduleType ?? policy.module_type ?? 'APP_LIMIT',
    targetType: policy.targetType ?? policy.target_type ?? 'APP',
    packageName: policy.packageName ?? policy.package_name ?? '',
    featureId: policy.featureId ?? policy.feature_id ?? null,
    dailyLimitMinutes: policy.dailyLimitMinutes ?? policy.daily_limit_minutes ?? null,
    blockMode: policy.blockMode ?? policy.block_mode ?? 'TIME_LIMIT',
    enabled: policy.enabled ?? true,
    scheduleJson: policy.scheduleJson ?? policy.schedule_json ?? null,
    metadataJson: policy.metadataJson ?? policy.metadata_json ?? null,
    lockMode: policy.lockMode ?? policy.lock_mode ?? 'NORMAL',
    lockUntilDayEndMillis: policy.lockUntilDayEndMillis ?? policy.lock_until_day_end_millis ?? null,
    lockUntilCustomMillis: policy.lockUntilCustomMillis ?? policy.lock_until_custom_millis ?? null,
    lockOnBlockActive: policy.lockOnBlockActive ?? policy.lock_on_block_active ?? false,
    lockDelayMinutes: policy.lockDelayMinutes ?? policy.lock_delay_minutes ?? null,
    lockDelayStartedAtMillis: policy.lockDelayStartedAtMillis ?? policy.lock_delay_started_at_millis ?? null,
    source: policy.source ?? 'DASHBOARD',
    updatedAt: policy.updatedAt ?? policy.updated_at ?? null,
    appliedAt: policy.appliedAt ?? policy.applied_at ?? null,
  }
}

function formatDateTime(value) {
  if (!value) return 'нет данных'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

function errorMessage(error) {
  const raw = error?.message || 'Неизвестная ошибка'
  try {
    const parsed = JSON.parse(raw)
    return parsed.detail || raw
  } catch {
    return raw
  }
}

function appliedState(policy) {
  if (!policy.updatedAt && policy.appliedAt) return 'applied'
  if (!policy.updatedAt) return 'unknown'
  if (!policy.appliedAt) return 'pending'
  return new Date(policy.appliedAt).getTime() >= new Date(policy.updatedAt).getTime()
    ? 'applied'
    : 'pending'
}

function cooldownJson(usageMinutes, windowMinutes, blockMinutes) {
  return JSON.stringify({
    usageMinutes: Number(usageMinutes) || 10,
    windowMinutes: Number(windowMinutes) || 60,
    blockMinutes: Number(blockMinutes) || 15,
  })
}

export default function App() {
  const [token, setToken] = useState(localStorage.getItem('token'))
  const [devices, setDevices] = useState([])
  const [selectedDevice, setSelectedDevice] = useState(null)
  const [policies, setPolicies] = useState([])
  const [features, setFeatures] = useState([])
  const [usage, setUsage] = useState([])
  const [apps, setApps] = useState([])
  const [changes, setChanges] = useState([])
  const [pairingCode, setPairingCode] = useState('')
  const [connectionInfo, setConnectionInfo] = useState(null)
  const [selectedPhoneUrl, setSelectedPhoneUrl] = useState('')
  const [deviceStatus, setDeviceStatus] = useState(null)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [pairingBusy, setPairingBusy] = useState(false)
  const [savingPolicies, setSavingPolicies] = useState(false)
  const [savingFeatures, setSavingFeatures] = useState(false)
  const [selectedAppPackage, setSelectedAppPackage] = useState('')
  const [newRuleMode, setNewRuleMode] = useState('TIME_LIMIT')
  const [newRuleLimitMinutes, setNewRuleLimitMinutes] = useState(60)
  const [newRuleStrict, setNewRuleStrict] = useState(false)
  const [cooldownUsageMinutes, setCooldownUsageMinutes] = useState(10)
  const [cooldownWindowMinutes, setCooldownWindowMinutes] = useState(60)
  const [cooldownBlockMinutes, setCooldownBlockMinutes] = useState(15)
  const [incognitoPackages, setIncognitoPackages] = useState([])
  const [focusPackages, setFocusPackages] = useState([])
  const [focusDurationMinutes, setFocusDurationMinutes] = useState(25)
  const [focusMode, setFocusMode] = useState('STRICT')

  const selectedDeviceInfo = useMemo(
    () => devices.find((device) => device.id === selectedDevice) || null,
    [devices, selectedDevice],
  )

  const appLabelByPackage = useMemo(() => {
    const map = new Map()
    apps.forEach((app) => map.set(app.packageName, app.label))
    return map
  }, [apps])

  const featureById = useMemo(() => {
    const map = new Map()
    features.forEach((feature) => map.set(feature.featureId, feature))
    return map
  }, [features])

  const browserApps = useMemo(() => {
    return apps.filter((app) => {
      const haystack = `${app.packageName} ${app.label}`.toLowerCase()
      return incognitoBrowserPackages.has(app.packageName) ||
        haystack.includes('chrome') ||
        haystack.includes('firefox')
    })
  }, [apps])

  const focusApps = useMemo(() => apps.slice(0, 120), [apps])

  useEffect(() => {
    if (token) {
      loadConnectionInfo()
      loadDevices()
    }
  }, [token])

  useEffect(() => {
    if (token) return
    async function connectLocalDashboard() {
      try {
        const result = await localDashboardLogin()
        localStorage.setItem('token', result.access_token)
        setToken(result.access_token)
        setError('')
      } catch (e) {
        setError(errorMessage(e))
      }
    }
    connectLocalDashboard()
  }, [token])

  useEffect(() => {
    if (selectedDevice) {
      loadDeviceData(selectedDevice)
    } else {
      setPolicies([])
      setFeatures([])
      setUsage([])
      setApps([])
      setChanges([])
      setDeviceStatus(null)
    }
  }, [selectedDevice])

  useEffect(() => {
    if (!selectedAppPackage && apps.length) {
      setSelectedAppPackage(apps[0].packageName)
    }
  }, [apps, selectedAppPackage])

  async function loadConnectionInfo() {
    try {
      const data = await api('/dashboard/connection-info')
      setConnectionInfo(data)
      setSelectedPhoneUrl((current) => current || data.recommendedPhoneUrl || data.phoneUrls?.[0] || '')
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  async function loadDevices() {
    try {
      const data = await api('/devices')
      setDevices(data)
      setSelectedDevice((current) => current || data[0]?.id || null)
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  async function loadDeviceData(deviceId) {
    try {
      const [policyData, usageData, status, appData, changeData, featureData] = await Promise.all([
        api(`/devices/${deviceId}/policies`),
        api(`/devices/${deviceId}/usage`),
        api(`/devices/${deviceId}/status`),
        api(`/devices/${deviceId}/apps`),
        api(`/devices/${deviceId}/changes`),
        api(`/devices/${deviceId}/features`),
      ])
      setPolicies(policyData.map(normalizePolicy))
      syncFeatureControls(featureData.map(normalizeFeature))
      setUsage(usageData)
      setDeviceStatus(status)
      setApps(appData)
      setChanges(changeData)
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  function syncFeatureControls(remoteFeatures) {
    const merged = featureDefinitions.map((definition) => (
      remoteFeatures.find((feature) => feature.featureId === definition.id) || defaultFeature(definition.id)
    ))
    const focusRemote = remoteFeatures.find((feature) => feature.featureId === 'FOCUS_SESSION') ||
      defaultFeature('FOCUS_SESSION')
    setFeatures([...merged, focusRemote])

    const incognito = parseMetadata(
      remoteFeatures.find((feature) => feature.featureId === 'BROWSER_INCOGNITO')?.metadataJson,
    )
    setIncognitoPackages(Array.isArray(incognito.packages) ? incognito.packages : [])

    const focus = parseMetadata(focusRemote.metadataJson)
    setFocusPackages(Array.isArray(focus.blockedPackages) ? focus.blockedPackages : [])
    setFocusDurationMinutes(focus.durationMinutes || 25)
    setFocusMode(focus.mode || 'STRICT')
  }

  function featureOrDefault(featureId) {
    return featureById.get(featureId) || defaultFeature(featureId)
  }

  function updateFeatureList(featureId, enabled, metadataJson = null) {
    const current = featureOrDefault(featureId)
    const nextFeature = {
      ...current,
      featureId,
      enabled,
      metadataJson,
      source: current.source || 'DASHBOARD',
    }
    const exists = features.some((feature) => feature.featureId === featureId)
    return exists
      ? features.map((feature) => (feature.featureId === featureId ? nextFeature : feature))
      : [...features, nextFeature]
  }

  async function saveFeatureList(nextFeatures, successMessage) {
    if (!selectedDevice) return
    try {
      setSavingFeatures(true)
      await api(`/devices/${selectedDevice}/features`, {
        method: 'PUT',
        body: JSON.stringify({ features: nextFeatures }),
      })
      setNotice(successMessage)
      setError('')
      await loadDeviceData(selectedDevice)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setSavingFeatures(false)
    }
  }

  async function toggleFeature(featureId, enabled) {
    let metadataJson = null
    if (featureId === 'BROWSER_INCOGNITO') {
      const packages = incognitoPackages.length
        ? incognitoPackages
        : browserApps.map((app) => app.packageName)
      metadataJson = JSON.stringify({ packages })
      if (enabled && packages.length === 0) {
        setError('Сначала синхронизируй список приложений или выбери браузер для инкогнито.')
        return
      }
    }
    const nextFeatures = updateFeatureList(featureId, enabled, metadataJson)
    setFeatures(nextFeatures)
    await saveFeatureList(nextFeatures, 'Настройки функции сохранены. Нажми sync на телефоне, чтобы применить.')
  }

  async function saveIncognitoBrowsers() {
    const current = featureOrDefault('BROWSER_INCOGNITO')
    const nextFeatures = updateFeatureList(
      'BROWSER_INCOGNITO',
      current.enabled,
      JSON.stringify({ packages: incognitoPackages }),
    )
    setFeatures(nextFeatures)
    await saveFeatureList(nextFeatures, 'Список браузеров для инкогнито сохранён.')
  }

  async function startRemoteFocus() {
    if (focusPackages.length === 0) {
      setError('Выбери хотя бы одно приложение для фокуса.')
      return
    }
    const metadataJson = JSON.stringify({
      commandId: `focus-start-${Date.now()}`,
      durationMinutes: Number(focusDurationMinutes) || 25,
      mode: focusMode,
      blockedPackages: focusPackages,
      blockedCategoryIds: [],
    })
    const nextFeatures = updateFeatureList('FOCUS_SESSION', true, metadataJson)
    setFeatures(nextFeatures)
    await saveFeatureList(nextFeatures, 'Фокус отправлен на backend. Нажми sync на телефоне, чтобы включить.')
  }

  async function stopRemoteFocus() {
    const nextFeatures = updateFeatureList('FOCUS_SESSION', false, JSON.stringify({
      commandId: `focus-stop-${Date.now()}`,
      durationMinutes: Number(focusDurationMinutes) || 25,
      mode: focusMode,
      blockedPackages: focusPackages,
      blockedCategoryIds: [],
    }))
    setFeatures(nextFeatures)
    await saveFeatureList(nextFeatures, 'Команда остановки фокуса сохранена. Нажми sync на телефоне.')
  }

  async function pairDevice() {
    const code = pairingCode.trim().toUpperCase()
    if (!code) {
      setError('Введите код с телефона')
      return
    }

    try {
      setPairingBusy(true)
      const device = await api('/devices/register', {
        method: 'POST',
        body: JSON.stringify({ pairingCode: code }),
      })
      setPairingCode('')
      setNotice(`Телефон подключён: ${device.name}`)
      setError('')
      await loadDevices()
      setSelectedDevice(device.id)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setPairingBusy(false)
    }
  }

  async function savePolicies() {
    if (!selectedDevice) return
    try {
      setSavingPolicies(true)
      await api(`/devices/${selectedDevice}/policies`, {
        method: 'PUT',
        body: JSON.stringify({ policies }),
      })
      setNotice('Правила сохранены. Статус станет “применено” после синхронизации телефона.')
      setError('')
      await loadDeviceData(selectedDevice)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setSavingPolicies(false)
    }
  }

  function updatePolicy(index, field, value) {
    setPolicies((prev) => prev.map((p, i) => (i === index ? { ...p, [field]: value } : p)))
  }

  async function addPolicy() {
    const packageName = selectedAppPackage || apps[0]?.packageName
    if (!packageName) {
      setError('Сначала синхронизируй телефон, чтобы dashboard получил список приложений')
      return
    }

    const blockMode = newRuleMode
    const id = `app:${packageName}:${blockMode}`
    const nextPolicy = {
      id,
      moduleType: 'APP_LIMIT',
      targetType: 'APP',
      packageName,
      featureId: null,
      dailyLimitMinutes: blockMode === 'TIME_LIMIT' ? Number(newRuleLimitMinutes) || 60 : null,
      blockMode,
      enabled: true,
      scheduleJson: null,
      metadataJson: blockMode === 'COOLDOWN'
        ? cooldownJson(cooldownUsageMinutes, cooldownWindowMinutes, cooldownBlockMinutes)
        : null,
      lockMode: newRuleStrict ? 'STRICT' : 'NORMAL',
      lockUntilDayEndMillis: null,
      lockUntilCustomMillis: null,
      lockOnBlockActive: false,
      lockDelayMinutes: null,
      lockDelayStartedAtMillis: null,
      source: 'DASHBOARD',
      updatedAt: null,
      appliedAt: null,
    }

    const existingIndex = policies.findIndex((policy) => policy.id === id)
    const nextPolicies = existingIndex === -1
      ? [...policies, nextPolicy]
      : policies.map((policy, index) => (index === existingIndex ? { ...policy, ...nextPolicy } : policy))

    setPolicies(nextPolicies)
    try {
      setSavingPolicies(true)
      await api(`/devices/${selectedDevice}/policies`, {
        method: 'PUT',
        body: JSON.stringify({ policies: nextPolicies }),
      })
      setNotice('Лимит сохранён на backend. Нажми sync на телефоне, чтобы применить его.')
      setError('')
      await loadDeviceData(selectedDevice)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setSavingPolicies(false)
    }
  }

  async function emergencyUnlock(packageName) {
    if (!selectedDevice || !packageName) return
    try {
      await api(`/devices/${selectedDevice}/unlock`, {
        method: 'POST',
        body: JSON.stringify({ packageName, durationMinutes: 15 }),
      })
      setNotice(`${packageName} разблокирован на 15 минут после следующей синхронизации`)
      setError('')
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  async function copyPhoneUrl() {
    if (!selectedPhoneUrl) return
    try {
      await navigator.clipboard.writeText(selectedPhoneUrl)
      setNotice('Адрес скопирован')
    } catch {
      setNotice('Скопируй адрес из поля вручную')
    }
  }

  function logout() {
    localStorage.removeItem('token')
    setToken(null)
    setDevices([])
    setSelectedDevice(null)
  }

  function renderAppliedBadge(policy) {
    const state = appliedState(policy)
    if (state === 'applied') return <span className="status-pill mini good">применено</span>
    if (state === 'pending') return <span className="status-pill mini warning">ждёт sync</span>
    return <span className="status-pill mini">нет данных</span>
  }

  if (!token) {
    return (
      <div className="container compact">
        <h1>Appbllocker</h1>
        <section className="panel">
          <p>Подключаю локальный dashboard...</p>
          {error && <p className="error">{error}</p>}
        </section>
      </div>
    )
  }

  return (
    <div className="container">
      <header className="topbar">
        <div>
          <p className="eyebrow">Локальное управление</p>
          <h1>Appbllocker Dashboard</h1>
        </div>
        <button className="secondary" onClick={logout}>Сбросить доступ</button>
      </header>

      <section className="panel connection-panel">
        <div className="section-header">
          <div>
            <p className="eyebrow">Шаг 1</p>
            <h2>Подключение телефона</h2>
          </div>
          <span className={devices.length ? 'status-pill good' : 'status-pill'}>
            {devices.length ? 'есть подключение' : 'ожидает телефон'}
          </span>
        </div>

        <div className="connect-grid">
          <div className="field-block">
            <label>Адрес backend для телефона</label>
            <div className="inline-control">
              {connectionInfo?.phoneUrls?.length > 1 ? (
                <select value={selectedPhoneUrl} onChange={(e) => setSelectedPhoneUrl(e.target.value)}>
                  {connectionInfo.phoneUrls.map((url) => (
                    <option key={url} value={url}>{url}</option>
                  ))}
                </select>
              ) : (
                <input readOnly value={selectedPhoneUrl || 'адрес не найден'} />
              )}
              <button className="secondary" onClick={copyPhoneUrl} disabled={!selectedPhoneUrl}>Копировать</button>
            </div>
            <p className="hint">Этот адрес вводится в приложении на телефоне один раз и сохраняется там.</p>
          </div>

          <div className="field-block">
            <label>Код с телефона</label>
            <div className="inline-control">
              <input
                placeholder="ABCD-2345"
                value={pairingCode}
                onChange={(e) => setPairingCode(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') pairDevice()
                }}
              />
              <button onClick={pairDevice} disabled={pairingBusy || !pairingCode.trim()}>
                {pairingBusy ? 'Подключаю...' : 'Подключить'}
              </button>
            </div>
            <p className="hint">После ввода кода телефон сам завершит подключение и запомнит backend.</p>
          </div>
        </div>
      </section>

      <section className="panel">
        <div className="section-header">
          <div>
            <p className="eyebrow">Функции</p>
            <h2>Функции Appbllocker</h2>
          </div>
          <button className="secondary" onClick={() => selectedDevice && loadDeviceData(selectedDevice)} disabled={!selectedDevice}>
            Обновить
          </button>
        </div>

        <div className="feature-grid">
          {featureDefinitions.map((definition) => {
            const feature = featureOrDefault(definition.id)
            return (
              <div className="feature-card" key={definition.id}>
                <div>
                  <strong>{definition.title}</strong>
                  <span>{feature.source || 'PHONE'}</span>
                </div>
                {renderAppliedBadge(feature)}
                <label className="check-row">
                  <input
                    type="checkbox"
                    checked={feature.enabled}
                    disabled={!selectedDevice || savingFeatures}
                    onChange={(e) => toggleFeature(definition.id, e.target.checked)}
                  />
                  <span>{feature.enabled ? 'Включено' : 'Выключено'}</span>
                </label>
              </div>
            )
          })}
        </div>

        <div className="feature-detail-grid">
          <div className="feature-detail">
            <div className="section-header compact-header">
              <div>
                <p className="eyebrow">Incognito</p>
                <h2>Браузеры</h2>
              </div>
              <button className="secondary small" onClick={saveIncognitoBrowsers} disabled={!selectedDevice || savingFeatures}>
                Сохранить
              </button>
            </div>
            <select
              multiple
              className="multi-select"
              value={incognitoPackages}
              onChange={(event) => setIncognitoPackages(Array.from(event.target.selectedOptions, (option) => option.value))}
            >
              {browserApps.map((app) => (
                <option key={app.packageName} value={app.packageName}>{app.label} ({app.packageName})</option>
              ))}
            </select>
            {browserApps.length === 0 && <p className="hint">Браузеры появятся после sync списка приложений.</p>}
          </div>

          <div className="feature-detail">
            <div className="section-header compact-header">
              <div>
                <p className="eyebrow">Focus</p>
                <h2>Запуск фокуса</h2>
              </div>
              {renderAppliedBadge(featureOrDefault('FOCUS_SESSION'))}
            </div>
            <div className="focus-grid">
              <div className="field-block">
                <label>Длительность, минут</label>
                <input type="number" min="1" value={focusDurationMinutes} onChange={(e) => setFocusDurationMinutes(e.target.value)} />
              </div>
              <div className="field-block">
                <label>Режим</label>
                <select value={focusMode} onChange={(e) => setFocusMode(e.target.value)}>
                  <option value="STRICT">STRICT</option>
                  <option value="SOFT">SOFT</option>
                </select>
              </div>
            </div>
            <label>Приложения для блокировки</label>
            <select
              multiple
              className="multi-select"
              value={focusPackages}
              onChange={(event) => setFocusPackages(Array.from(event.target.selectedOptions, (option) => option.value))}
            >
              {focusApps.map((app) => (
                <option key={app.packageName} value={app.packageName}>{app.label} ({app.packageName})</option>
              ))}
            </select>
            <div className="actions feature-actions">
              <button onClick={startRemoteFocus} disabled={!selectedDevice || savingFeatures || focusPackages.length === 0}>
                Включить фокус
              </button>
              <button className="secondary" onClick={stopRemoteFocus} disabled={!selectedDevice || savingFeatures}>
                Выключить фокус
              </button>
            </div>
          </div>
        </div>
      </section>

      <section className="panel">
        <div className="section-header">
          <div>
            <p className="eyebrow">Устройство</p>
            <h2>Состояние</h2>
          </div>
          <button className="secondary" onClick={() => { loadDevices(); if (selectedDevice) loadDeviceData(selectedDevice) }}>
            Обновить
          </button>
        </div>

        {devices.length === 0 ? (
          <p className="empty">Телефоны пока не подключены.</p>
        ) : (
          <>
            <select
              className="device-select"
              value={selectedDevice || ''}
              onChange={(e) => setSelectedDevice(Number(e.target.value))}
            >
              {devices.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name} ({d.pairedAt ? 'подключено' : d.pairingCode})
                </option>
              ))}
            </select>
            <div className="status-grid">
              <div>
                <span>Статус</span>
                <strong>{deviceStatus?.online ? 'online' : 'offline'}</strong>
              </div>
              <div>
                <span>Последняя синхронизация</span>
                <strong>{formatDateTime(deviceStatus?.lastSyncAt || selectedDeviceInfo?.lastSyncAt)}</strong>
              </div>
              <div>
                <span>Приложений в списке</span>
                <strong>{apps.length}</strong>
              </div>
            </div>
          </>
        )}
      </section>

      <section className="panel">
        <div className="section-header">
          <div>
            <p className="eyebrow">Лимиты</p>
            <h2>Создать правило</h2>
          </div>
          <button onClick={addPolicy} disabled={!selectedDevice || apps.length === 0 || savingPolicies}>
            {savingPolicies ? 'Сохраняю...' : 'Добавить и сохранить'}
          </button>
        </div>

        <div className="rule-form-grid">
          <div className="field-block wide">
            <label>Приложение</label>
            <select value={selectedAppPackage} onChange={(e) => setSelectedAppPackage(e.target.value)}>
              {apps.map((app) => (
                <option key={app.packageName} value={app.packageName}>
                  {app.label} ({app.packageName})
                </option>
              ))}
            </select>
            {apps.length === 0 && <p className="hint">Список появится после синхронизации телефона.</p>}
          </div>
          <div className="field-block">
            <label>Режим</label>
            <select value={newRuleMode} onChange={(e) => setNewRuleMode(e.target.value)}>
              {blockModes.map((mode) => (
                <option key={mode} value={mode}>{modeLabels[mode]}</option>
              ))}
            </select>
          </div>
          <div className="field-block">
            <label>Лимит, минут</label>
            <input
              type="number"
              value={newRuleLimitMinutes}
              disabled={newRuleMode !== 'TIME_LIMIT'}
              onChange={(e) => setNewRuleLimitMinutes(e.target.value)}
            />
          </div>
          <div className="field-block">
            <label>Строгий режим</label>
            <label className="check-row">
              <input type="checkbox" checked={newRuleStrict} onChange={(e) => setNewRuleStrict(e.target.checked)} />
              <span>STRICT</span>
            </label>
          </div>
        </div>

        {newRuleMode === 'COOLDOWN' && (
          <div className="rule-form-grid cooldown-grid">
            <div className="field-block">
              <label>Использование, мин</label>
              <input type="number" value={cooldownUsageMinutes} onChange={(e) => setCooldownUsageMinutes(e.target.value)} />
            </div>
            <div className="field-block">
              <label>Окно, мин</label>
              <input type="number" value={cooldownWindowMinutes} onChange={(e) => setCooldownWindowMinutes(e.target.value)} />
            </div>
            <div className="field-block">
              <label>Блокировка, мин</label>
              <input type="number" value={cooldownBlockMinutes} onChange={(e) => setCooldownBlockMinutes(e.target.value)} />
            </div>
          </div>
        )}
      </section>

      <section className="panel">
        <div className="section-header">
          <div>
            <p className="eyebrow">Лимиты</p>
            <h2>Все правила телефона</h2>
          </div>
          <div className="actions">
            <button onClick={savePolicies} disabled={!selectedDevice || savingPolicies}>
              {savingPolicies ? 'Сохраняю...' : 'Сохранить изменения'}
            </button>
          </div>
        </div>

        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Приложение</th>
                <th>Package</th>
                <th>Источник</th>
                <th>Режим</th>
                <th>Лимит</th>
                <th>Strict</th>
                <th>On</th>
                <th>Применение</th>
                <th>Unlock</th>
              </tr>
            </thead>
            <tbody>
              {policies.map((p, index) => (
                <tr key={p.id}>
                  <td>{appLabelByPackage.get(p.packageName) || p.packageName || p.featureId || p.id}</td>
                  <td>
                    <input value={p.packageName || ''} onChange={(e) => updatePolicy(index, 'packageName', e.target.value)} />
                  </td>
                  <td><span className="source-tag">{p.source || 'PHONE'}</span></td>
                  <td>
                    <select value={p.blockMode || 'TIME_LIMIT'} onChange={(e) => updatePolicy(index, 'blockMode', e.target.value)}>
                      {blockModes.map((mode) => (
                        <option key={mode} value={mode}>{mode}</option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <input
                      type="number"
                      value={p.dailyLimitMinutes ?? ''}
                      disabled={p.blockMode !== 'TIME_LIMIT'}
                      onChange={(e) => updatePolicy(index, 'dailyLimitMinutes', e.target.value === '' ? null : Number(e.target.value))}
                    />
                  </td>
                  <td>
                    <select value={p.lockMode || 'NORMAL'} onChange={(e) => updatePolicy(index, 'lockMode', e.target.value)}>
                      {lockModes.map((mode) => (
                        <option key={mode} value={mode}>{mode}</option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <input type="checkbox" checked={p.enabled} onChange={(e) => updatePolicy(index, 'enabled', e.target.checked)} />
                  </td>
                  <td>{renderAppliedBadge(p)}</td>
                  <td>
                    <button className="secondary small" onClick={() => emergencyUnlock(p.packageName)}>15 min</button>
                  </td>
                </tr>
              ))}
              {policies.length === 0 && (
                <tr>
                  <td colSpan="9" className="empty-cell">Лимитов пока нет. После sync телефон отправит сюда локальные правила.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <p className="hint">Dashboard не удаляет лимиты. Чтобы убрать действие правила, выключи `On` или переведи `Strict` в `NORMAL`.</p>
      </section>

      <section className="panel">
        <div className="section-header">
          <div>
            <p className="eyebrow">Журнал</p>
            <h2>Последние изменения dashboard</h2>
          </div>
        </div>
        {changes.length === 0 ? (
          <p className="empty">Изменений пока нет.</p>
        ) : (
          <div className="change-list">
            {changes.slice(0, 8).map((change) => (
              <div className="change-item" key={change.id}>
                <strong>{change.summary}</strong>
                <span>{formatDateTime(change.createdAt)}</span>
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="panel">
        <div className="section-header">
          <div>
            <p className="eyebrow">Статистика</p>
            <h2>Usage</h2>
          </div>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>Date</th><th>Package</th><th>Minutes</th></tr>
            </thead>
            <tbody>
              {usage.map((u, i) => (
                <tr key={`${u.dateKey}-${u.packageName}-${i}`}>
                  <td>{u.dateKey}</td>
                  <td>{u.packageName}</td>
                  <td>{Math.round(u.usedMillis / 60000)}</td>
                </tr>
              ))}
              {usage.length === 0 && (
                <tr>
                  <td colSpan="3" className="empty-cell">Данные появятся после синхронизации телефона.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {(notice || error) && (
        <div className={error ? 'toast error-toast' : 'toast'}>
          {error || notice}
        </div>
      )}
    </div>
  )
}
