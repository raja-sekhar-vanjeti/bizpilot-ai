import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_BASE_URL } from '../api.config';
import { toPageResult, toParams } from '../http.util';
import { ApiResponse, AuditLogEntry, AuditLogPage, SpringPage } from '../models';

/** /api/audit-logs — read-only audit trail. entityType is optional; blank values are omitted. */
@Injectable({ providedIn: 'root' })
export class AuditLogService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/audit-logs`;

  list(entityType?: string, page = 0, size = 20): Observable<AuditLogPage> {
    return this.http
      .get<ApiResponse<SpringPage<AuditLogEntry>>>(this.url, { params: toParams({ entityType, page, size }) })
      .pipe(map((res) => toPageResult(res.data)));
  }
}
