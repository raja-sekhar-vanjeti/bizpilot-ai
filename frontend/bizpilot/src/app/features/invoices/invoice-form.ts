import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormArray, FormGroup, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable, switchMap } from 'rxjs';
import { errorMessage, today } from '../../core/http.util';
import { Customer, Invoice, InvoiceRequest } from '../../core/models';
import { CustomerService } from '../../core/services/customer.service';
import { InvoiceService } from '../../core/services/invoice.service';
import { ToastService } from '../../core/toast.service';
import { dateOrderValidator, fieldError } from '../../shared/form-errors';
import { MoneyPipe } from '../../shared/money.pipe';
import { PageHeader, StateBlock } from '../../shared/ui';

/** HALF_UP rounding to 2 decimals, matching the backend BigDecimal calculations. */
function round2(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

@Component({
  selector: 'app-invoice-form',
  imports: [ReactiveFormsModule, RouterLink, MoneyPipe, PageHeader, StateBlock],
  template: `
    <div class="hero-strip">
      <app-page-header [title]="id() ? 'Edit invoice' : 'New invoice'"
        [subtitle]="id() ? 'Only draft invoices can be edited.' : 'Invoices start as drafts. Send them when ready.'">
        <a [routerLink]="id() ? ['/invoices', id()] : ['/invoices']" class="btn btn-secondary">Back</a>
      </app-page-header>
    </div>

    <app-state [loading]="loading()" [error]="loadError()" (retry)="load()" />

    @if (!loading() && !loadError()) {
      @if (locked()) {
        <div class="alert alert-warn">This invoice is {{ locked() }} and can no longer be edited.</div>
      } @else {
        <form [formGroup]="form" (ngSubmit)="save(false)" novalidate class="invoice-form">
          <section class="card tone-indigo accent-indigo">
            <h2>Details</h2>
            <div class="form-grid three">
              <div class="field span-2">
                <label for="i-customer">Customer</label>
                <select id="i-customer" class="select" formControlName="customerId">
                  <option value="">Select a customer…</option>
                  @for (c of customers(); track c.id) {
                    <option [value]="c.id">{{ c.name }}{{ c.companyName ? ' · ' + c.companyName : '' }}</option>
                  }
                </select>
                @if (err(form.controls.customerId, 'Customer'); as e) { <span class="form-error">{{ e }}</span> }
                @if (customers().length === 0) {
                  <span class="muted small">No customers yet. <a routerLink="/customers">Add a customer</a> first.</span>
                }
              </div>
              <div class="field">
                <label for="i-currency">Currency</label>
                <input id="i-currency" class="input" formControlName="currency" maxlength="3" />
                @if (err(form.controls.currency, 'Currency'); as e) { <span class="form-error">{{ e }}</span> }
              </div>
              <div class="field">
                <label for="i-date">Invoice date</label>
                <input id="i-date" class="input" type="date" formControlName="invoiceDate" />
                @if (err(form.controls.invoiceDate, 'Invoice date'); as e) { <span class="form-error">{{ e }}</span> }
              </div>
              <div class="field">
                <label for="i-due">Due date</label>
                <input id="i-due" class="input" type="date" formControlName="dueDate" />
                @if (err(form.controls.dueDate, 'Due date'); as e) { <span class="form-error">{{ e }}</span> }
              </div>
              <div class="field">
                <label for="i-tax">Tax %</label>
                <input id="i-tax" class="input" type="number" step="0.01" min="0" max="100" formControlName="taxPercent" />
                @if (err(form.controls.taxPercent, 'Tax'); as e) { <span class="form-error">{{ e }}</span> }
              </div>
            </div>
          </section>

          <section class="card tone-violet accent-violet">
            <div class="card-head">
              <h2>Line items</h2>
              <button type="button" class="btn btn-secondary btn-sm" (click)="addItem()">+ Add item</button>
            </div>
            <div class="items" formArrayName="items">
              <div class="item-row item-head">
                <span>Description</span><span>Qty</span><span>Unit price</span><span class="num">Line total</span><span></span>
              </div>
              @for (item of items.controls; track item; let i = $index) {
                <div class="item-row" [formGroupName]="i">
                  <div>
                    <input class="input" formControlName="description" placeholder="Service or product" [attr.aria-label]="'Item ' + (i + 1) + ' description'" />
                    @if (err(item.get('description'), 'Description'); as e) { <span class="form-error">{{ e }}</span> }
                  </div>
                  <div>
                    <input class="input" type="number" step="0.01" min="0.01" formControlName="quantity" [attr.aria-label]="'Item ' + (i + 1) + ' quantity'" />
                    @if (err(item.get('quantity'), 'Quantity'); as e) { <span class="form-error">{{ e }}</span> }
                  </div>
                  <div>
                    <input class="input" type="number" step="0.01" min="0" formControlName="unitPrice" [attr.aria-label]="'Item ' + (i + 1) + ' unit price'" />
                    @if (err(item.get('unitPrice'), 'Unit price'); as e) { <span class="form-error">{{ e }}</span> }
                  </div>
                  <span class="num line-total">{{ lineTotal(i) | money: currency() }}</span>
                  <button type="button" class="icon-btn" [disabled]="items.length === 1" (click)="removeItem(i)" [attr.aria-label]="'Remove item ' + (i + 1)">✕</button>
                </div>
              }
            </div>
          </section>

          <div class="grid two-col">
            <section class="card tone-amber accent-amber">
              <h2>Notes</h2>
              <textarea class="textarea" rows="4" formControlName="notes" placeholder="Payment terms, bank details…" aria-label="Notes"></textarea>
              @if (err(form.controls.notes, 'Notes'); as e) { <span class="form-error">{{ e }}</span> }
            </section>
            <section class="card totals totals-grad">
              <h2>Summary</h2>
              <div class="total-row"><span>Subtotal</span><span>{{ totals().subtotal | money: currency() }}</span></div>
              <div class="total-row">
                <label for="i-discount">Discount</label>
                <input id="i-discount" class="input input-sm num" type="number" step="0.01" min="0" formControlName="discountAmount" />
              </div>
              @if (err(form.controls.discountAmount, 'Discount'); as e) { <span class="form-error">{{ e }}</span> }
              @if (totals().discountTooHigh) { <span class="form-error">Discount cannot exceed the subtotal</span> }
              <div class="total-row"><span>Tax ({{ form.controls.taxPercent.value || 0 }}%)</span><span>{{ totals().tax | money: currency() }}</span></div>
              <div class="total-row grand"><span>Total</span><span>{{ totals().total | money: currency() }}</span></div>
              <p class="muted small">Final amounts are calculated by the server.</p>
            </section>
          </div>

          @if (saveError()) { <div class="alert alert-error" role="alert">{{ saveError() }}</div> }

          <div class="form-actions">
            <a [routerLink]="id() ? ['/invoices', id()] : ['/invoices']" class="btn btn-secondary">Cancel</a>
            <button type="submit" class="btn btn-secondary" [disabled]="saving()">{{ saving() ? 'Saving…' : 'Save draft' }}</button>
            <button type="button" class="btn btn-primary btn-grad" [disabled]="saving()" (click)="save(true)">Save &amp; send</button>
          </div>
        </form>
      }
    }
  `,
  styles: [`
    .hero-strip { background: var(--grad-primary); color: #fff; border-radius: 12px; padding: 16px 20px; margin-bottom: 16px; box-shadow: 0 4px 14px rgba(79, 70, 229, 0.25); }
    .btn-grad { background: var(--grad-primary); color: #fff; border: 0; }
    .btn-grad:hover:not(:disabled) { filter: brightness(1.08); }
    .accent-indigo { border-top: 3px solid #3f6b57; }
    .accent-violet { border-top: 3px solid #7fa08a; }
    .accent-amber { border-top: 3px solid #c9965a; }
    .totals-grad { background: var(--grad-success); color: #fff; border: 0; box-shadow: 0 6px 18px rgba(16, 185, 129, 0.3); }
    .totals-grad h2, .totals-grad .total-row, .totals-grad .total-row label { color: #fff; -webkit-text-fill-color: #fff; background: none; }
    .totals-grad .total-row.grand { border-top-color: rgba(255, 255, 255, 0.6); font-weight: 700; }
    .totals-grad .muted { color: rgba(255, 255, 255, 0.85); }
    .totals-grad .form-error { color: #fff; font-weight: 600; }
  `],
})
export class InvoiceFormPage implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly api = inject(InvoiceService);
  private readonly customerApi = inject(CustomerService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  /** Route parameter (edit mode). */
  readonly id = input<string>('');

  protected readonly err = fieldError;
  protected readonly customers = signal<Customer[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly locked = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);

  protected readonly form = this.fb.group(
    {
      customerId: ['', Validators.required],
      invoiceDate: [today(), Validators.required],
      dueDate: [today(15), Validators.required],
      taxPercent: this.fb.control<number | null>(18, [Validators.min(0), Validators.max(100)]),
      discountAmount: this.fb.control<number | null>(0, [Validators.min(0)]),
      currency: ['INR', [Validators.required, Validators.minLength(3), Validators.maxLength(3)]],
      notes: ['', Validators.maxLength(1000)],
      items: this.fb.array([this.newItem()]),
    },
    { validators: dateOrderValidator('invoiceDate', 'dueDate', 'Due date cannot be before the invoice date') },
  );

  get items(): FormArray<FormGroup> {
    return this.form.controls.items as unknown as FormArray<FormGroup>;
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.customerApi.all().subscribe({
      next: (customers) => {
        this.customers.set(customers);
        if (!this.id()) {
          this.loading.set(false);
          return;
        }
        this.api.get(this.id()).subscribe({
          next: (inv) => { this.fill(inv); this.loading.set(false); },
          error: (e) => { this.loadError.set(errorMessage(e)); this.loading.set(false); },
        });
      },
      error: (e) => { this.loadError.set(errorMessage(e)); this.loading.set(false); },
    });
  }

  addItem(): void {
    this.items.push(this.newItem());
  }

  removeItem(index: number): void {
    if (this.items.length > 1) this.items.removeAt(index);
  }

  protected currency(): string {
    return (this.form.controls.currency.value || 'INR').toUpperCase();
  }

  protected lineTotal(index: number): number {
    const v = this.items.at(index).getRawValue() as { quantity: number | null; unitPrice: number | null };
    return round2(round2(Number(v.quantity) || 0) * round2(Number(v.unitPrice) || 0));
  }

  protected totals(): { subtotal: number; tax: number; total: number; discountTooHigh: boolean } {
    let subtotal = 0;
    for (let i = 0; i < this.items.length; i++) subtotal += this.lineTotal(i);
    subtotal = round2(subtotal);
    const discount = round2(Number(this.form.controls.discountAmount.value) || 0);
    const base = subtotal - discount;
    const tax = round2((base * (Number(this.form.controls.taxPercent.value) || 0)) / 100);
    return { subtotal, tax, total: round2(base + tax), discountTooHigh: discount > subtotal };
  }

  save(andSend: boolean): void {
    if (this.form.invalid || this.totals().discountTooHigh) {
      this.form.markAllAsTouched();
      this.items.controls.forEach((c) => c.markAllAsTouched());
      return;
    }
    const v = this.form.getRawValue();
    const request: InvoiceRequest = {
      customerId: v.customerId,
      invoiceDate: v.invoiceDate,
      dueDate: v.dueDate,
      taxPercent: v.taxPercent ?? 0,
      discountAmount: v.discountAmount ?? 0,
      currency: v.currency.trim().toUpperCase(),
      notes: v.notes.trim() || null,
      items: (v.items as { description: string; quantity: number; unitPrice: number }[]).map((i) => ({
        description: i.description.trim(),
        quantity: Number(i.quantity),
        unitPrice: Number(i.unitPrice),
      })),
    };

    this.saving.set(true);
    this.saveError.set(null);
    let call: Observable<Invoice> = this.id() ? this.api.update(this.id(), request) : this.api.create(request);
    if (andSend) {
      call = call.pipe(switchMap((inv) => this.api.send(inv.id)));
    }
    call.subscribe({
      next: (inv) => {
        this.toast.success(andSend ? `Invoice ${inv.invoiceNumber} saved and sent` : `Invoice ${inv.invoiceNumber} saved as draft`);
        void this.router.navigate(['/invoices', inv.id]);
      },
      error: (e) => { this.saving.set(false); this.saveError.set(errorMessage(e)); },
    });
  }

  private newItem(description = '', quantity: number | null = 1, unitPrice: number | null = null) {
    return this.fb.group({
      description: [description, [Validators.required, Validators.maxLength(300)]],
      quantity: this.fb.control<number | null>(quantity, [Validators.required, Validators.min(0.01)]),
      unitPrice: this.fb.control<number | null>(unitPrice, [Validators.required, Validators.min(0)]),
    });
  }

  private fill(inv: Invoice): void {
    if (inv.status !== 'DRAFT') {
      this.locked.set(inv.status.toLowerCase().replace('_', ' '));
      return;
    }
    this.form.patchValue({
      customerId: inv.customerId,
      invoiceDate: inv.invoiceDate,
      dueDate: inv.dueDate,
      taxPercent: Number(inv.taxPercent),
      discountAmount: Number(inv.discountAmount),
      currency: inv.currency,
      notes: inv.notes ?? '',
    });
    this.items.clear();
    for (const item of inv.items) {
      this.items.push(this.newItem(item.description, Number(item.quantity), Number(item.unitPrice)));
    }
    if (this.items.length === 0) this.addItem();
  }
}
