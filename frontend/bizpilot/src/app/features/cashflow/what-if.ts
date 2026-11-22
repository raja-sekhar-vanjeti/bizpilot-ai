import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/http.util';
import { Customer, ExpectedInvoice, ForecastHorizon, WhatIfResponse } from '../../core/models';
import { CashflowService } from '../../core/services/cashflow.service';
import { CustomerService } from '../../core/services/customer.service';
import { BarChart, ChartGroup } from '../../shared/bar-chart';
import { fieldError } from '../../shared/form-errors';
import { MoneyPipe } from '../../shared/money.pipe';
import { PageHeader, StatCard, StateBlock } from '../../shared/ui';

type Scope = 'all' | 'customer' | 'invoices';

/** Read-only payment-delay simulation (POST /api/cashflow/what-if). Nothing is saved. */
@Component({
  selector: 'app-what-if',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, MoneyPipe, PageHeader, StatCard, StateBlock, BarChart],
  template: `
    <app-page-header title="What-if simulation" subtitle="See how late customer payments would change your cash position. Nothing is saved.">
      <a routerLink="/cashflow" class="btn btn-secondary">Back to forecast</a>
    </app-page-header>

    <section class="card">
      <form [formGroup]="form" (ngSubmit)="run()" novalidate>
        <div class="form-grid three">
          <div class="field">
            <label for="w-delay">Delay payments by (days)</label>
            <input id="w-delay" class="input" type="number" min="1" max="365" formControlName="delayDays" />
            @if (err(form.controls.delayDays, 'Delay'); as e) { <span class="form-error">{{ e }}</span> }
          </div>
          <div class="field">
            <label for="w-days">Forecast horizon</label>
            <select id="w-days" class="select" formControlName="days">
              <option [ngValue]="30">30 days</option>
              <option [ngValue]="60">60 days</option>
              <option [ngValue]="90">90 days</option>
            </select>
          </div>
          <div class="field">
            <label for="w-scope">Apply delay to</label>
            <select id="w-scope" class="select" formControlName="scope">
              <option value="all">All open invoices</option>
              <option value="customer">One customer's invoices</option>
              <option value="invoices">Selected invoices</option>
            </select>
          </div>
        </div>

        @if (form.controls.scope.value === 'customer') {
          <div class="field">
            <label for="w-customer">Customer</label>
            <select id="w-customer" class="select" formControlName="customerId">
              <option value="">Select a customer…</option>
              @for (c of customers(); track c.id) { <option [value]="c.id">{{ c.name }}</option> }
            </select>
            @if (scopeError()) { <span class="form-error">{{ scopeError() }}</span> }
          </div>
        }

        @if (form.controls.scope.value === 'invoices') {
          <fieldset class="field invoice-picker">
            <legend>Invoices to delay</legend>
            @if (openInvoices().length === 0) {
              <p class="muted small">No unpaid sent invoices to simulate.</p>
            }
            @for (i of openInvoices(); track i.invoiceId) {
              <label class="check">
                <input type="checkbox" [checked]="selected().has(i.invoiceId)" (change)="toggle(i.invoiceId)" />
                <span>{{ i.invoiceNumber }} · {{ i.customerName }} · {{ i.outstandingAmount | money }} · expected {{ i.expectedDate | date: 'mediumDate' }}</span>
              </label>
            }
            @if (scopeError()) { <span class="form-error">{{ scopeError() }}</span> }
          </fieldset>
        }

        @if (error()) { <div class="alert alert-error" role="alert">{{ error() }}</div> }
        <div class="form-actions">
          <button type="submit" class="btn btn-primary" [disabled]="running()">{{ running() ? 'Simulating…' : 'Run simulation' }}</button>
        </div>
      </form>
    </section>

    <app-state [loading]="running()" />

    @if (!running() && result(); as r) {
      <div class="grid stats-grid">
        <app-stat-card label="Closing balance (now)" [value]="r.baseline.projectedClosingBalance | money" />
        <app-stat-card label="Closing balance (with delay)" [value]="r.simulated.projectedClosingBalance | money"
          [tone]="r.closingBalanceImpact < 0 ? 'bad' : 'default'" [hint]="impactText(r.closingBalanceImpact)" />
        <app-stat-card label="Lowest balance (now)" [value]="r.baseline.lowestProjectedBalance | money"
          [hint]="r.baseline.lowestBalanceDate ? 'On ' + (r.baseline.lowestBalanceDate | date: 'mediumDate') : ''" />
        <app-stat-card label="Lowest balance (with delay)" [value]="r.simulated.lowestProjectedBalance | money"
          [tone]="r.simulated.lowestProjectedBalance < 0 ? 'bad' : r.lowestBalanceImpact < 0 ? 'warn' : 'default'"
          [hint]="impactText(r.lowestBalanceImpact) + (r.simulated.lowestBalanceDate ? ' · ' + (r.simulated.lowestBalanceDate | date: 'mediumDate') : '')" />
        <app-stat-card label="Pushed past the forecast period" [value]="r.inflowPushedBeyondHorizon | money"
          [tone]="r.inflowPushedBeyondHorizon > 0 ? 'warn' : 'default'" [hint]="r.affectedInvoices.length + ' invoice(s) affected'" />
      </div>

      @if (r.simulated.lowestProjectedBalance < 0 && r.baseline.lowestProjectedBalance >= 0) {
        <div class="alert alert-error">A {{ r.delayDays }}-day delay would push your cash balance below zero.</div>
      }

      <section class="card">
        <div class="card-head"><h2>Weekly closing balance: now vs with delay</h2></div>
        <app-bar-chart [groups]="compareGroups()" [series]="compareSeries" ariaLabel="Baseline versus simulated closing balance" />
      </section>

      <section class="card">
        <div class="card-head"><h2>Affected invoices</h2></div>
        <app-state [empty]="r.affectedInvoices.length === 0" emptyText="No open invoices matched — nothing would change." />
        @if (r.affectedInvoices.length > 0) {
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Invoice</th><th>Customer</th><th>Expected</th><th>Revised</th><th class="num">Outstanding</th><th></th></tr></thead>
              <tbody>
                @for (a of r.affectedInvoices; track a.invoiceId) {
                  <tr>
                    <td><a [routerLink]="['/invoices', a.invoiceId]">{{ a.invoiceNumber }}</a></td>
                    <td>{{ a.customerName }}</td>
                    <td>{{ a.originalExpectedDate | date: 'mediumDate' }}</td>
                    <td>{{ a.revisedExpectedDate | date: 'mediumDate' }}</td>
                    <td class="num">{{ a.outstandingAmount | money }}</td>
                    <td>@if (a.movedBeyondHorizon) { <span class="badge badge-partial">After period</span> }</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
    }
  `,
})
export class WhatIfPage implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly api = inject(CashflowService);
  private readonly customerApi = inject(CustomerService);

  protected readonly err = fieldError;
  protected readonly customers = signal<Customer[]>([]);
  protected readonly openInvoices = signal<ExpectedInvoice[]>([]);
  protected readonly selected = signal<Set<string>>(new Set());
  protected readonly result = signal<WhatIfResponse | null>(null);
  protected readonly running = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly scopeError = signal<string | null>(null);

  protected readonly form = this.fb.group({
    delayDays: this.fb.control<number | null>(15, [Validators.required, Validators.min(1), Validators.max(365)]),
    days: this.fb.control<ForecastHorizon>(30),
    scope: this.fb.control<Scope>('all'),
    customerId: [''],
  });

  protected readonly compareSeries = [
    { name: 'Now', color: '#2563eb' },
    { name: 'With delay', color: '#d97706' },
  ];
  protected readonly compareGroups = computed<ChartGroup[]>(() => {
    const r = this.result();
    if (!r) return [];
    return r.baselinePeriods.map((p, i) => ({
      label: new Date(p.periodStart + 'T00:00:00').toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }),
      values: [Number(p.closingBalance), Number(r.simulatedPeriods[i]?.closingBalance ?? 0)],
    }));
  });

  ngOnInit(): void {
    this.customerApi.all().subscribe({ next: (c) => this.customers.set(c), error: () => undefined });
    // Open invoices come from the 90-day forecast (it lists every unpaid sent invoice).
    this.api.forecast(90, 'WEEKLY').subscribe({ next: (f) => this.openInvoices.set(f.expectedInvoices), error: () => undefined });
  }

  toggle(id: string): void {
    this.selected.update((set) => {
      const next = new Set(set);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  impactText(value: number): string {
    if (value === 0) return 'No change';
    const fmt = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(Math.abs(value));
    return value < 0 ? `${fmt} lower` : `${fmt} higher`;
  }

  run(): void {
    this.scopeError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    if (v.scope === 'customer' && !v.customerId) {
      this.scopeError.set('Choose a customer');
      return;
    }
    if (v.scope === 'invoices' && this.selected().size === 0) {
      this.scopeError.set('Select at least one invoice');
      return;
    }
    this.running.set(true);
    this.error.set(null);
    this.api
      .whatIf({
        delayDays: Number(v.delayDays),
        days: v.days,
        customerId: v.scope === 'customer' ? v.customerId : null,
        invoiceIds: v.scope === 'invoices' ? [...this.selected()] : null,
      })
      .subscribe({
        next: (r) => { this.result.set(r); this.running.set(false); },
        error: (e) => { this.error.set(errorMessage(e)); this.running.set(false); },
      });
  }
}
