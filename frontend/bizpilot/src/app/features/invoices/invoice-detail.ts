import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/http.util';
import { Invoice, Payment } from '../../core/models';
import { InvoiceService } from '../../core/services/invoice.service';
import { ToastService } from '../../core/toast.service';
import { LabelPipe, MoneyPipe } from '../../shared/money.pipe';
import { Modal, PageHeader, StateBlock, StatusBadge } from '../../shared/ui';
import { RecordPayment } from '../payments/record-payment';

@Component({
  selector: 'app-invoice-detail',
  imports: [RouterLink, DatePipe, MoneyPipe, LabelPipe, PageHeader, StateBlock, StatusBadge, Modal, RecordPayment],
  template: `
    <app-state [loading]="loading()" [error]="error()" (retry)="load()" />

    @if (invoice(); as inv) {
      <app-page-header [title]="'Invoice ' + inv.invoiceNumber" [subtitle]="inv.customerName">
        <a routerLink="/invoices" class="btn btn-secondary">All invoices</a>
        @if (inv.status === 'DRAFT') {
          <a [routerLink]="['/invoices', inv.id, 'edit']" class="btn btn-secondary">Edit</a>
          <button type="button" class="btn btn-primary" [disabled]="busy()" (click)="send()">Send invoice</button>
        }
        @if (canPay()) {
          <button type="button" class="btn btn-primary" (click)="payOpen.set(true)">Record payment</button>
        }
        @if (canCancel()) {
          <button type="button" class="btn btn-ghost-danger" (click)="confirmCancel.set(true)">Cancel invoice</button>
        }
        <button type="button" class="btn btn-secondary" (click)="print()">Print</button>
      </app-page-header>

      <div class="grid stats-grid">
        <div class="card stat-card"><span class="stat-label">Status</span><span><app-badge [status]="inv.status" /></span>
          @if (inv.sentAt) { <span class="stat-hint muted">Sent {{ inv.sentAt | date: 'medium' }}</span> }
        </div>
        <div class="card stat-card"><span class="stat-label">Total</span><span class="stat-value">{{ inv.totalAmount | money: inv.currency }}</span></div>
        <div class="card stat-card tone-good"><span class="stat-label">Paid</span><span class="stat-value">{{ inv.paidAmount | money: inv.currency }}</span></div>
        <div class="card stat-card" [class.tone-warn]="inv.outstandingAmount > 0">
          <span class="stat-label">Outstanding</span><span class="stat-value">{{ inv.outstandingAmount | money: inv.currency }}</span>
          <span class="stat-hint muted">Due {{ inv.dueDate | date: 'mediumDate' }}</span>
        </div>
      </div>

      <section class="card printable">
        <div class="invoice-head">
          <div>
            <h2>{{ inv.invoiceNumber }}</h2>
            <p class="muted">Billed to <strong>{{ inv.customerName }}</strong></p>
          </div>
          <dl class="meta">
            <dt>Invoice date</dt><dd>{{ inv.invoiceDate | date: 'mediumDate' }}</dd>
            <dt>Due date</dt><dd>{{ inv.dueDate | date: 'mediumDate' }}</dd>
            <dt>Currency</dt><dd>{{ inv.currency }}</dd>
          </dl>
        </div>
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Description</th><th class="num">Qty</th><th class="num">Unit price</th><th class="num">Line total</th></tr></thead>
            <tbody>
              @for (item of inv.items; track item.id) {
                <tr>
                  <td>{{ item.description }}</td>
                  <td class="num">{{ item.quantity }}</td>
                  <td class="num">{{ item.unitPrice | money: inv.currency }}</td>
                  <td class="num">{{ item.lineTotal | money: inv.currency }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <div class="totals totals-inline">
          <div class="total-row"><span>Subtotal</span><span>{{ inv.subtotal | money: inv.currency }}</span></div>
          @if (inv.discountAmount > 0) {
            <div class="total-row"><span>Discount</span><span>−{{ inv.discountAmount | money: inv.currency }}</span></div>
          }
          <div class="total-row"><span>Tax ({{ inv.taxPercent }}%)</span><span>{{ inv.taxAmount | money: inv.currency }}</span></div>
          <div class="total-row grand"><span>Total</span><span>{{ inv.totalAmount | money: inv.currency }}</span></div>
        </div>
        @if (inv.notes) { <p class="notes"><strong>Notes:</strong> {{ inv.notes }}</p> }
      </section>

      <section class="card">
        <div class="card-head"><h2>Payments</h2></div>
        <app-state [loading]="loadingPayments()" [empty]="!loadingPayments() && payments().length === 0"
          emptyText="No payments recorded for this invoice." />
        @if (payments().length > 0) {
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Date</th><th>Method</th><th>Reference</th><th>Notes</th><th class="num">Amount</th></tr></thead>
              <tbody>
                @for (p of payments(); track p.id) {
                  <tr>
                    <td>{{ p.paymentDate | date: 'mediumDate' }}</td>
                    <td>{{ p.method | label }}</td>
                    <td>{{ p.reference || '—' }}</td>
                    <td>{{ p.notes || '—' }}</td>
                    <td class="num text-good">{{ p.amount | money: inv.currency }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>

      <app-modal [open]="payOpen()" title="Record payment" (closed)="payOpen.set(false)">
        @if (payOpen()) {
          <app-record-payment [fixedInvoice]="inv" (saved)="onPaid($event)" (cancelled)="payOpen.set(false)" />
        }
      </app-modal>

      <app-modal [open]="confirmCancel()" title="Cancel invoice" (closed)="confirmCancel.set(false)">
        <p>Cancel invoice <strong>{{ inv.invoiceNumber }}</strong>? Cancelled invoices are excluded from receivables and forecasts. This cannot be undone.</p>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" (click)="confirmCancel.set(false)">Keep invoice</button>
          <button type="button" class="btn btn-danger" [disabled]="busy()" (click)="cancel()">Cancel invoice</button>
        </div>
      </app-modal>
    }
  `,
})
export class InvoiceDetailPage implements OnInit {
  private readonly api = inject(InvoiceService);
  private readonly toast = inject(ToastService);

