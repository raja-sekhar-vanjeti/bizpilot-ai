import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { blankToNull, errorMessage, today } from '../../core/http.util';
import { Invoice, PAYMENT_METHODS, Payment, PaymentMethod } from '../../core/models';
import { PaymentService } from '../../core/services/payment.service';
import { fieldError } from '../../shared/form-errors';
import { LabelPipe, MoneyPipe } from '../../shared/money.pipe';

/**
 * Records a payment against an open invoice (SENT / PARTIALLY_PAID / OVERDUE).
 * The backend rejects overpayments; the form checks the outstanding amount first.
 */
@Component({
  selector: 'app-record-payment',
  imports: [ReactiveFormsModule, MoneyPipe, LabelPipe],
  template: `
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      @if (!fixedInvoice()) {
        <div class="field">
          <label for="p-invoice">Invoice</label>
          <select id="p-invoice" class="select" formControlName="invoiceId" (change)="onInvoiceChange()">
            <option value="">Select an open invoice…</option>
            @for (inv of invoices(); track inv.id) {
              <option [value]="inv.id">{{ inv.invoiceNumber }} · {{ inv.customerName }} · {{ inv.outstandingAmount | money: inv.currency }} due</option>
            }
          </select>
          @if (err(form.controls.invoiceId, 'Invoice'); as e) { <span class="form-error">{{ e }}</span> }
          @if (invoices().length === 0) {
            <span class="muted small">No open invoices. Send an invoice before recording payments.</span>
          }
        </div>
      }
      @if (selected(); as inv) {
        <div class="info-strip">
          <span>{{ inv.invoiceNumber }} · {{ inv.customerName }}</span>
          <span>Outstanding <strong>{{ inv.outstandingAmount | money: inv.currency }}</strong></span>
        </div>
      }
      <div class="form-grid">
        <div class="field">
          <label for="p-amount">Amount</label>
          <input id="p-amount" class="input" type="number" step="0.01" min="0.01" formControlName="amount" />
          @if (err(form.controls.amount, 'Amount'); as e) { <span class="form-error">{{ e }}</span> }
          @if (exceeds()) { <span class="form-error">Amount exceeds the outstanding balance</span> }
          @if (selected(); as inv) {
            <button type="button" class="link-btn small" (click)="payInFull()">Pay full {{ inv.outstandingAmount | money: inv.currency }}</button>
          }
        </div>
        <div class="field">
          <label for="p-date">Payment date</label>
          <input id="p-date" class="input" type="date" formControlName="paymentDate" />
          @if (err(form.controls.paymentDate, 'Payment date'); as e) { <span class="form-error">{{ e }}</span> }
        </div>
        <div class="field">
          <label for="p-method">Method</label>
          <select id="p-method" class="select" formControlName="method">
            @for (m of methods; track m) { <option [value]="m">{{ m | label }}</option> }
          </select>
        </div>
        <div class="field">
          <label for="p-ref">Reference <span class="optional">(optional)</span></label>
          <input id="p-ref" class="input" formControlName="reference" placeholder="UTR / cheque no." />
          @if (err(form.controls.reference, 'Reference'); as e) { <span class="form-error">{{ e }}</span> }
        </div>
      </div>
      <div class="field">
        <label for="p-notes">Notes <span class="optional">(optional)</span></label>
        <textarea id="p-notes" class="textarea" rows="2" formControlName="notes"></textarea>
        @if (err(form.controls.notes, 'Notes'); as e) { <span class="form-error">{{ e }}</span> }
      </div>
      @if (error()) { <div class="alert alert-error" role="alert">{{ error() }}</div> }
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" (click)="cancelled.emit()">Cancel</button>
        <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ saving() ? 'Recording…' : 'Record payment' }}</button>
      </div>
    </form>
  `,
})
export class RecordPayment implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly api = inject(PaymentService);

  /** When set, the payment is recorded for this invoice only. */
  readonly fixedInvoice = input<Invoice | null>(null);
  /** Open invoices to choose from when no invoice is fixed. */
  readonly invoices = input<Invoice[]>([]);
  readonly saved = output<Payment>();
  readonly cancelled = output<void>();

  protected readonly err = fieldError;
  protected readonly methods = PAYMENT_METHODS;
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly selectedId = signal<string>('');
  private readonly amountValue = signal<number | null>(null);

  protected readonly form = this.fb.group({
    invoiceId: ['', Validators.required],
    amount: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    paymentDate: [today(), Validators.required],
    method: this.fb.control<PaymentMethod>('UPI', Validators.required),
    reference: ['', Validators.maxLength(100)],
    notes: ['', Validators.maxLength(500)],
  });

  protected readonly selected = computed<Invoice | null>(() => {
    const fixed = this.fixedInvoice();
    if (fixed) return fixed;
    return this.invoices().find((i) => i.id === this.selectedId()) ?? null;
  });

  protected readonly exceeds = computed(() => {
    const inv = this.selected();
    const amount = this.amountValue();
    return !!inv && amount !== null && amount > Number(inv.outstandingAmount);
  });

  ngOnInit(): void {
    const fixed = this.fixedInvoice();
    if (fixed) {
      this.form.controls.invoiceId.setValue(fixed.id);
      this.selectedId.set(fixed.id);
    }
    this.form.controls.amount.valueChanges.subscribe((v) => this.amountValue.set(v === null ? null : Number(v)));
  }

  onInvoiceChange(): void {
    this.selectedId.set(this.form.controls.invoiceId.value);
  }

  payInFull(): void {
    const inv = this.selected();
    if (inv) this.form.controls.amount.setValue(Number(inv.outstandingAmount));
  }

  save(): void {
    if (this.form.invalid || this.exceeds()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = blankToNull(this.form.getRawValue());
    this.saving.set(true);
    this.error.set(null);
    this.api
      .record({
        invoiceId: v.invoiceId,
        amount: Number(v.amount),
        paymentDate: v.paymentDate,
        method: v.method,
        reference: v.reference,
        notes: v.notes,
      })
      .subscribe({
        next: (p) => { this.saving.set(false); this.saved.emit(p); },
        error: (e) => { this.saving.set(false); this.error.set(errorMessage(e)); },
      });
  }
}
