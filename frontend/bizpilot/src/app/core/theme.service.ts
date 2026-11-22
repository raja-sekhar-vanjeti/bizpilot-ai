import { Injectable, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'bizpilot-theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly _theme = signal<Theme>(this.initialTheme());
  readonly theme = this._theme.asReadonly();

  init(): void {
    this.apply(this._theme());
  }

  toggle(): void {
    this.setTheme(this._theme() === 'dark' ? 'light' : 'dark');
  }

  setTheme(t: Theme): void {
    this._theme.set(t);
    this.apply(t);
    this.persist(t);
  }

  private initialTheme(): Theme {
    const stored = this.readStored();
    if (stored) {
      return stored;
    }
    return typeof window !== 'undefined' &&
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-color-scheme: dark)').matches
      ? 'dark'
      : 'light';
  }

  private readStored(): Theme | null {
    try {
      if (typeof localStorage === 'undefined') {
        return null;
      }
      const value = localStorage.getItem(STORAGE_KEY);
      return value === 'light' || value === 'dark' ? value : null;
    } catch {
      return null;
    }
  }

  private persist(t: Theme): void {
    try {
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem(STORAGE_KEY, t);
      }
    } catch {
      // Storage unavailable (private mode, quota, etc.) — theme still applies for this session.
    }
  }

  private apply(t: Theme): void {
    if (typeof document === 'undefined') {
      return;
    }
    const root = document.documentElement;
    root.setAttribute('data-theme', t);
    root.style.colorScheme = t;
  }
}
