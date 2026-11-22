import { Component, inject } from '@angular/core';
import { ToastService } from '../core/toast.service';

@Component({
  selector: 'app-toasts',
  template: `
    <div class="toasts" aria-live="polite">
      @for (t of toast.toasts(); track t.id) {
        <div class="toast" [class]="'toast toast-' + t.kind" role="status">
          <span>{{ t.text }}</span>
          <button type="button" class="icon-btn" aria-label="Dismiss" (click)="toast.dismiss(t.id)">✕</button>
        </div>
      }
    </div>
  `,
  styles: [`
    @keyframes bp-toast-in { from { opacity: 0; translate: 40px 0; } to { opacity: 1; translate: 0 0; } }
    .toast {
      display: flex; align-items: center; justify-content: space-between; gap: 12px;
      padding: 12px 14px; border-radius: 16px;
      border: 1px solid var(--border, #e2e8f0);
      background: color-mix(in srgb, var(--card, #ffffff) 82%, transparent);
      backdrop-filter: blur(14px); -webkit-backdrop-filter: blur(14px);
      box-shadow: 0 12px 30px -12px rgba(15, 23, 42, .3);
      animation: bp-toast-in .3s cubic-bezier(.2, .8, .2, 1) both;
    }
    .toast-success { border-left: 3px solid var(--green, #16a34a); }
    .toast-error { border-left: 3px solid var(--red, #dc2626); }
    .toast-warning { border-left: 3px solid var(--amber, #d97706); }
    .toast-info { border-left: 3px solid var(--blue, #2563eb); }
    @media (prefers-reduced-motion: reduce) {
      .toast { animation: none; }
    }
  `],
})
export class ToastContainer {
  protected readonly toast = inject(ToastService);
}
