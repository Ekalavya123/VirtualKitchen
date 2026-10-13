import { createContext, useContext } from 'react'
import type { Kitchen } from '../../types/kitchen'
import type { User } from '../../types/User'

export type SignedInSession = { user: User; kitchen: Kitchen }

/** Provided by the signed-in route guard, so every route below it can rely on a user and kitchen. */
export const SignedInContext = createContext<SignedInSession | null>(null)

/** The signed-in user and kitchen; only for routes inside the signed-in guard. */
export function useSignedIn(): SignedInSession {
  const session = useContext(SignedInContext)
  if (!session) {
    throw new Error('useSignedIn() used outside the signed-in routes')
  }
  return session
}
