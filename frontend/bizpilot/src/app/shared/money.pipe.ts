import { Pipe, PipeTransform } from '@angular/core';

const formatters = new Map<string, Intl.NumberFormat>();

/** Formats an amount in Indian grouping, e.g. 100000 -> ₹1,00,000.00. */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: number | null | undefined, currency = 'INR'): string {
    if (value === null || value === undefined || Number.isNaN(Number(value))) {
      return '—';
    }
    const code = currency || 'INR';
    let fmt = formatters.get(code);
    if (!fmt) {
      try {
        fmt = new Intl.NumberFormat('en-IN', { style: 'currency', currency: code, minimumFractionDigits: 2 });
      } catch {
        fmt = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
      }
      formatters.set(code, fmt);
    }
    return fmt.format(Number(value));
  }
}

/** "BANK_TRANSFER" -> "Bank Transfer". */
@Pipe({ name: 'label' })
export class LabelPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) return '';
    return value
      .toLowerCase()
      .split('_')
      .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
      .join(' ');
  }
}
