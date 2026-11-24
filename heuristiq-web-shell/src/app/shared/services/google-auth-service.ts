import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';

/**
 * Wraps Firebase Auth's Google sign-in popup flow. The backend (UamGoogleAuthService) verifies a
 * Firebase ID token via the Firebase Admin SDK — not a raw Google Identity Services credential —
 * so this must go through Firebase Auth, not `google.accounts.id`.
 *
 * Requires src/environments/environment*.ts's `firebase` block to hold real project values
 * (currently placeholders) and the backend's FIREBASE_ADMIN_CREDENTIALS_PATH/_BASE64 to point at a
 * real service account. Until both are set, signIn() will fail at runtime with a clear error.
 */
@Injectable({ providedIn: 'root' })
export class GoogleAuthService {
  async signIn(): Promise<string> {
    const { initializeApp, getApps } = await import('firebase/app');
    const { getAuth, GoogleAuthProvider, signInWithPopup } = await import('firebase/auth');

    if (getApps().length === 0) {
      initializeApp(environment.firebase);
    }

    const auth = getAuth();
    const provider = new GoogleAuthProvider();
    const result = await signInWithPopup(auth, provider);
    return result.user.getIdToken();
  }
}
