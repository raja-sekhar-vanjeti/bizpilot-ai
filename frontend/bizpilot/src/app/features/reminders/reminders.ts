import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/http.util';
import { EmailDelivery, EmailPreview, Reminder, ReminderStatus } from '../../core/models';
import { ReminderService } from '../../core/services/reminder.service';
import { ToastService } from '../../core/toast.service';
import { PageHeader, StateBlock, StatusBadge } from '../../shared/ui';

/**
 * Payment reminders. The backend drafts messages from fixed templates for overdue invoices and
 * invoices due within 7 days. Nothing is emailed or messaged — the user copies the draft.
 */
@Component({
  selector: 'app-reminders',
  imports: [RouterLink, DatePipe, PageHeader, StateBlock, StatusBadge],
  template: `
    <app-page-header title="Reminders" subtitle="Draft payment reminders for overdue and upcoming invoices.">
      <button type="button" class="btn btn-primary" [disabled]="generating()" (click)="generate()">
        {{ generating() ? 'Generating…' : 'Generate reminders' }}
      </button>
    </app-page-header>

    <div class="alert alert-info">
      Review each reminder, then send it by email. Emails are sent only when you click Send email.
    </div>

    <div class="tabs" role="tablist">
      @for (s of statuses; track s) {
        <button type="button" role="tab" class="tab" [class.active]="status() === s" [attr.aria-selected]="status() === s" (click)="setStatus(s)">
          {{ tabLabel(s) }}
          @if (status() === s && !loading()) { <span class="tab-count">{{ reminders().length }}</span> }
        </button>
      }
    </div>

    @if (status() === 'OPEN' && !loading() && reminders().length > 0) {
      <div class="chip-row">
        <span class="chip chip-bad">{{ overdueCount() }} overdue</span>
        <span class="chip chip-warn">{{ reminders().length - overdueCount() }} upcoming</span>
      </div>
    }

    <app-state [loading]="loading()" [error]="error()" (retry)="load()"
      [empty]="!loading() && !error() && reminders().length === 0"
      [emptyText]="status() === 'OPEN' ? 'No open reminders. Click “Generate reminders” to check overdue and upcoming invoices.' : 'Nothing here.'" />

    <div class="reminder-list">
      @for (r of reminders(); track r.id) {
        <article class="card reminder" [class.reminder-overdue]="r.reminderType === 'OVERDUE'">
          <header class="reminder-head">
            <div class="row-actions">
              <app-badge [status]="r.reminderType" />
              @if (r.status !== 'OPEN') { <app-badge [status]="r.status" /> }
            </div>
            <span class="muted small">Created {{ r.reminderDate | date: 'mediumDate' }}</span>
          </header>
          <p class="reminder-message">{{ r.message }}</p>
          @if (sentInfo()[r.id]; as sent) { <p class="muted small">Email sent to {{ sent.recipientEmail }} on {{ sent.sentAt | date: 'medium' }}</p> }
          <footer class="row-actions wrap">
            <button type="button" class="btn btn-secondary btn-sm" (click)="copy(r)">Copy message</button>
            <a class="btn btn-secondary btn-sm" [routerLink]="['/invoices', r.invoiceId]">View invoice</a>
            @if (r.status === 'OPEN') {
              <span class="spacer"></span>
              <button type="button" class="btn btn-secondary btn-sm" (click)="openPreview(r)">Preview email</button>
              <button type="button" class="btn btn-primary btn-sm" [disabled]="sendingId() === r.id" (click)="confirmSend(r)">{{ sendingId() === r.id ? 'Sending…' : 'Send email' }}</button>
              <button type="button" class="btn btn-primary btn-sm" [disabled]="busyId() === r.id" (click)="update(r, 'COMPLETED')">Mark sent</button>
              <button type="button" class="btn btn-secondary btn-sm" [disabled]="busyId() === r.id" (click)="update(r, 'DISMISSED')">Dismiss</button>
            }
          </footer>
        </article>
      }
    </div>

    @if (preview(); as p) {
      <section class="card preview">
        <h3>Email preview</h3>
        <p class="muted small">To: {{ p.to }}</p>
        <p class="muted small">Subject: {{ p.subject }}</p>
        <iframe sandbox="" title="Email preview" [srcdoc]="p.htmlBody" style="width:100%;height:420px;border:1px solid var(--border);border-radius:8px"></iframe>
        <div class="row-actions"><button type="button" class="btn btn-secondary btn-sm" (click)="closePreview()">Close</button></div>
      </section>
    }
  `,
})
export class RemindersPage implements OnInit {
  private readonly api = inject(ReminderService);
  private readonly toast = inject(ToastService);

