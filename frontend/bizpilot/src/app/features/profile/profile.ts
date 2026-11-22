import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { API_BASE_URL } from '../../core/api.config';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/http.util';
import { PageHeader, StateBlock } from '../../shared/ui';

/**
 * Profile & settings. The backend exposes GET /api/auth/me only — there is no endpoint to update
 * the profile or change the password, so those are shown read-only rather than faked.
 */
@Component({
  selector: 'app-profile',
  imports: [DatePipe, PageHeader, StateBlock],
  template: `
    <app-page-header title="Profile & settings" subtitle="Your account and session." />

    <app-state [loading]="loading()" [error]="error()" (retry)="load()" />

    @if (!loading() && auth.profile(); as p) {
      <div class="grid two-col">
        <section class="card">
          <div class="profile-head">
            <span class="avatar avatar-lg">{{ initials(p.fullName) }}</span>
            <div>
              <h2>{{ p.fullName }}</h2>
              <p class="muted">{{ p.businessName }}</p>
            </div>
          </div>
          <dl class="detail-list">
            <dt>Email</dt><dd>{{ p.email }}</dd>
            <dt>Phone</dt><dd>{{ p.phone || '—' }}</dd>
            <dt>Business</dt><dd>{{ p.businessName }}</dd>
            <dt>Member since</dt><dd>{{ p.createdAt | date: 'longDate' }}</dd>
          </dl>
          <p class="muted small">
            Profile details are read-only: the backend does not provide an endpoint to update the profile or change the password yet.
          </p>
        </section>

        <section class="card">
          <h2>Session &amp; security</h2>
          <dl class="detail-list">
            <dt>Signed in as</dt><dd>{{ p.email }}</dd>
            <dt>Session expires</dt><dd>{{ expiresAt() ? (expiresAt() | date: 'medium') : '—' }}</dd>
            <dt>Authentication</dt><dd>JWT bearer token</dd>
            <dt>Data access</dt><dd>Only your business's records are visible to this account.</dd>
          </dl>
          <button type="button" class="btn btn-danger" (click)="auth.logout()">Sign out</button>
        </section>

        <section class="card">
          <h2>Application</h2>
          <dl class="detail-list">
            <dt>API server</dt><dd><code>{{ apiUrl }}</code></dd>
            <dt>Currency display</dt><dd>Indian Rupee (₹), Indian number grouping</dd>
            <dt>Reminders</dt><dd>Drafted only — never sent automatically</dd>
            <dt>Insights</dt><dd>Rule-based, computed on the server; no external AI service</dd>
          </dl>
        </section>
      </div>
    }
  `,
})
export class ProfilePage implements OnInit {
  protected readonly auth = inject(AuthService);
  protected readonly apiUrl = API_BASE_URL;
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.auth.loadProfile().subscribe({
      next: () => this.loading.set(false),
      error: (e) => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }

  expiresAt(): number | null {
    return this.auth.expiresAt();
  }

  initials(name: string): string {
    return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]?.toUpperCase()).join('') || '•';
  }
}
