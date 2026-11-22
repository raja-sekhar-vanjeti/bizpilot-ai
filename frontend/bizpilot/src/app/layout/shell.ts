import { Component, OnInit, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { ThemeService } from '../core/theme.service';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

interface NavSection {
  title: string;
  items: NavItem[];
}

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="shell" [class.nav-open]="navOpen()">
      <aside class="sidebar" aria-label="Main navigation">
        <a routerLink="/dashboard" class="brand">
          <span class="brand-mark">B</span>
          <span class="brand-text">BizPilot <em>AI</em><small class="brand-sub">Business co-pilot</small></span>
        </a>
        <nav>
          <p class="space-label">Your space</p>
          @for (section of sections; track section.title) {
            <p class="nav-section">{{ section.title }}</p>
            @for (item of section.items; track item.path) {
              <a [routerLink]="item.path" routerLinkActive="active" class="nav-link" [attr.data-group]="section.title">
                <span class="nav-icon" aria-hidden="true">{{ item.icon }}</span>{{ item.label }}
              </a>
            }
          }
        </nav>
        <div class="promo">
          <strong>One small step a day.</strong>
          <p>Keep your invoices and reminders moving forward.</p>
          <a routerLink="/dashboard" class="promo-btn">Open dashboard</a>
        </div>
      </aside>

      <div class="nav-scrim" (click)="navOpen.set(false)"></div>

      <div class="main">
        <header class="topbar">
          <button type="button" class="icon-btn menu-btn" aria-label="Open menu" (click)="navOpen.set(!navOpen())">☰</button>
          <div class="topbar-title">
            <strong>{{ auth.profile()?.businessName ?? 'BizPilot' }}</strong>
          </div>
          <a routerLink="/profile" class="user-chip" title="Profile and settings">
            <span class="avatar">{{ initials() }}</span>
            <span class="user-name">{{ auth.profile()?.fullName ?? '' }}</span>
          </a>
          <button
            type="button"
            class="theme-toggle"
            [attr.aria-label]="themeService.theme() === 'light' ? 'Switch to dark theme' : 'Switch to light theme'"
            (click)="themeService.toggle()"
          >
            @if (themeService.theme() === 'dark') {
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
                <circle cx="12" cy="12" r="4" />
                <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
              </svg>
            } @else {
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
              </svg>
            }
          </button>
          <button type="button" class="btn btn-secondary btn-sm" (click)="auth.logout()">Sign out</button>
        </header>
        <main class="content">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
  styles: `
    .sidebar {
      margin: 16px;
      border-radius: 28px;
      background: color-mix(in srgb, var(--card, #fff) 72%, transparent);
      backdrop-filter: blur(18px);
      -webkit-backdrop-filter: blur(18px);
      border: 1px solid color-mix(in srgb, var(--border, #e2e8f0) 70%, transparent);
      box-shadow: 0 18px 40px -24px color-mix(in srgb, var(--sage-deep, #3f6b57) 35%, transparent);
      padding: 20px 14px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 4px 8px 16px;
      color: var(--text, #0f172a);
      text-decoration: none;
    }
    .brand-mark {
      width: 36px;
      height: 36px;
      display: inline-grid;
      place-items: center;
      background: var(--sage-deep, #3f6b57);
      color: #fff;
      border-radius: 10px;
      font-weight: 700;
      box-shadow: 0 6px 14px -8px color-mix(in srgb, var(--sage-deep, #3f6b57) 80%, transparent);
    }
    .brand-text {
      display: flex;
      flex-direction: column;
      line-height: 1.15;
      color: var(--text, #0f172a);
      font-weight: 700;
    }
    .brand-text em {
      font-style: normal;
      color: var(--text, #0f172a);
    }
    .brand-sub {
      font-size: 0.62rem;
      font-weight: 500;
      letter-spacing: 0.14em;
      text-transform: uppercase;
      color: var(--muted, #64748b);
    }
    .space-label {
      margin: 12px 10px 6px;
      font-size: 0.68rem;
      font-weight: 600;
      letter-spacing: 0.16em;
      text-transform: uppercase;
      color: var(--muted, #64748b);
    }
    .nav-section {
      margin: 12px 10px 4px;
      font-size: 0.66rem;
      font-weight: 600;
      letter-spacing: 0.14em;
      text-transform: uppercase;
      color: color-mix(in srgb, var(--muted, #64748b) 80%, transparent);
    }
    .nav-link {
      position: relative;
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 9px 12px;
      border-radius: 999px;
      color: var(--muted, #64748b);
      text-decoration: none;
      transition: background-color 0.2s ease, color 0.2s ease, box-shadow 0.2s ease;
      --accent: var(--sage-deep, #3f6b57);
    }
    .nav-link .nav-icon {
      color: var(--accent);
      width: 18px;
      text-align: center;
    }
    .nav-link:hover {
      background: color-mix(in srgb, var(--card, #fff) 60%, transparent);
      color: var(--text, inherit);
    }
    .nav-link.active {
      background: #fff;
      color: var(--text, #0f172a);
      font-weight: 600;
      box-shadow: 0 8px 20px -12px color-mix(in srgb, var(--sage-deep, #3f6b57) 45%, transparent);
    }
    .nav-link.active .nav-icon {
      color: var(--sage-deep, #3f6b57);
    }
    .promo {
      margin-top: auto;
      padding: 16px;
      border-radius: 22px;
      background: var(--grad-primary, linear-gradient(160deg, var(--sage-soft, #dfe9e1), var(--sage, #8fb39b)));
      color: var(--text, #0f172a);
    }
    .promo strong {
      display: block;
      font-size: 0.9rem;
      margin-bottom: 4px;
    }
    .promo p {
      margin: 0 0 12px;
      font-size: 0.75rem;
      color: var(--muted, #64748b);
      line-height: 1.4;
    }
    .promo-btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 7px 14px;
      border-radius: 999px;
      background: var(--sage-deep, #3f6b57);
      color: #fff;
      font-size: 0.78rem;
      font-weight: 600;
      text-decoration: none;
    }
    .topbar {
      position: sticky;
      top: 0;
      z-index: 5;
      background: transparent;
      border-bottom: none;
    }
    .theme-toggle {
      display: inline-grid;
      place-items: center;
      width: 40px;
      height: 40px;
      border-radius: 50%;
      border: 1px solid color-mix(in srgb, var(--border, #e2e8f0) 70%, transparent);
      background: color-mix(in srgb, #fff 70%, transparent);
      color: var(--text, inherit);
      box-shadow: 0 6px 16px -10px color-mix(in srgb, var(--sage-deep, #3f6b57) 40%, transparent);
      cursor: pointer;
      transition: background-color 0.2s ease, transform 0.2s ease;
    }
    .theme-toggle:hover {
      background: #fff;
      transform: translateY(-1px);
    }
    .theme-toggle:focus-visible {
      outline: 2px solid var(--sage-deep, #3f6b57);
      outline-offset: 2px;
    }
    .theme-toggle svg {
      width: 18px;
      height: 18px;
    }
  `,
})
export class Shell implements OnInit {
  protected readonly auth = inject(AuthService);
  protected readonly themeService = inject(ThemeService);
  private readonly router = inject(Router);
  protected readonly navOpen = signal(false);