  readonly id = input.required<string>();

  protected readonly invoice = signal<Invoice | null>(null);
  protected readonly payments = signal<Payment[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadingPayments = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly payOpen = signal(false);
  protected readonly confirmCancel = signal(false);

  // Mirrors backend rules: payments only on SENT/PARTIALLY_PAID/OVERDUE; cancel only without payments.
  protected readonly canPay = computed(() => {
    const s = this.invoice()?.status;
    return s === 'SENT' || s === 'PARTIALLY_PAID' || s === 'OVERDUE';
  });
  protected readonly canCancel = computed(() => {
    const inv = this.invoice();
    return !!inv && inv.status !== 'PAID' && inv.status !== 'CANCELLED' && Number(inv.paidAmount) === 0;
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.get(this.id()).subscribe({
      next: (inv) => { this.invoice.set(inv); this.loading.set(false); this.loadPayments(); },
      error: (e) => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }

  loadPayments(): void {
    this.loadingPayments.set(true);
    this.api.payments(this.id()).subscribe({
      next: (p) => {
        this.payments.set([...p].sort((a, b) => b.paymentDate.localeCompare(a.paymentDate)));
        this.loadingPayments.set(false);
      },
      error: () => this.loadingPayments.set(false),
    });
  }

  send(): void {
    this.busy.set(true);
    this.api.send(this.id()).subscribe({
      next: (inv) => { this.invoice.set(inv); this.busy.set(false); this.toast.success(`Invoice ${inv.invoiceNumber} marked as sent`); },
      error: (e) => { this.busy.set(false); this.toast.error(errorMessage(e)); },
    });
  }

  cancel(): void {
    this.busy.set(true);
    this.api.cancel(this.id()).subscribe({
      next: (inv) => {
        this.invoice.set(inv);
        this.busy.set(false);
        this.confirmCancel.set(false);
        this.toast.success(`Invoice ${inv.invoiceNumber} cancelled`);
      },
      error: (e) => { this.busy.set(false); this.toast.error(errorMessage(e)); },
    });
  }

  onPaid(p: Payment): void {
    this.payOpen.set(false);
    this.toast.success(`Payment of ${p.amount} recorded`);
    this.load();
  }

  print(): void {
    window.print();
  }
}
