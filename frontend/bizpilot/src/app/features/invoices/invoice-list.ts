import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/http.util';
import { Customer, INVOICE_STATUSES, Invoice, InvoiceStatus, PageResult } from '../../core/models';
import { CustomerService } from '../../core/services/customer.service';
import { InvoiceService } from '../../core/services/invoice.service';
import { LabelPipe, MoneyPipe } from '../../shared/money.pipe';
import { PageHeader, Pagination, StateBlock, StatusBadge } from '../../shared/ui';

type FilterMode = 'all' | 'status' | 'customer' | 'dates';

/** Maps an invoice state to a tint tone for the status chip. */
function statusTone(status: InvoiceStatus): string {
  switch (status) {
    case 'PAID': return 'emerald';
    case 'OVERDUE': return 'rose';
    case 'SENT':
    case 'PARTIALLY_PAID': return 'amber';
    case 'DRAFT': return 'violet';
    default: return 'muted';
  }
}

/**
 * Invoice list. The backend applies one filter per request (status, customer or invoice-date
 * range), so the UI offers one filter at a time instead of pretending to combine them.
 */
@Component({
  selector: 'app-invoice-list',
  imports: [FormsModule, RouterLink, DatePipe, MoneyPipe, LabelPipe, PageHeader, StateBlock, Pagination, StatusBadge],
  template: `
    <div class="hero-strip">
      <app-page-header title="Invoices" subtitle="Create, send and track invoices.">
        <a routerLink="/invoices/new" class="btn btn-primary btn-grad">+ New invoice</a>
      </app-page-header>
    </div>

    <div class="card">
      <div class="toolbar">
        <div class="field inline">
          <label for="f-mode">Filter by</label>
          <select id="f-mode" class="select" [ngModel]="mode()" (ngModelChange)="setMode($event)">
            <option value="all">All invoices</option>
            <option value="status">Status</option>
            <option value="customer">Customer</option>
            <option value="dates">Invoice date range</option>
          </select>
        </div>
        @switch (mode()) {
          @case ('status') {
            <div class="field inline">
              <label for="f-status">Status</label>
              <select id="f-status" class="select" [ngModel]="status()" (ngModelChange)="status.set($event); reload()">
                @for (s of statuses; track s) { <option [value]="s">{{ s | label }}</option> }
              </select>
            </div>
          }
          @case ('customer') {
            <div class="field inline">
              <label for="f-customer">Customer</label>
              <select id="f-customer" class="select" [ngModel]="customer()" (ngModelChange)="customer.set($event); reload()">
                <option value="">Select…</option>
                @for (c of customers(); track c.id) { <option [value]="c.id">{{ c.name }}</option> }
              </select>
            </div>
          }
          @case ('dates') {
            <div class="field inline">
              <label for="f-from">From</label>
              <input id="f-from" class="input" type="date" [ngModel]="from()" (ngModelChange)="from.set($event); reload()" />
            </div>
            <div class="field inline">
              <label for="f-to">To</label>
              <input id="f-to" class="input" type="date" [ngModel]="to()" (ngModelChange)="to.set($event); reload()" />
            </div>
          }
        }
      </div>

      <app-state [loading]="loading()" [error]="error()" (retry)="load()"
        [empty]="!loading() && !error() && result().items.length === 0"
        [emptyText]="mode() === 'all' ? 'No invoices yet. Create your first invoice.' : 'No invoices match this filter.'">
        @if (mode() === 'all') { <a routerLink="/invoices/new" class="btn btn-primary btn-grad btn-sm">Create invoice</a> }
      </app-state>

      @if (!loading() && result().items.length > 0) {
        <div class="table-wrap">
          <table class="table table-click">
            <thead>
              <tr>
                <th>Number</th><th>Customer</th><th>Invoice date</th><th>Due date</th>
                <th class="num">Total</th><th class="num">Outstanding</th><th>Status</th>
              </tr>
            </thead>
            <tbody>
              @for (inv of result().items; track inv.id) {
                <tr (click)="open(inv)" tabindex="0" (keydown.enter)="open(inv)">
                  <td><a [routerLink]="['/invoices', inv.id]" (click)="$event.stopPropagation()">{{ inv.invoiceNumber }}</a></td>
                  <td>{{ inv.customerName }}</td>
                  <td>{{ inv.invoiceDate | date: 'mediumDate' }}</td>
                  <td>{{ inv.dueDate | date: 'mediumDate' }}</td>
                  <td class="num">{{ inv.totalAmount | money: inv.currency }}</td>
                  <td class="num">{{ inv.outstandingAmount | money: inv.currency }}</td>
                  <td><span class="st-chip" [attr.data-tone]="tone(inv.status)"><app-badge [status]="inv.status" /></span></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-pagination [page]="result().page" [totalPages]="result().totalPages" [totalElements]="result().totalElements" (pageChange)="goTo($event)" />
      }
    </div>
  `,
  styles: [`
    .hero-strip { background: var(--grad-primary); color: #fff; border-radius: 12px; padding: 16px 20px; margin-bottom: 16px; box-shadow: 0 4px 14px rgba(79, 70, 229, 0.25); }
    .btn-grad { background: var(--grad-primary); color: #fff; border: 0; }
    .btn-grad:hover:not(:disabled) { filter: brightness(1.08); }
    thead th { background: var(--tint-indigo); }
    .st-chip { display: inline-block; border-radius: 999px; padding: 2px 4px; }
    .st-chip[data-tone="emerald"] { background: var(--tint-emerald); }
    .st-chip[data-tone="rose"] { background: var(--tint-rose); }
    .st-chip[data-tone="amber"] { background: var(--tint-amber); }
    .st-chip[data-tone="violet"] { background: var(--tint-violet); }
  `],
})
export class InvoiceListPage implements OnInit {
  private readonly api = inject(InvoiceService);
  private readonly customerApi = inject(CustomerService);
  private readonly router = inject(Router);

