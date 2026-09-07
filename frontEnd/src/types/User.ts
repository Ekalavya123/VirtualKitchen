export type AuthProvider = 'LOCAL' | 'GOOGLE'
export type UserType = 'ADMIN' | 'USER'

export interface User {
  id: number
  name: string
  email: string
  emailVerified?: boolean
  authProvider?: AuthProvider
  userType?: UserType
  createdAt?: string
  updatedAt?: string
}
