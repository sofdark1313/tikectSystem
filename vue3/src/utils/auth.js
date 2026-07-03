import Cookies from 'js-cookie'

const TokenKey = 'Admin-Token'
const RefreshTokenKey = 'Admin-Refresh-Token'
const nameKey = 'userName'
const userIdKey = 'userId'

function buildCookieOptions(expiresInSeconds) {
  const expires = Number(expiresInSeconds)
  if (!Number.isFinite(expires) || expires <= 0) {
    return undefined
  }
  return { expires: expires / 86400 }
}

export function getToken() {
  return Cookies.get(TokenKey)
}

export function setToken(token, expiresInSeconds) {
  const options = buildCookieOptions(expiresInSeconds)
  return options ? Cookies.set(TokenKey, token, options) : Cookies.set(TokenKey, token)
}

export function removeToken() {
  return Cookies.remove(TokenKey)
}

export function getRefreshToken() {
  return Cookies.get(RefreshTokenKey)
}

export function setRefreshToken(token, expiresInSeconds) {
  const options = buildCookieOptions(expiresInSeconds)
  return options ? Cookies.set(RefreshTokenKey, token, options) : Cookies.set(RefreshTokenKey, token)
}

export function removeRefreshToken() {
  return Cookies.remove(RefreshTokenKey)
}

//设置userName
export function getName() {
  return Cookies.get(nameKey)
}

export function setName(token) {
  return Cookies.set(nameKey, token)
}

export function removeName() {
  return Cookies.remove(nameKey)
}

//设置userId
export function getUserIdKey() {
  return Cookies.get(userIdKey)
}

export function setUserIdKey(token) {
  return Cookies.set(userIdKey, token)
}

export function removeUserIdKey() {
  return Cookies.remove(userIdKey)
}
