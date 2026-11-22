import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/http.util';
import { ToastService } from '../../core/toast.service';
import { fieldError } from '../../shared/form-errors';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <section class="auth-hero">
        <div class="brand brand-light"><span class="brand-mark">B</span><span class="brand-text">BizPilot <em>AI</em></span></div>
        <h2>Your smart back office.</h2>
        <p>Invoices, payments, expenses, reminders and a cash-flow forecast — all in one place for freelancers and small businesses.</p>
        <ul>
          <li>30 / 60 / 90-day cash-flow forecasts</li>
          <li>What-if payment delay simulation</li>
          <li>Rule-based financial insights</li>
        </ul>
      </section>
      <section class="auth-card card">
        <h1>Welcome back</h1>
        <p class="muted">Sign in to your business account.</p>
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <div class="field">
            <label for="email">Email</label>
            <input id="email" class="input" type="email" formControlName="email" autocomplete="email" />
            @if (err(form.controls.email, 'Email'); as e) { <span class="form-error">{{ e }}</span> }
          </div>
          <div class="field">
            <label for="password">Password</label>
            <input id="password" class="input" type="password" formControlName="password" autocomplete="current-password" />
            @if (err(form.controls.password, 'Password'); as e) { <span class="form-error">{{ e }}</span> }
          </div>
          @if (error()) {
            <div class="alert alert-error" role="alert">{{ error() }}</div>
          }
          <button type="submit" class="btn btn-primary btn-block grad-btn" [disabled]="busy()">
            {{ busy() ? 'Signing in…' : 'Sign in' }}
          </button>
        </form>
        <p class="auth-switch">New to BizPilot? <a routerLink="/register">Create an account</a></p>
      </section>
    </div>
  `,
  styles: [`
    .auth-hero {
      position: relative;
      overflow: hidden;
      background: var(--grad-primary);
      color: #fff;
      box-shadow: 0 18px 40px rgba(79, 70, 229, 0.28);
    }
    .auth-hero > * { position: relative; z-index: 1; }
    .auth-hero h2, .auth-hero p, .auth-hero li { color: #fff; }
    .auth-hero::before,
    .auth-hero::after {
      content: '';
      position: absolute;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.14);
      pointer-events: none;
      z-index: 0;
    }
    .auth-hero::before { width: 220px; height: 220px; top: -70px; right: -60px; }
    .auth-hero::after { width: 120px; height: 120px; bottom: -40px; left: -30px; background: rgba(255, 255, 255, 0.1); }
    @media (prefers-reduced-motion: no-preference) {
      .auth-hero::before { animation: auth-float 9s ease-in-out infinite; }
      .auth-hero::after { animation: auth-float 12s ease-in-out infinite reverse; }
    }
    @keyframes auth-float {
      0%, 100% { transform: translateY(0); }
      50% { transform: translateY(-14px); }
    }
    .btn.grad-btn {
      background: var(--grad-primary);
      border: none;
      color: #fff;
      box-shadow: 0 10px 24px rgba(79, 70, 229, 0.32);
    }
    .btn.grad-btn:hover:not(:disabled) { filter: brightness(1.07); }
    .btn.grad-btn:disabled { opacity: 0.7; }
  `],
})
export class LoginPage {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  /** Bound from the ?returnUrl= query parameter. */
  readonly returnUrl = input<string>('');

  protected readonly err = fieldError;
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => {
        this.toast.success('Signed in');
        const target = this.returnUrl() && this.returnUrl().startsWith('/') ? this.returnUrl() : '/dashboard';
        void this.router.navigateByUrl(target);
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e, 'Invalid email or password.'));
      },
    });
  }
}
