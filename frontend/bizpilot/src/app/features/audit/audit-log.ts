import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { errorMessage, label } from '../../core/http.util';
import { AuditLogEntry, PageResult } from '../../core/models';
import { AuditLogService } from '../../core/services/audit.service';
import { PageHeader, Pagination, StateBlock } from '../../shared/ui';

const PAGE_SIZE = 20;

const FILTERS: { value: string | null; label: string }[] = [
  { value: null, label: 'All' },
  { value: 'CUSTOMER', label: 'Customer' },
  { value: 'INVOICE', label: 'Invoice' },
  { value: 'PAYMENT', label: 'Payment' },
  { value: 'EXPENSE', label: 'Expense' },
  { value: 'REMINDER', label: 'Reminder' },
];

@Component({
  selector: 'app-audit-log',
  imports: [DatePipe, PageHeader, StateBlock, Pagination],
  template: `
    <app-page-header title="Audit log" subtitle="A record of changes made in your account.">
      <button type="button" class="btn btn-secondary btn-sm" [disabled]="loading()" (click)="load()">Refresh</button>
    </app-page-header>

    <div class="filters" role="group" aria-label="Filter by entity type">
      @for (f of filters; track f.label) {
        <button
          type="button"
          class="filter"
          [class.active]="filter() === f.value"
          [attr.aria-pressed]="filter() === f.value"
          (click)="setFilter(f.value)"
        >{{ f.label }}</button>
      }
    </div>

    <div class="card">
      <app-state
        [loading]="loading()"
        [error]="error()"
        (retry)="load()"
        [empty]="!loading() && !error() && result().items.length === 0"
        [emptyText]="filter() ? 'No audit entries for this type yet.' : 'No audit entries yet.'"
      />

      @if (!loading() && result().items.length > 0) {
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr><th>When</th><th>Action</th><th>Entity</th><th>Summary</th></tr>
            </thead>
            <tbody>
              @for (e of result().items; track e.id) {
                <tr>
                  <td class="when">{{ e.createdAt | date: 'medium' }}</td>
                  <td><span class="act" [class]="'act act-' + e.action.toLowerCase()">{{ label(e.action) }}</span></td>
                  <td>
                    <strong>{{ label(e.entityType) }}</strong>
                    <span class="muted entity-id">#{{ e.entityId }}</span>
                  </td>
                  <td>{{ e.summary }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-pagination
          [page]="result().page"
          [totalPages]="result().totalPages"
          [totalElements]="result().totalElements"
          (pageChange)="goTo($event)"
        />
      }
    </div>
  `,
  styles: [`
    .filters { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
    .filter {
      padding: 6px 14px; border-radius: 999px; cursor: pointer; font: inherit; font-size: .82rem; font-weight: 600;
      border: 1px solid var(--border, #e2e8f0); background: color-mix(in srgb, var(--card, #ffffff) 80%, transparent);
      color: var(--muted, #64748b);
    }
    .filter:hover:not(.active) { color: var(--text, #0f172a); }
    .filter.active { background: var(--grad-primary); color: #fff; border-color: transparent; }
    .filter:focus-visible { outline: 2px solid var(--sage-deep, #3f6b57); outline-offset: 2px; }
    .when { white-space: nowrap; }
    .entity-id { margin-left: 6px; font-size: .8rem; }
    .act {
      display: inline-flex; padding: 3px 10px; border-radius: 999px; border: 1px solid transparent;
      font-size: .72rem; font-weight: 700; letter-spacing: .04em; white-space: nowrap;
    }
    .act-create { background: var(--tint-emerald); color: var(--green, #16a34a); border-color: color-mix(in srgb, var(--green, #16a34a) 30%, transparent); }
    .act-update { background: var(--tint-violet); color: #7e22ce; border-color: color-mix(in srgb, #7e22ce 30%, transparent); }
    .act-delete { background: var(--tint-rose); color: var(--red, #dc2626); border-color: color-mix(in srgb, var(--red, #dc2626) 30%, transparent); }
    .act-payment { background: var(--tint-indigo); color: #4338ca; border-color: color-mix(in srgb, #4338ca 30%, transparent); }
    .act-send { background: var(--tint-amber); color: var(--amber, #d97706); border-color: color-mix(in srgb, var(--amber, #d97706) 30%, transparent); }
  `],
})
export class AuditLogPage implements OnInit {
  private readonly api = inject(AuditLogService);

  protected readonly filters = FILTERS;
  protected readonly label = label;
  protected readonly filter = signal<string | null>(null);
  protected readonly result = signal<PageResult<AuditLogEntry>>({ items: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 });
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  private page = 0;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list(this.filter() ?? undefined, this.page, PAGE_SIZE).subscribe({
      next: (r) => { this.result.set(r); this.loading.set(false); },
      error: (e) => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }

  setFilter(value: string | null): void {
    if (this.filter() === value) return;
    this.filter.set(value);
    this.page = 0;
    this.load();
  }

  goTo(page: number): void {
    this.page = page;
    this.load();
  }
}