  protected readonly sections: NavSection[] = [
    {
      title: 'Overview',
      items: [
        { path: '/dashboard', label: 'Dashboard', icon: '▦' },
        { path: '/insights', label: 'Insights', icon: '✦' },
      ],
    },
    {
      title: 'Sales',
      items: [
        { path: '/customers', label: 'Customers', icon: '☺' },
        { path: '/invoices', label: 'Invoices', icon: '▤' },
        { path: '/payments', label: 'Payments', icon: '₹' },
        { path: '/reminders', label: 'Reminders', icon: '⏰' },
      ],
    },
    {
      title: 'Spending',
      items: [{ path: '/expenses', label: 'Expenses', icon: '⇣' }],
    },
    {
      title: 'Planning',
      items: [
        { path: '/cashflow', label: 'Cash-flow Forecast', icon: '↗' },
        { path: '/what-if', label: 'What-if Simulation', icon: '⇄' },
      ],
    },
    {
      title: 'Account',
      items: [{ path: '/profile', label: 'Profile & Settings', icon: '⚙' }],
    },
  ];

  ngOnInit(): void {
    if (!this.auth.profile()) {
      this.auth.loadProfile().subscribe({ error: () => undefined });
    }
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => this.navOpen.set(false));
  }

  protected initials(): string {
    const name = this.auth.profile()?.fullName ?? '';
    return (
      name
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map((p) => p[0]?.toUpperCase())
        .join('') || '•'
    );
  }
}
