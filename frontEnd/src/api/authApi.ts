/**
 * Authentication & User API
 * Handles all user-related API calls
 */

import { apiGet, apiPost } from './client'
import { API } from './endpoints'
import type { User } from '../types/User'

export interface UserCreateRequest {
  name: string
  email: string
  passwordHash: string
}

export interface KitchenCreateRequest {
  name: string
  ownerId: number
}

export interface Kitchen {
  id: number
  name: string
  ownerId: number
}

export interface AuthResponse {
  token: string
  user: User
}

export interface SignupRequest {
  name: string
  email: string
  password: string
  confirmPassword: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface ResetPasswordRequest {
  email: string
  newPassword: string
  confirmNewPassword: string
}

export const AuthApi = {
  /**
   * Create a new user (legacy, password-less; superseded by AuthenticationApi.signup)
   */
  async createUser(data: UserCreateRequest): Promise<User> {
    return apiPost<User>(API.auth.users, data)
  },

  /**
   * Get user by email
   */
  async getUserByEmail(email: string): Promise<User> {
    return apiGet<User>(API.auth.userByEmail(email))
  },
}

export const KitchenApi = {
  /**
   * Create a new kitchen
   */
  async createKitchen(data: KitchenCreateRequest): Promise<Kitchen> {
    return apiPost<Kitchen>(API.kitchen.list, data)
  },

  /**
   * Get kitchen by owner ID
   */
  async getKitchenByOwnerId(ownerId: number): Promise<Kitchen | Kitchen[]> {
    return apiGet<Kitchen | Kitchen[]>(API.kitchen.byOwnerId(ownerId))
  },
}

/**
 * Full authentication API: email/password, Google OAuth, and OTP-based flows.
 */
export const AuthenticationApi = {
  async signup(data: SignupRequest): Promise<User> {
    return apiPost<User>(API.auth.signup, data)
  },

  async login(data: LoginRequest): Promise<AuthResponse> {
    return apiPost<AuthResponse>(API.auth.login, data)
  },

  async loginWithGoogle(idToken: string): Promise<AuthResponse> {
    return apiPost<AuthResponse>(API.auth.google, { idToken })
  },

  async me(): Promise<User> {
    return apiGet<User>(API.auth.me)
  },

  async sendEmailVerificationOtp(email: string): Promise<void> {
    await apiPost(API.auth.sendEmailOtp, { email })
  },

  async verifyEmailOtp(email: string, otp: string): Promise<AuthResponse> {
    return apiPost<AuthResponse>(API.auth.verifyEmailOtp, { email, otp })
  },

  async sendLoginOtp(email: string): Promise<void> {
    await apiPost(API.auth.sendLoginOtp, { email })
  },

  async verifyLoginOtp(email: string, otp: string): Promise<AuthResponse> {
    return apiPost<AuthResponse>(API.auth.verifyLoginOtp, { email, otp })
  },

  async forgotPassword(email: string): Promise<void> {
    await apiPost(API.auth.forgotPassword, { email })
  },

  async verifyPasswordResetOtp(email: string, otp: string): Promise<void> {
    await apiPost(API.auth.verifyPasswordResetOtp, { email, otp })
  },

  async resetPassword(data: ResetPasswordRequest): Promise<void> {
    await apiPost(API.auth.resetPassword, data)
  },
}