  protected readonly tone = statusTone;

  /** Optional ?customerId= from the customers page. */
  readonly customerId = input<string>('');

  protected readonly statuses = INVOICE_STATUSES;
  protected readonly mode = signal<FilterMode>('all');
  protected readonly status = signal<InvoiceStatus>('SENT');
  protected readonly customer = signal('');
  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly customers = signal<Customer[]>([]);

  protected readonly result = signal<PageResult<Invoice>>({ items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  private page = 0;

  ngOnInit(): void {
    this.customerApi.all().subscribe({ next: (c) => this.customers.set(c), error: () => undefined });
    if (this.customerId()) {
      this.mode.set('customer');
      this.customer.set(this.customerId());
    }
    this.load();
  }

  setMode(mode: FilterMode): void {
    this.mode.set(mode);
    this.reload();
  }

  reload(): void {
    this.page = 0;
    this.load();
  }

  goTo(page: number): void {
    this.page = page;
    this.load();
  }

  load(): void {
    const mode = this.mode();
    if ((mode === 'customer' && !this.customer()) || (mode === 'dates' && (!this.from() || !this.to()))) {
      // Wait until the filter is complete; the backend ignores partial ranges.
      this.result.set({ items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list({
        status: mode === 'status' ? this.status() : null,
        customerId: mode === 'customer' ? this.customer() : null,
        from: mode === 'dates' ? this.from() : null,
        to: mode === 'dates' ? this.to() : null,
        page: this.page,
        size: 10,
        sort: 'invoiceDate,desc',
      })
      .subscribe({
        next: (r) => { this.result.set(r); this.loading.set(false); },
        error: (e) => { this.error.set(errorMessage(e)); this.loading.set(false); },
      });
  }

  open(inv: Invoice): void {
    void this.router.navigate(['/invoices', inv.id]);
  }
}
