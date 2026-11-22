import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { errorMessage } from '../../core/http.util';
import { Insight, InsightSeverity } from '../../core/models';
import { InsightsService } from '../../core/services/cashflow.service';
import { MoneyPipe } from '../../shared/money.pipe';
import { PageHeader, StateBlock, StatusBadge } from '../../shared/ui';

/** Deterministic, rule-based insights computed by the backend from this business's data. */
@Component({
  selector: 'app-insights',
  imports: [DatePipe, MoneyPipe, PageHeader, StateBlock, StatusBadge],
  template: `
    <app-page-header title="Financial insights" subtitle="Rule-based alerts from your invoices, payments, expenses and forecast.">
      <button type="button" class="btn btn-secondary" [disabled]="loading()" (click)="load()">Refresh</button>
    </app-page-header>

    @if (!loading() && !error() && insights().length > 0) {
      <div class="chip-row">
        <button type="button" class="chip chip-btn" [class.active]="filter() === null" (click)="filter.set(null)">All ({{ insights().length }})</button>
        @for (s of severities; track s) {
          @if (count(s) > 0) {
            <button type="button" class="chip chip-btn" [class]="'chip chip-btn sev-' + s.toLowerCase()" [class.active]="filter() === s" (click)="filter.set(s)">
              {{ s === 'CRITICAL' ? 'Critical' : s === 'WARNING' ? 'Warnings' : 'Info' }} ({{ count(s) }})
            </button>
          }
        }
      </div>
    }

    <app-state [loading]="loading()" [error]="error()" (retry)="load()"
      [empty]="!loading() && !error() && insights().length === 0"
      emptyText="No alerts right now. Insights appear as you add invoices, payments and expenses." />

    <div class="insight-list">
      @for (i of visible(); track i.code + i.title) {
        <article class="card insight" [class]="'card insight sev-' + i.severity.toLowerCase()">
          <header class="insight-head">
            <app-badge [status]="i.severity" />
            <h2>{{ i.title }}</h2>
          </header>
          <p>{{ i.description }}</p>
          <div class="insight-meta">
            @if (i.amount !== null) { <span><span class="muted">Amount</span> <strong>{{ i.amount | money }}</strong></span> }
            @if (i.relevantDate) { <span><span class="muted">Date</span> <strong>{{ i.relevantDate | date: 'mediumDate' }}</strong></span> }
          </div>
          <p class="insight-action"><strong>Suggested action:</strong> {{ i.suggestedAction }}</p>
        </article>
      }
    </div>
  `,
})
export class InsightsPage implements OnInit {
  private readonly api = inject(InsightsService);

  protected readonly severities: InsightSeverity[] = ['CRITICAL', 'WARNING', 'INFO'];
  protected readonly insights = signal<Insight[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly filter = signal<InsightSeverity | null>(null);
  protected readonly visible = computed(() => {
    const f = this.filter();
    return f ? this.insights().filter((i) => i.severity === f) : this.insights();
  });

  ngOnInit(): void {
    this.load();
  }

  count(s: InsightSeverity): number {
    return this.insights().filter((i) => i.severity === s).length;
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list().subscribe({
      next: (i) => { this.insights.set(i); this.loading.set(false); },
      error: (e) => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }
}