  protected readonly statuses: ReminderStatus[] = ['OPEN', 'COMPLETED', 'DISMISSED'];
  protected readonly status = signal<ReminderStatus>('OPEN');
  protected readonly reminders = signal<Reminder[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly generating = signal(false);
  protected readonly busyId = signal<string | null>(null);
  protected readonly overdueCount = computed(() => this.reminders().filter((r) => r.reminderType === 'OVERDUE').length);

  ngOnInit(): void {
    this.load();
  }

  tabLabel(s: ReminderStatus): string {
    return s === 'OPEN' ? 'Open' : s === 'COMPLETED' ? 'Completed' : 'Dismissed';
  }

  setStatus(s: ReminderStatus): void {
    this.status.set(s);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list(this.status()).subscribe({
      next: (r) => {
        // Overdue first, then by date.
        const sorted = [...r].sort((a, b) =>
          a.reminderType === b.reminderType ? a.reminderDate.localeCompare(b.reminderDate) : a.reminderType === 'OVERDUE' ? -1 : 1,
        );
        this.reminders.set(sorted);
        this.loading.set(false);
      },
      error: (e) => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }

  generate(): void {
    this.generating.set(true);
    this.api.generate().subscribe({
      next: (created) => {
        this.generating.set(false);
        this.toast[created.length ? 'success' : 'info'](
          created.length ? `${created.length} new reminder(s) drafted` : 'No new reminders — everything is already covered.',
        );
        this.status.set('OPEN');
        this.load();
      },
      error: (e) => { this.generating.set(false); this.toast.error(errorMessage(e)); },
    });
  }

  update(r: Reminder, status: 'COMPLETED' | 'DISMISSED'): void {
    this.busyId.set(r.id);
    this.api.updateStatus(r.id, status).subscribe({
      next: () => {
        this.busyId.set(null);
        this.reminders.update((list) => list.filter((x) => x.id !== r.id));
        this.toast.success(status === 'COMPLETED' ? 'Reminder marked as sent' : 'Reminder dismissed');
      },
      error: (e) => { this.busyId.set(null); this.toast.error(errorMessage(e)); },
    });
  }

  copy(r: Reminder): void {
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(r.message).then(
        () => this.toast.success('Message copied to clipboard'),
        () => this.toast.error('Could not copy. Select the text and copy it manually.'),
      );
    } else {
      this.toast.error('Clipboard is not available in this browser.');
    }
  }

  protected readonly preview = signal<EmailPreview | null>(null);
  protected readonly sendingId = signal<string | null>(null);
  protected readonly sentInfo = signal<Record<string, EmailDelivery>>({});

  openPreview(r: Reminder): void {
    this.api.previewEmail(r.id).subscribe({
      next: (p) => this.preview.set(p),
      error: (e) => this.toast.error(errorMessage(e)),
    });
    this.api.deliveries(r.id).subscribe({
      next: (list) => {
        const sent = list.find((d) => d.status === 'SENT');
        if (sent) this.sentInfo.update((m) => ({ ...m, [r.id]: sent }));
      },
      error: () => {},
    });
  }

  closePreview(): void {
    this.preview.set(null);
  }

  confirmSend(r: Reminder): void {
    if (!window.confirm('Send this payment reminder by email now? A reminder can only be emailed once.')) return;
    this.sendingId.set(r.id);
    this.api.sendEmail(r.id).subscribe({
      next: (d) => {
        this.sendingId.set(null);
        if (d.status === 'SENT') {
          this.toast.success('Reminder email sent');
          this.sentInfo.update((m) => ({ ...m, [r.id]: d }));
          this.preview.set(null);
        } else {
          this.toast.error(d.failureReason ?? 'Email could not be sent. Try again later.');
        }
      },
      error: (e) => {
        this.sendingId.set(null);
        this.toast.error(errorMessage(e));
      },
    });
  }

}
