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
})
export class Modal {
  readonly open = input(false);
  readonly title = input('');
  readonly wide = input(false);
  readonly closed = output<void>();
}
