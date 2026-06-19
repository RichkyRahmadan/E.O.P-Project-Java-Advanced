import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
}

export interface RegisterResponse {
  message: string;
  username: string;
  status: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);

  private readonly ACCESS_TOKEN_KEY = 'eop_access_token';
  private readonly REFRESH_TOKEN_KEY = 'eop_refresh_token';

  login(credentials: any): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', credentials).pipe(
      tap(res => this.saveSession(res))
    );
  }

  registerUser(userData: any): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>('/api/auth/register', userData);
  }

  registerMerchant(merchantData: any): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>('/api/auth/register/merchant', merchantData);
  }

  refreshSession(refreshToken: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/refresh', { refreshToken }).pipe(
      tap(res => this.saveSession(res))
    );
  }

  logout(): Observable<void> {
    const refreshToken = this.getRefreshToken() || '';
    return this.http.post<void>('/api/auth/logout', null, {
      headers: { 'X-Refresh-Token': refreshToken }
    }).pipe(
      tap(() => this.clearSession())
    );
  }

  saveSession(session: AuthResponse): void {
    localStorage.setItem(this.ACCESS_TOKEN_KEY, session.accessToken);
    localStorage.setItem(this.REFRESH_TOKEN_KEY, session.refreshToken);
  }

  clearSession(): void {
    localStorage.removeItem(this.ACCESS_TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_TOKEN_KEY);
  }

  getAccessToken(): string | null {
    return localStorage.getItem(this.ACCESS_TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(this.REFRESH_TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    return !!this.getAccessToken();
  }

  // JWT Helper decoding
  private getDecodedToken(): any {
    const token = this.getAccessToken();
    if (!token) return null;
    try {
      const payload = token.split('.')[1];
      const decoded = JSON.parse(atob(payload));
      return decoded;
    } catch (e) {
      return null;
    }
  }

  getUserId(): string | null {
    const decoded = this.getDecodedToken();
    return decoded ? decoded.sub : null;
  }

  getUserRole(): string | null {
    const decoded = this.getDecodedToken();
    if (!decoded || !decoded.role) return null;
    // Bersihkan prefix ROLE_ (contoh: ROLE_ADMIN -> ADMIN) agar sesuai dengan UI route
    return decoded.role.startsWith('ROLE_') ? decoded.role.substring(5) : decoded.role;
  }

  getUserPermissions(): string[] {
    const decoded = this.getDecodedToken();
    if (!decoded || !decoded.permissions) return [];
    return decoded.permissions.split(',');
  }

  getUserStatus(): string | null {
    const decoded = this.getDecodedToken();
    return decoded ? decoded.status : null;
  }

  hasPermission(permission: string): boolean {
    return this.getUserPermissions().includes(permission);
  }
}
