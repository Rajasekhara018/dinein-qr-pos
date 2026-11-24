import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

interface UamResponse<T> {
  success?: boolean;
  responseObject?: T;
  message?: string;
}

export interface VendorSession {
  id: string;
  name?: string;
  email?: string;
  username?: string;
}

interface VendorLoginResponse {
  token?: string;
  accessToken?: string;
  userId: string;
  username?: string;
  email?: string;
  fullName?: string;
}

interface VendorRegisterResponse {
  userId: string;
}

/**
 * Vendor identity, kept separate from the customer-facing AuthService -- different session
 * semantics (no customer role applies to a vendor) and its own storage keys so a customer
 * session and a vendor session in the same browser never stomp each other. Talks to UAM's
 * generic /identity/auth/** surface (IdentityAuthController) with userType: 'VENDOR', the same
 * envelope + unwrap pattern used by the customer AuthService/UamService.
 */
@Injectable({ providedIn: 'root' })
export class VendorAuthService {
  private vendorSubject = new BehaviorSubject<VendorSession | null>(null);
  vendor$: Observable<VendorSession | null> = this.vendorSubject.asObservable();

  constructor(private http: HttpClient, private router: Router) {
    this.restoreSession();
  }

  get currentVendor(): VendorSession | null {
    return this.vendorSubject.value;
  }

  private restoreSession(): void {
    const token = localStorage.getItem('vendor_access_token');
    const raw = localStorage.getItem('vendor_data');
    if (token && raw) {
      try { this.vendorSubject.next(JSON.parse(raw)); } catch { /* ignore */ }
    }
  }

  /** Creates the vendor's UAM login identity. Called before AdminAPI's business/KYC registration
   *  (POST /vendors/register), whose payload then carries the returned id as uam_user_id. */
  register(details: { name: string; mobile: string; email: string; city?: string }): Observable<string> {
    const body = {
      requestObject: {
        name: details.name,
        mobile: details.mobile,
        email: details.email,
        city: details.city,
        userType: 'VENDOR',
      },
    };
    return this.http.post<UamResponse<VendorRegisterResponse>>(`${environment.uamApiUrl}/identity/auth/register`, body).pipe(
      map((response) => this.unwrap(response).userId),
    );
  }

  sendPhoneOtp(phone: string): Observable<void> {
    const body = { requestObject: { phone, userType: 'VENDOR' } };
    return this.http.post<UamResponse<unknown>>(`${environment.uamApiUrl}/identity/auth/phone/send-otp`, body).pipe(
      map(() => undefined),
    );
  }

  verifyPhoneOtp(phone: string, otp: string): Observable<VendorSession> {
    const body = { requestObject: { phone, otp, userType: 'VENDOR' } };
    return this.http.post<UamResponse<VendorLoginResponse>>(`${environment.uamApiUrl}/identity/auth/phone/verify-otp`, body).pipe(
      map((response) => this.unwrap(response)),
      tap((data) => this.applySession(data)),
      map(() => this.vendorSubject.value as VendorSession),
    );
  }

  private applySession(data: VendorLoginResponse): void {
    const token = data.accessToken || data.token;
    if (!token) {
      throw new Error('Login response did not include an access token');
    }
    localStorage.setItem('vendor_access_token', token);
    const vendor: VendorSession = {
      id: data.userId,
      name: data.fullName ?? data.username,
      email: data.email,
      username: data.username,
    };
    localStorage.setItem('vendor_data', JSON.stringify(vendor));
    this.vendorSubject.next(vendor);
  }

  private unwrap<T>(response: UamResponse<T>): T {
    if (!response.success || !response.responseObject) {
      throw {
        error: {
          detail: response.message || 'Request failed',
          message: response.message || 'Request failed',
        },
      };
    }
    return response.responseObject;
  }

  logout(): void {
    localStorage.removeItem('vendor_access_token');
    localStorage.removeItem('vendor_data');
    this.vendorSubject.next(null);
    this.router.navigate(['/vendor/login']);
  }

  isAuthenticated(): boolean {
    return !!this.vendorSubject.value && !!localStorage.getItem('vendor_access_token');
  }
}
