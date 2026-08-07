import { describe, expect, it, beforeEach } from 'vitest'
import {
  isRememberLoginEnabled,
  loadSavedLoginCredentials,
  saveLoginCredentials,
} from './login-credentials'

describe('login-credentials', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('defaultsRememberLoginToTrue', () => {
    expect(isRememberLoginEnabled()).toBe(true)
  })

  it('savesAndLoadsCredentialsWhenRememberEnabled', () => {
    saveLoginCredentials(true, 'user@example.com', 'secret')
    expect(loadSavedLoginCredentials()).toEqual({
      remember: true,
      email: 'user@example.com',
      password: 'secret',
    })
  })

  it('clearsCredentialsWhenRememberDisabled', () => {
    saveLoginCredentials(true, 'user@example.com', 'secret')
    saveLoginCredentials(false, 'user@example.com', 'secret')
    expect(loadSavedLoginCredentials()).toEqual({
      remember: false,
      email: '',
      password: '',
    })
  })
})
