import { Component, OnInit, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../core/auth.service';

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
          <span class="brand-text">BizPilot <em>AI</em></span>
        </a>
        <nav>
          @for (section of sections; track section.title) {
            <p class="nav-section">{{ section.title }}</p>
            @for (item of section.items; track item.path) {
              <a [routerLink]="item.path" routerLinkActive="active" class="nav-link">
                <span class="nav-icon" aria-hidden="true">{{ item.icon }}</span>{{ item.label }}
              </a>
            }
          }
        </nav>
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
          <button type="button" class="btn btn-secondary btn-sm" (click)="auth.logout()">Sign out</button>
        </header>
        <main class="content">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
})
export class Shell implements OnInit {
  protected readonly auth = inject(AuthService);
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
