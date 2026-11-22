import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/http.util';
import { Invoice, PageResult, Payment } from '../../core/models';
import { InvoiceService } from '../../core/services/invoice.service';
import { PaymentService } from '../../core/services/payment.service';
import { ToastService } from '../../core/toast.service';
import { LabelPipe, MoneyPipe } from '../../shared/money.pipe';
import { Modal, PageHeader, Pagination, StateBlock } from '../../shared/ui';
import { RecordPayment } from './record-payment';

/**
 * Payments received. PaymentResponse carries only invoiceId, so invoice numbers and customers
 * are resolved from the invoice list. Payments cannot be edited or deleted (no backend endpoint).
 */
@Component({
  selector: 'app-payments',
  imports: [RouterLink, DatePipe, MoneyPipe, LabelPipe, PageHeader, StateBlock, Pagination, Modal, RecordPayment],
  template: `
    <div aria-hidden="true" style="height:4px;border-radius:4px;background:var(--grad-success);margin-bottom:14px"></div>
    <app-page-header title="Payments" subtitle="Money received against your invoices.">
      <button type="button" class="btn btn-primary" style="background:var(--grad-success);border-color:transparent;color:#fff" (click)="openRecord()">+ Record payment</button>
    </app-page-header>

    <div class="stats-grid">
      <div class="stat-card tone-emerald" style="background:var(--tint-emerald)">
        <span class="stat-label">Payments recorded</span>
        <span class="stat-value">{{ result().totalElements }}</span>
      </div>
    </div>

    <div class="card">
      <app-state [loading]="loading()" [error]="error()" (retry)="load()"
        [empty]="!loading() && !error() && result().items.length === 0"
        emptyText="No payments recorded yet. Record a payment when a customer pays an invoice." />

      @if (!loading() && result().items.length > 0) {
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr><th>Date</th><th>Invoice</th><th>Customer</th><th>Method</th><th>Reference</th><th class="num">Amount</th></tr>
            </thead>
            <tbody>
              @for (p of result().items; track p.id) {
                <tr>
                  <td>{{ p.paymentDate | date: 'mediumDate' }}</td>
                  <td>
                    <a [routerLink]="['/invoices', p.invoiceId]">{{ invoiceById().get(p.invoiceId)?.invoiceNumber ?? 'View invoice' }}</a>
                  </td>
                  <td>{{ invoiceById().get(p.invoiceId)?.customerName ?? '—' }}</td>
                  <td><span class="chip" style="background:var(--tint-indigo);color:#4338ca">{{ p.method | label }}</span></td>
                  <td>{{ p.reference || '—' }}</td>
                  <td class="num text-good">{{ p.amount | money: (invoiceById().get(p.invoiceId)?.currency ?? 'INR') }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-pagination [page]="result().page" [totalPages]="result().totalPages" [totalElements]="result().totalElements" (pageChange)="goTo($event)" />
      }
    </div>

    <app-modal [open]="recordOpen()" title="Record payment" (closed)="recordOpen.set(false)">
      @if (recordOpen()) {
        <app-record-payment [invoices]="openInvoices()" (saved)="onSaved($event)" (cancelled)="recordOpen.set(false)" />
      }
    </app-modal>
  `,
})
export class PaymentsPage implements OnInit {
  private readonly api = inject(PaymentService);
  private readonly invoiceApi = inject(InvoiceService);
  private readonly toast = inject(ToastService);

  protected readonly result = signal<PageResult<Payment>>({ items: [], page: 0, size: 15, totalElements: 0, totalPages: 0 });
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly invoices = signal<Invoice[]>([]);
  protected readonly recordOpen = signal(false);
  private page = 0;

  protected readonly invoiceById = computed(() => new Map(this.invoices().map((i) => [i.id, i])));
  protected readonly openInvoices = computed(() =>
    this.invoices().filter(
      (i) => (i.status === 'SENT' || i.status === 'PARTIALLY_PAID' || i.status === 'OVERDUE') && Number(i.outstandingAmount) > 0,
    ),
  );

  ngOnInit(): void {
    this.loadInvoices();
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list({ page: this.page, size: 15, sort: 'paymentDate,desc' }).subscribe({
      next: (r) => { this.result.set(r); this.loading.set(false); },
      error: (e) => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }

  goTo(page: number): void {
    this.page = page;
    this.load();
  }

  openRecord(): void {
    this.loadInvoices();
    this.recordOpen.set(true);
  }

  onSaved(p: Payment): void {
    this.recordOpen.set(false);
    this.toast.success(`Payment of ${p.amount} recorded`);
    this.page = 0;
    this.load();
    this.loadInvoices();
  }

  private loadInvoices(): void {
    this.invoiceApi.list({ page: 0, size: 500, sort: 'dueDate' }).subscribe({
      next: (r) => this.invoices.set(r.items),
      error: () => undefined,
    });
  }
}
