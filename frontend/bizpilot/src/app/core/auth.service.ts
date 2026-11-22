import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, map, tap } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { ApiResponse, AuthResponse, LoginRequest, RegisterRequest, UserProfile } from './models';

const TOKEN_KEY = 'bizpilot.token';
const EXPIRES_KEY = 'bizpilot.expiresAt';

interface StoredSession {
  token: string;
  expiresAt: number;
}

function readSession(): StoredSession | null {
  try {
    const token = localStorage.getItem(TOKEN_KEY);
    const expiresAt = Number(localStorage.getItem(EXPIRES_KEY));
    if (token && expiresAt && expiresAt > Date.now()) {
      return { token, expiresAt };
    }
  } catch {
    // Storage unavailable (private mode): start logged out.
  }
  return null;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly url = `${API_BASE_URL}/auth`;

  private readonly session = signal<StoredSession | null>(readSession());
  readonly profile = signal<UserProfile | null>(null);

  readonly isAuthenticated = computed(() => {
    const s = this.session();
    return !!s && s.expiresAt > Date.now();
  });

  token(): string | null {
    const s = this.session();
    return s && s.expiresAt > Date.now() ? s.token : null;
  }

  expiresAt(): number | null {
    return this.session()?.expiresAt ?? null;
  }

  login(request: LoginRequest): Observable<void> {
    return this.http.post<ApiResponse<AuthResponse>>(`${this.url}/login`, request).pipe(
      tap((res) => this.store(res.data)),
      map(() => undefined),
    );
  }

  register(request: RegisterRequest): Observable<void> {
    return this.http.post<ApiResponse<AuthResponse>>(`${this.url}/register`, request).pipe(
      tap((res) => this.store(res.data)),
      map(() => undefined),
    );
  }

  loadProfile(): Observable<UserProfile> {
    return this.http.get<ApiResponse<UserProfile>>(`${this.url}/me`).pipe(
      map((res) => res.data),
      tap((profile) => this.profile.set(profile)),
    );
  }

  logout(redirect = true): void {
    try {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(EXPIRES_KEY);
    } catch {
      // ignore
    }
    this.session.set(null);
    this.profile.set(null);
    if (redirect) {
      void this.router.navigate(['/login']);
    }
  }

  private store(auth: AuthResponse): void {
    const session = { token: auth.token, expiresAt: Date.now() + auth.expiresInSeconds * 1000 };
    try {
      localStorage.setItem(TOKEN_KEY, session.token);
      localStorage.setItem(EXPIRES_KEY, String(session.expiresAt));
    } catch {
      // The session still works for this tab.
    }
    this.session.set(session);
  }
}
