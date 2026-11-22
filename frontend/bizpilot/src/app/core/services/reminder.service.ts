import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_BASE_URL } from '../api.config';
import { ApiResponse, EmailDelivery, EmailPreview, Reminder, ReminderStatus } from '../models';

/** /api/reminders — Reminders are drafted by the backend; emails are sent only when the user explicitly requests it. */
@Injectable({ providedIn: 'root' })
export class ReminderService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/reminders`;

  list(status: ReminderStatus = 'OPEN'): Observable<Reminder[]> {
    return this.http.get<ApiResponse<Reminder[]>>(this.url, { params: { status } }).pipe(map((res) => res.data));
  }

  generate(): Observable<Reminder[]> {
    return this.http.post<ApiResponse<Reminder[]>>(`${this.url}/generate`, null).pipe(map((res) => res.data));
  }

  updateStatus(id: string, status: 'COMPLETED' | 'DISMISSED'): Observable<Reminder> {
    return this.http
      .patch<ApiResponse<Reminder>>(`${this.url}/${id}/status`, null, { params: { status } })
      .pipe(map((res) => res.data));
  }

  previewEmail(id: string): Observable<EmailPreview> {
    return this.http.get<ApiResponse<EmailPreview>>(`${this.url}/${id}/email/preview`).pipe(map((res) => res.data));
  }

  sendEmail(id: string): Observable<EmailDelivery> {
    return this.http.post<ApiResponse<EmailDelivery>>(`${this.url}/${id}/email/send`, null).pipe(map((res) => res.data));
  }

  deliveries(id: string): Observable<EmailDelivery[]> {
    return this.http.get<ApiResponse<EmailDelivery[]>>(`${this.url}/${id}/email/deliveries`).pipe(map((res) => res.data));
  }
}
