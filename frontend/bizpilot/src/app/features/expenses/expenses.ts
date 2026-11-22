import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { blankToNull, errorMessage, today } from '../../core/http.util';
import { EXPENSE_CATEGORIES, Expense, ExpenseCategory, PAYMENT_METHODS, PageResult, PaymentMethod } from '../../core/models';
import { ExpenseService } from '../../core/services/expense.service';
import { ToastService } from '../../core/toast.service';
import { fieldError } from '../../shared/form-errors';
import { LabelPipe, MoneyPipe } from '../../shared/money.pipe';
import { Modal, PageHeader, Pagination, StateBlock } from '../../shared/ui';

@Component({
  selector: 'app-expenses',
  imports: [ReactiveFormsModule, FormsModule, DatePipe, MoneyPipe, LabelPipe, PageHeader, StateBlock, Pagination, Modal],
  template: `
    <div aria-hidden="true" style="height:4px;border-radius:4px;background:var(--grad-warm);margin-bottom:14px"></div>
    <app-page-header title="Expenses" subtitle="Track what your business spends.">
      <button type="button" class="btn btn-primary" style="background:var(--grad-warm);border-color:transparent;color:#fff" (click)="openCreate()">+ Add expense</button>
    </app-page-header>

    <div class="card">
      <div class="toolbar">
        <div class="field inline">
          <label for="e-fcat">Category</label>
          <select id="e-fcat" class="select" [ngModel]="category()" (ngModelChange)="category.set($event); reload()">
            <option value="">All categories</option>
            @for (c of categories; track c) { <option [value]="c">{{ c | label }}</option> }
          </select>
        </div>
        <div class="field inline">
          <label for="e-ffrom">From</label>
          <input id="e-ffrom" class="input" type="date" [ngModel]="from()" (ngModelChange)="from.set($event); reload()" />
        </div>
        <div class="field inline">
          <label for="e-fto">To</label>
          <input id="e-fto" class="input" type="date" [ngModel]="to()" (ngModelChange)="to.set($event); reload()" />
        </div>
        @if (category() || from() || to()) {
          <button type="button" class="btn btn-secondary btn-sm" (click)="clearFilters()">Clear</button>
        }
        @if ((from() && !to()) || (!from() && to())) {
          <span class="muted small">Set both dates to filter by range.</span>
        }
      </div>

      <app-state [loading]="loading()" [error]="error()" (retry)="load()"
        [empty]="!loading() && !error() && result().items.length === 0"
        [emptyText]="category() || from() || to() ? 'No expenses match these filters.' : 'No expenses yet. Add rent, software, travel and other costs.'" />

      @if (!loading() && result().items.length > 0) {
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr style="background:var(--tint-rose)"><th>Date</th><th>Description</th><th>Category</th><th>Vendor</th><th>Method</th><th class="num">Amount</th><th class="actions-col">Actions</th></tr>
            </thead>
            <tbody>
              @for (e of result().items; track e.id) {
                <tr>
                  <td>{{ e.expenseDate | date: 'mediumDate' }}</td>
                  <td><strong>{{ e.description }}</strong>@if (e.notes) {<br /><span class="muted small">{{ e.notes }}</span>}</td>
                  <td><span class="chip" style="background:var(--tint-rose);color:#be123c">{{ e.category | label }}</span></td>
                  <td>{{ e.vendor || '—' }}</td>
                  <td>{{ e.paymentMethod | label }}</td>
                  <td class="num text-bad">{{ e.amount | money }}</td>
                  <td>
                    <div class="row-actions">
                      <button type="button" class="btn btn-secondary btn-sm" (click)="openEdit(e)">Edit</button>
                      <button type="button" class="btn btn-ghost-danger btn-sm" (click)="toDelete.set(e)">Delete</button>
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-pagination [page]="result().page" [totalPages]="result().totalPages" [totalElements]="result().totalElements" (pageChange)="goTo($event)" />
      }
    </div>

    <app-modal [open]="formOpen()" [title]="editing() ? 'Edit expense' : 'Add expense'" (closed)="formOpen.set(false)">
      <form [formGroup]="form" (ngSubmit)="save()" novalidate>
        <div class="field">
          <label for="e-desc">Description</label>
          <input id="e-desc" class="input" formControlName="description" />
          @if (err(form.controls.description, 'Description'); as e) { <span class="form-error">{{ e }}</span> }
        </div>
        <div class="form-grid">
          <div class="field">
            <label for="e-amount">Amount (₹)</label>
            <input id="e-amount" class="input" type="number" step="0.01" min="0.01" formControlName="amount" />
            @if (err(form.controls.amount, 'Amount'); as e) { <span class="form-error">{{ e }}</span> }
          </div>
          <div class="field">
            <label for="e-date">Date</label>
            <input id="e-date" class="input" type="date" formControlName="expenseDate" />
            @if (err(form.controls.expenseDate, 'Date'); as e) { <span class="form-error">{{ e }}</span> }
          </div>
          <div class="field">
            <label for="e-cat">Category</label>
            <select id="e-cat" class="select" formControlName="category">
              @for (c of categories; track c) { <option [value]="c">{{ c | label }}</option> }
            </select>
          </div>
          <div class="field">
            <label for="e-method">Payment method</label>
            <select id="e-method" class="select" formControlName="paymentMethod">
              @for (m of methods; track m) { <option [value]="m">{{ m | label }}</option> }
            </select>
          </div>
        </div>
        <div class="field">
          <label for="e-vendor">Vendor <span class="optional">(optional)</span></label>
          <input id="e-vendor" class="input" formControlName="vendor" />
          @if (err(form.controls.vendor, 'Vendor'); as e) { <span class="form-error">{{ e }}</span> }
        </div>
        <div class="field">
          <label for="e-notes">Notes <span class="optional">(optional)</span></label>
          <textarea id="e-notes" class="textarea" rows="2" formControlName="notes"></textarea>
          @if (err(form.controls.notes, 'Notes'); as e) { <span class="form-error">{{ e }}</span> }
        </div>
        @if (formError()) { <div class="alert alert-error" role="alert">{{ formError() }}</div> }
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" (click)="formOpen.set(false)">Cancel</button>
          <button type="submit" class="btn btn-primary" style="background:var(--grad-warm);border-color:transparent;color:#fff" [disabled]="saving()">{{ saving() ? 'Saving…' : 'Save expense' }}</button>
        </div>
      </form>
    </app-modal>

    <app-modal [open]="!!toDelete()" title="Delete expense" (closed)="toDelete.set(null)">
      <p>Delete <strong>{{ toDelete()?.description }}</strong> ({{ toDelete()?.amount | money }})? This cannot be undone.</p>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" (click)="toDelete.set(null)">Cancel</button>
        <button type="button" class="btn btn-danger" [disabled]="saving()" (click)="remove()">{{ saving() ? 'Deleting…' : 'Delete' }}</button>
      </div>
    </app-modal>
  `,
})
export class ExpensesPage implements OnInit {
  private readonly api = inject(ExpenseService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly err = fieldError;
  protected readonly categories = EXPENSE_CATEGORIES;
  protected readonly methods = PAYMENT_METHODS;

  protected readonly category = signal<ExpenseCategory | ''>('');
  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly result = signal<PageResult<Expense>>({ items: [], page: 0, size: 15, totalElements: 0, totalPages: 0 });
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  private page = 0;

  protected readonly formOpen = signal(false);
  protected readonly editing = signal<Expense | null>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly toDelete = signal<Expense | null>(null);

  // Limits match the backend ExpenseRequest.
  protected readonly form = this.fb.group({
    description: ['', [Validators.required, Validators.maxLength(300)]],
    amount: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    expenseDate: [today(), Validators.required],
    category: this.fb.control<ExpenseCategory>('OTHER', Validators.required),
    paymentMethod: this.fb.control<PaymentMethod>('UPI', Validators.required),
    vendor: ['', Validators.maxLength(160)],
    notes: ['', Validators.maxLength(500)],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const from = this.from();
    const to = this.to();
    if (from && to && to < from) {
      this.error.set('The end date cannot be before the start date.');
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const range = from && to;
    this.api
      .list({
        category: this.category() || null,
        from: range ? from : null,
        to: range ? to : null,
        page: this.page,
        size: 15,
        sort: 'expenseDate,desc',
      })
      .subscribe({
        next: (r) => { this.result.set(r); this.loading.set(false); },
        error: (e) => { this.error.set(errorMessage(e)); this.loading.set(false); },
      });
  }

  reload(): void {
    this.page = 0;
    this.load();
  }

  goTo(page: number): void {
    this.page = page;
    this.load();
  }

  clearFilters(): void {
    this.category.set('');
    this.from.set('');
    this.to.set('');
    this.reload();
  }

  openCreate(): void {
    this.editing.set(null);
    this.form.reset();
    this.formError.set(null);
    this.formOpen.set(true);
  }

  openEdit(e: Expense): void {
    this.editing.set(e);
    this.form.reset({
      description: e.description,
      amount: Number(e.amount),
      expenseDate: e.expenseDate,
      category: e.category,
      paymentMethod: e.paymentMethod,
      vendor: e.vendor ?? '',
      notes: e.notes ?? '',
    });
    this.formError.set(null);
    this.formOpen.set(true);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = blankToNull(this.form.getRawValue());
    const request = { ...v, amount: Number(v.amount) };
    const editing = this.editing();
    this.saving.set(true);
    this.formError.set(null);
    const call = editing ? this.api.update(editing.id, request) : this.api.create(request);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.toast.success(editing ? 'Expense updated' : 'Expense added');
        this.load();
      },
      error: (e) => { this.saving.set(false); this.formError.set(errorMessage(e)); },
    });
  }

  remove(): void {
    const e = this.toDelete();
    if (!e) return;
    this.saving.set(true);
    this.api.delete(e.id).subscribe({
      next: () => {
        this.saving.set(false);
        this.toDelete.set(null);
        this.toast.success('Expense deleted');
        if (this.result().items.length === 1 && this.page > 0) this.page--;
        this.load();
      },
      error: (err) => { this.saving.set(false); this.toast.error(errorMessage(err)); },
    });
  }
}
