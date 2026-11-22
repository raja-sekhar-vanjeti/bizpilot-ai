import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/http.util';
import { ToastService } from '../../core/toast.service';
import { fieldError } from '../../shared/form-errors';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <section class="auth-hero">
        <div class="brand brand-light"><span class="brand-mark">B</span><span class="brand-text">BizPilot <em>AI</em></span></div>
        <h2>Set up your business in a minute.</h2>
        <p>Each account is a private workspace — your customers, invoices and cash flow are visible only to you.</p>
      </section>
      <section class="auth-card card">
        <h1>Create your account</h1>
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <div class="form-grid">
            <div class="field">
              <label for="fullName">Full name</label>
              <input id="fullName" class="input" formControlName="fullName" autocomplete="name" />
              @if (err(form.controls.fullName, 'Full name'); as e) { <span class="form-error">{{ e }}</span> }
            </div>
            <div class="field">
              <label for="businessName">Business name</label>
              <input id="businessName" class="input" formControlName="businessName" autocomplete="organization" />
              @if (err(form.controls.businessName, 'Business name'); as e) { <span class="form-error">{{ e }}</span> }
            </div>
          </div>
          <div class="field">
            <label for="email">Email</label>
            <input id="email" class="input" type="email" formControlName="email" autocomplete="email" />
            @if (err(form.controls.email, 'Email'); as e) { <span class="form-error">{{ e }}</span> }
          </div>
          <div class="field">
            <label for="phone">Phone <span class="optional">(optional)</span></label>
            <input id="phone" class="input" type="tel" formControlName="phone" autocomplete="tel" />
            @if (err(form.controls.phone, 'Phone'); as e) { <span class="form-error">{{ e }}</span> }
          </div>
          <div class="form-grid">
            <div class="field">
              <label for="password">Password</label>
              <input id="password" class="input" type="password" formControlName="password" autocomplete="new-password" />
              @if (err(form.controls.password, 'Password'); as e) { <span class="form-error">{{ e }}</span> }
            </div>
            <div class="field">
              <label for="confirm">Confirm password</label>
              <input id="confirm" class="input" type="password" formControlName="confirm" autocomplete="new-password" />
              @if (form.controls.confirm.touched && form.controls.confirm.value !== form.controls.password.value) {
                <span class="form-error">Passwords do not match</span>
              }
            </div>
          </div>
          @if (error()) {
            <div class="alert alert-error" role="alert">{{ error() }}</div>
          }
          <button type="submit" class="btn btn-primary btn-block grad-btn" [disabled]="busy()">
            {{ busy() ? 'Creating account…' : 'Create account' }}
          </button>
        </form>
        <p class="auth-switch">Already have an account? <a routerLink="/login">Sign in</a></p>
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
export class RegisterPage {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly err = fieldError;
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  // Limits match the backend RegisterRequest and users table.
  protected readonly form = this.fb.group({
    fullName: ['', [Validators.required, Validators.maxLength(120)]],
    businessName: ['', [Validators.required, Validators.maxLength(160)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    phone: ['', [Validators.maxLength(20)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(100)]],
    confirm: ['', [Validators.required]],
  });

  submit(): void {
    const v = this.form.getRawValue();
    if (this.form.invalid || v.password !== v.confirm) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.auth
      .register({
        fullName: v.fullName.trim(),
        businessName: v.businessName.trim(),
        email: v.email.trim(),
        password: v.password,
        phone: v.phone.trim() || null,
      })
      .subscribe({
        next: () => {
          this.toast.success('Account created. Welcome to BizPilot!');
          void this.router.navigateByUrl('/dashboard');
        },
        error: (e) => {
          this.busy.set(false);
          this.error.set(errorMessage(e, 'Could not create the account.'));
        },
      });
  }
}
