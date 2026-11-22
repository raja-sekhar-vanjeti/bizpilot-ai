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
})
export class ToastContainer {
  protected readonly toast = inject(ToastService);
}
