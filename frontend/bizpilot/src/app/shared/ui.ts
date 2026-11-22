import { Component, computed, input, output } from '@angular/core';
import { label } from '../core/http.util';

/** Page title row with optional projected actions. */
@Component({
  selector: 'app-page-header',
  template: `
    <div class="page-header">
      <div>
        <h1>{{ title() }}</h1>
        @if (subtitle()) {
          <p class="muted">{{ subtitle() }}</p>
        }
      </div>
      <div class="page-actions"><ng-content /></div>
    </div>
  `,
  styles: [`
    @keyframes bp-header-in { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: none; } }
    .page-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; flex-wrap: wrap; margin-bottom: 20px; animation: bp-header-in .35s ease both; }
    .page-header h1 { margin: 0; letter-spacing: -0.02em; }
    .page-actions { display: flex; gap: 8px; flex-wrap: wrap; }
    @media (prefers-reduced-motion: reduce) { .page-header { animation: none; } }
  `],
})
export class PageHeader {
  readonly title = input.required<string>();
  readonly subtitle = input<string>('');
}

/** Loading spinner / error with retry / empty message. Render page content separately. */
@Component({
  selector: 'app-state',
  template: `
    @if (loading()) {
      <div class="state"><span class="spinner" aria-hidden="true"></span><span>Loading…</span></div>
    } @else if (error()) {
      <div class="state state-error" role="alert">
        <strong>Could not load data</strong>
        <span>{{ error() }}</span>
        <button type="button" class="btn btn-secondary btn-sm" (click)="retry.emit()">Try again</button>
      </div>
    } @else if (empty()) {
      <div class="state state-empty">
        <span class="state-icon" aria-hidden="true">∅</span>
        <span>{{ emptyText() }}</span>
        <ng-content />
      </div>
    }
  `,
  styles: [`
    .state {
      display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 10px;
      padding: 28px 20px; border-radius: 16px; text-align: center;
      border: 1px dashed var(--border, #e2e8f0);
      background: color-mix(in srgb, var(--card, #ffffff) 70%, transparent);
      color: var(--muted, #64748b);
    }
    .state-error {
      border-style: solid; color: var(--red, #dc2626);
      border-color: color-mix(in srgb, var(--red, #dc2626) 35%, transparent);
      background: color-mix(in srgb, var(--red, #dc2626) 8%, transparent);
    }
    .state-error strong { display: block; width: 100%; }
    .state-icon { font-size: 22px; opacity: .6; }
  `],
})
export class StateBlock {
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  readonly empty = input(false);
  readonly emptyText = input('Nothing here yet.');
  readonly retry = output<void>();
}

@Component({
  selector: 'app-stat-card',
  template: `
    <div class="card stat-card" [class]="'tone-' + tone()">
      <span class="stat-label">{{ label() }}</span>
      <span class="stat-value">{{ value() }}</span>
      @if (hint()) {
        <span class="stat-hint muted">{{ hint() }}</span>
      }
    </div>
  `,
  styles: [`
    @keyframes bp-stat-in { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: none; } }
    .stat-card {
      display: flex; flex-direction: column; gap: 4px; border-radius: 16px;
      background: color-mix(in srgb, var(--card, #ffffff) 80%, transparent);
      backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px);
      border: 1px solid var(--border, #e2e8f0);
      box-shadow: 0 1px 2px rgba(15, 23, 42, .04), 0 8px 24px -12px rgba(15, 23, 42, .18);
      transition: transform .2s ease, box-shadow .2s ease;
      animation: bp-stat-in .45s ease both;
    }
    .stat-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 2px 4px rgba(15, 23, 42, .05), 0 14px 32px -14px rgba(15, 23, 42, .25);
    }
    .stat-label { font-size: .8rem; font-weight: 600; color: var(--muted, #64748b); }
    .stat-value { font-size: 1.6rem; font-weight: 700; letter-spacing: -0.01em; }
    .stat-hint { font-size: .8rem; }
    .tone-good .stat-value { color: var(--green, #16a34a); }
    .tone-bad .stat-value { color: var(--red, #dc2626); }
    .tone-warn .stat-value { color: var(--amber, #d97706); }
    .tone-info .stat-value { color: var(--blue, #2563eb); }
    @media (prefers-reduced-motion: reduce) {
      .stat-card { animation: none; transition: none; }
      .stat-card:hover { transform: none; }
    }
  `],
})
export class StatCard {
  readonly label = input.required<string>();
  readonly value = input.required<string>();
  readonly hint = input<string>('');
  readonly tone = input<'default' | 'good' | 'bad' | 'warn' | 'info'>('default');
}

