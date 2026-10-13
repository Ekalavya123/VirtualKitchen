import { useEffect, useRef } from 'react'
import { GOOGLE_CLIENT_ID } from '../../api/config'

type GoogleSignInButtonProps = {
  onCredential: (idToken: string) => void
  disabled?: boolean
}

export default function GoogleSignInButton({ onCredential, disabled }: GoogleSignInButtonProps) {
  const buttonRef = useRef<HTMLDivElement>(null)
  // Google keeps the callback it was initialised with; the ref lets it always call the latest prop.
  const onCredentialRef = useRef(onCredential)
  useEffect(() => {
    onCredentialRef.current = onCredential
  }, [onCredential])

  useEffect(() => {
    if (!GOOGLE_CLIENT_ID || disabled) {
      return
    }

    const google = window.google

    if (!google?.accounts?.id || !buttonRef.current) {
      return
    }

    google.accounts.id.initialize({
      client_id: GOOGLE_CLIENT_ID,
      callback: response => onCredentialRef.current(response.credential),
    })

    buttonRef.current.innerHTML = ''
    google.accounts.id.renderButton(buttonRef.current, {
      theme: 'outline',
      size: 'large',
      width: 320,
      text: 'continue_with',
    })
  }, [disabled])

  if (!GOOGLE_CLIENT_ID) {
    return null
  }

  return <div ref={buttonRef} className="auth-google-button" />
}
