import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { blankToNull, errorMessage } from '../../core/http.util';
import { Customer, CustomerRequest, PageResult } from '../../core/models';
import { CustomerService } from '../../core/services/customer.service';
import { ToastService } from '../../core/toast.service';
import { fieldError } from '../../shared/form-errors';
import { Modal, PageHeader, Pagination, StateBlock } from '../../shared/ui';

@Component({
  selector: 'app-customers',
  imports: [ReactiveFormsModule, RouterLink, PageHeader, StateBlock, Pagination, Modal],
  template: `
    <app-page-header title="Customers" subtitle="People and companies you invoice.">
      <button type="button" class="btn btn-primary" (click)="openCreate()">+ Add customer</button>
    </app-page-header>

    <div class="card">
      <div class="toolbar">
        <input class="input search" type="search" placeholder="Search by name or email…" [formControl]="search" aria-label="Search customers" />
      </div>

      <app-state [loading]="loading()" [error]="error()" (retry)="load()"
        [empty]="!loading() && !error() && result().items.length === 0"
        [emptyText]="search.value ? 'No customers match your search.' : 'No customers yet. Add your first customer to start invoicing.'" />

      @if (!loading() && result().items.length > 0) {
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr><th>Name</th><th>Company</th><th>Email</th><th>Phone</th><th class="actions-col">Actions</th></tr>
            </thead>
            <tbody>
              @for (c of result().items; track c.id) {
                <tr>
                  <td><strong>{{ c.name }}</strong></td>
                  <td>{{ c.companyName || '—' }}</td>
                  <td>{{ c.email || '—' }}</td>
                  <td>{{ c.phone || '—' }}</td>
                  <td>
                    <div class="row-actions">
                      <a class="btn btn-secondary btn-sm" [routerLink]="['/invoices']" [queryParams]="{ customerId: c.id }">Invoices</a>
                      <button type="button" class="btn btn-secondary btn-sm" (click)="openEdit(c)">Edit</button>
                      <button type="button" class="btn btn-ghost-danger btn-sm" (click)="toArchive.set(c)">Archive</button>
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

    <app-modal [open]="formOpen()" [title]="editing() ? 'Edit customer' : 'Add customer'" (closed)="closeForm()">
      <form [formGroup]="form" (ngSubmit)="save()" novalidate>
        <div class="form-grid">
          <div class="field">
            <label for="c-name">Name</label>
            <input id="c-name" class="input" formControlName="name" />
            @if (err(form.controls.name, 'Name'); as e) { <span class="form-error">{{ e }}</span> }
          </div>
          <div class="field">
            <label for="c-company">Company <span class="optional">(optional)</span></label>
            <input id="c-company" class="input" formControlName="companyName" />
            @if (err(form.controls.companyName, 'Company'); as e) { <span class="form-error">{{ e }}</span> }
          </div>
          <div class="field">
            <label for="c-email">Email <span class="optional">(optional)</span></label>
            <input id="c-email" class="input" type="email" formControlName="email" />
            @if (err(form.controls.email, 'Email'); as e) { <span class="form-error">{{ e }}</span> }
          </div>
          <div class="field">
            <label for="c-phone">Phone <span class="optional">(optional)</span></label>
            <input id="c-phone" class="input" type="tel" formControlName="phone" />
            @if (err(form.controls.phone, 'Phone'); as e) { <span class="form-error">{{ e }}</span> }
          </div>
        </div>
        <div class="field">
          <label for="c-address">Billing address <span class="optional">(optional)</span></label>
          <textarea id="c-address" class="textarea" rows="2" formControlName="billingAddress"></textarea>
          @if (err(form.controls.billingAddress, 'Address'); as e) { <span class="form-error">{{ e }}</span> }
        </div>
        <div class="field">
          <label for="c-notes">Notes <span class="optional">(optional)</span></label>
          <textarea id="c-notes" class="textarea" rows="2" formControlName="notes"></textarea>
          @if (err(form.controls.notes, 'Notes'); as e) { <span class="form-error">{{ e }}</span> }
        </div>
        @if (formError()) { <div class="alert alert-error" role="alert">{{ formError() }}</div> }
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" (click)="closeForm()">Cancel</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ saving() ? 'Saving…' : 'Save customer' }}</button>
        </div>
      </form>
    </app-modal>

    <app-modal [open]="!!toArchive()" title="Archive customer" (closed)="toArchive.set(null)">
      <p>Archive <strong>{{ toArchive()?.name }}</strong>? They will be hidden from lists, but their invoices and payments are kept.</p>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" (click)="toArchive.set(null)">Cancel</button>
        <button type="button" class="btn btn-danger" [disabled]="saving()" (click)="archive()">{{ saving() ? 'Archiving…' : 'Archive' }}</button>
      </div>
    </app-modal>
  `,
})
export class CustomersPage implements OnInit {
  private readonly api = inject(CustomerService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly err = fieldError;
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly result = signal<PageResult<Customer>>({ items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  private page = 0;

  protected readonly formOpen = signal(false);
  protected readonly editing = signal<Customer | null>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly toArchive = signal<Customer | null>(null);

  // Limits match the backend CustomerRequest.
  protected readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(160)]],
    companyName: ['', [Validators.maxLength(160)]],
    email: ['', [Validators.email, Validators.maxLength(255)]],
    phone: ['', [Validators.maxLength(20)]],
    billingAddress: ['', [Validators.maxLength(500)]],
    notes: ['', [Validators.maxLength(1000)]],
  });

  ngOnInit(): void {
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.page = 0;
        this.load();
      });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list({ search: this.search.value.trim(), page: this.page, size: 10, sort: 'name' }).subscribe({
      next: (r) => { this.result.set(r); this.loading.set(false); },
      error: (e) => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }

  goTo(page: number): void {
    this.page = page;
    this.load();
  }

  openCreate(): void {
    this.editing.set(null);
    this.form.reset();
    this.formError.set(null);
    this.formOpen.set(true);
  }

  openEdit(c: Customer): void {
    this.editing.set(c);
    this.form.reset({
      name: c.name,
      companyName: c.companyName ?? '',
      email: c.email ?? '',
      phone: c.phone ?? '',
      billingAddress: c.billingAddress ?? '',
      notes: c.notes ?? '',
    });
    this.formError.set(null);
    this.formOpen.set(true);
  }

  closeForm(): void {
    this.formOpen.set(false);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const request = blankToNull(this.form.getRawValue()) as CustomerRequest;
    const editing = this.editing();
    this.saving.set(true);
    this.formError.set(null);
    const call = editing ? this.api.update(editing.id, request) : this.api.create(request);
    call.subscribe({
      next: (c) => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.toast.success(editing ? `Customer "${c.name}" updated` : `Customer "${c.name}" added`);
        this.load();
      },
      error: (e) => { this.saving.set(false); this.formError.set(errorMessage(e)); },
    });
  }

  archive(): void {
    const c = this.toArchive();
    if (!c) return;
    this.saving.set(true);
    this.api.archive(c.id).subscribe({
      next: () => {
        this.saving.set(false);
        this.toArchive.set(null);
        this.toast.success(`Customer "${c.name}" archived`);
        if (this.result().items.length === 1 && this.page > 0) this.page--;
        this.load();
      },
      error: (e) => { this.saving.set(false); this.toast.error(errorMessage(e)); },
    });
  }
}
