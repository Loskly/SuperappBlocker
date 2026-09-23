const API = '/api/v1'

export async function api(path, options = {}) {
  const token = localStorage.getItem('token')
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${API}${path}`, { ...options, headers })
  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || response.statusText)
  }
  if (response.status === 204) return null
  return response.json()
}

export async function login(email, password) {
  const body = new URLSearchParams({ username: email, password })
  const response = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  })
  if (!response.ok) throw new Error('Login failed')
  return response.json()
}

export async function localDashboardLogin() {
  const response = await fetch(`${API}/auth/local-dashboard`, {
    method: 'POST',
  })
  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || 'Local dashboard login failed')
  }
  return response.json()
}

export async function register(email, password) {
  return api('/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}