@Component({
  selector: 'app-badge',
  template: `<span class="badge" [class]="'badge badge-' + cls()">{{ text() }}</span>`,
  styles: [`
    .badge {
      display: inline-flex; align-items: center; gap: 4px; padding: 3px 10px; border-radius: 999px;
      font-size: .75rem; font-weight: 600; line-height: 1.4; white-space: nowrap;
      border: 1px solid transparent;
    }
    .badge-draft { color: var(--muted, #64748b); background: color-mix(in srgb, var(--muted, #64748b) 14%, transparent); border-color: color-mix(in srgb, var(--muted, #64748b) 28%, transparent); }
    .badge-sent { color: var(--blue, #2563eb); background: color-mix(in srgb, var(--blue, #2563eb) 14%, transparent); border-color: color-mix(in srgb, var(--blue, #2563eb) 30%, transparent); }
    .badge-partial { color: var(--amber, #d97706); background: color-mix(in srgb, var(--amber, #d97706) 14%, transparent); border-color: color-mix(in srgb, var(--amber, #d97706) 30%, transparent); }
    .badge-paid { color: var(--green, #16a34a); background: color-mix(in srgb, var(--green, #16a34a) 14%, transparent); border-color: color-mix(in srgb, var(--green, #16a34a) 30%, transparent); }
    .badge-overdue { color: var(--red, #dc2626); background: color-mix(in srgb, var(--red, #dc2626) 14%, transparent); border-color: color-mix(in srgb, var(--red, #dc2626) 30%, transparent); }
    .badge-cancelled { color: var(--muted, #64748b); background: transparent; border-color: var(--border, #e2e8f0); }
  `],
})
export class StatusBadge {
  readonly status = input.required<string>();
  protected readonly text = computed(() => label(this.status()));
  protected readonly cls = computed(() => {
    const map: Record<string, string> = {
      DRAFT: 'draft',
      SENT: 'sent',
      PARTIALLY_PAID: 'partial',
      PAID: 'paid',
      OVERDUE: 'overdue',
      CANCELLED: 'cancelled',
      OPEN: 'sent',
      COMPLETED: 'paid',
      DISMISSED: 'cancelled',
      UPCOMING: 'partial',
      CRITICAL: 'overdue',
      WARNING: 'partial',
      INFO: 'sent',
      PAYMENT: 'paid',
      EXPENSE: 'overdue',
    };
    return map[this.status()] ?? 'draft';
  });
}

@Component({
  selector: 'app-pagination',
  template: `
    @if (totalPages() > 1) {
      <nav class="pagination" aria-label="Pagination">
        <span class="muted">{{ totalElements() }} records · page {{ page() + 1 }} of {{ totalPages() }}</span>
        <div class="row-actions">
          <button type="button" class="btn btn-secondary btn-sm" [disabled]="page() === 0" (click)="pageChange.emit(page() - 1)">
            Previous
          </button>
          <button type="button" class="btn btn-secondary btn-sm" [disabled]="page() + 1 >= totalPages()" (click)="pageChange.emit(page() + 1)">
            Next
          </button>
        </div>
      </nav>
    }
  `,
  styles: [`
    .pagination {
      display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap;
      margin-top: 16px; padding: 12px 16px; border-radius: 16px;
      border: 1px solid var(--border, #e2e8f0);
      background: color-mix(in srgb, var(--card, #ffffff) 80%, transparent);
      backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px);
    }
  `],
})
export class Pagination {
  readonly page = input(0);
  readonly totalPages = input(0);
  readonly totalElements = input(0);
  readonly pageChange = output<number>();
}

/** Simple accessible modal dialog. */
@Component({
  selector: 'app-modal',
  host: { '(document:keydown.escape)': 'open() && closed.emit()' },
  template: `
    @if (open()) {
      <div class="modal-backdrop" (click)="closed.emit()"></div>
      <div class="modal" role="dialog" aria-modal="true" [attr.aria-label]="title()" [class.modal-wide]="wide()">
        <header class="modal-header">
          <h2>{{ title() }}</h2>
          <button type="button" class="icon-btn" aria-label="Close" (click)="closed.emit()">✕</button>
        </header>
        <div class="modal-body"><ng-content /></div>
      </div>
    }
  `,
  styles: [`
    @keyframes bp-backdrop-in { from { opacity: 0; } to { opacity: 1; } }
    @keyframes bp-modal-in { from { opacity: 0; scale: .96; } to { opacity: 1; scale: 1; } }
    .modal-backdrop {
      background: rgba(15, 23, 42, .45);
      backdrop-filter: blur(6px); -webkit-backdrop-filter: blur(6px);
      animation: bp-backdrop-in .2s ease both;
    }
    .modal {
      border-radius: 16px; border: 1px solid var(--border, #e2e8f0);
      background: color-mix(in srgb, var(--card, #ffffff) 92%, transparent);
      backdrop-filter: blur(16px); -webkit-backdrop-filter: blur(16px);
      box-shadow: 0 24px 60px -20px rgba(15, 23, 42, .35);
      animation: bp-modal-in .24s cubic-bezier(.2, .8, .2, 1) both;
    }
    @media (prefers-reduced-motion: reduce) {
      .modal-backdrop, .modal { animation: none; }
    }
  `],
})
export class Modal {
  readonly open = input(false);
  readonly title = input('');
  readonly wide = input(false);
  readonly closed = output<void>();
}
