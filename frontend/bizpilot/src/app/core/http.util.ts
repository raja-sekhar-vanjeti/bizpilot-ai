import { HttpErrorResponse, HttpParams } from '@angular/common/http';
import { PageResult, SpringPage } from './models';

/** Builds HttpParams, skipping null/undefined/empty values. */
export function toParams(values: object): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== null && value !== undefined && value !== '') {
      params = params.set(key, String(value));
    }
  }
  return params;
}

export function toPageResult<T>(page: SpringPage<T>): PageResult<T> {
  const meta = page.page;
  const items = page.content ?? [];
  return {
    items,
    page: meta?.number ?? page.number ?? 0,
    size: meta?.size ?? page.size ?? items.length,
    totalElements: meta?.totalElements ?? page.totalElements ?? items.length,
    totalPages: meta?.totalPages ?? page.totalPages ?? 1,
  };
}

/** Extracts a user-facing message from an API error (ApiResponse.message) or network failure. */
export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Cannot reach the BizPilot server. Check that the backend is running.';
    }
    const body = error.error as { message?: string } | null;
    if (body && typeof body === 'object' && body.message) {
      return body.message;
    }
    if (error.status === 403) {
      return 'You are not allowed to perform this action.';
    }
  }
  return fallback;
}

/** A date as yyyy-MM-dd in the browser's local time zone, optionally offset by days. */
export function today(offsetDays = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

/** "BANK_TRANSFER" -> "Bank Transfer". */
export function label(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  return value
    .toLowerCase()
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}

/** Converts empty strings in a form value to null so optional DTO fields are omitted cleanly. */
export function blankToNull<T extends object>(value: T): T {
  const out: Record<string, unknown> = {};
  for (const [key, v] of Object.entries(value)) {
    out[key] = typeof v === 'string' && v.trim() === '' ? null : typeof v === 'string' ? v.trim() : v;
  }
  return out as T;
}
