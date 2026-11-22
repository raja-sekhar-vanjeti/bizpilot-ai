// Mirrors the backend DTOs exactly (com.bizpilot.*.dto). Do not add fields the API does not return.

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

/** Spring Data page. Supports both the direct and the VIA_DTO ("page" object) serialization. */
export interface SpringPage<T> {
  content: T[];
  totalElements?: number;
  totalPages?: number;
  number?: number;
  size?: number;
  page?: { size: number; number: number; totalElements: number; totalPages: number };
}

export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PageQuery {
  page?: number;
  size?: number;
  sort?: string;
}

// ---- Auth
export interface LoginRequest {
  email: string;
  password: string;
}
export interface RegisterRequest {
  fullName: string;
  businessName: string;
  email: string;
  password: string;
  phone?: string | null;
}
export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
}
export interface UserProfile {
  id: string;
  fullName: string;
  businessName: string;
  email: string;
  phone: string | null;
  createdAt: string;
}

// ---- Customers
export interface CustomerRequest {
  name: string;
  email?: string | null;
  phone?: string | null;
  companyName?: string | null;
  billingAddress?: string | null;
  notes?: string | null;
}
export interface Customer {
  id: string;
  name: string;
  email: string | null;
  phone: string | null;
  companyName: string | null;
  billingAddress: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

// ---- Invoices
export type InvoiceStatus = 'DRAFT' | 'SENT' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE' | 'CANCELLED';
export const INVOICE_STATUSES: InvoiceStatus[] = ['DRAFT', 'SENT', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'CANCELLED'];

export interface InvoiceItemRequest {
  description: string;
  quantity: number;
  unitPrice: number;
}
export interface InvoiceRequest {
  customerId: string;
  invoiceDate: string;
  dueDate: string;
  taxPercent?: number | null;
  discountAmount?: number | null;
  currency?: string | null;
  notes?: string | null;
  items: InvoiceItemRequest[];
}
export interface InvoiceItem {
  id: string;
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}
export interface Invoice {
  id: string;
  invoiceNumber: string;
  customerId: string;
  customerName: string;
  invoiceDate: string;
  dueDate: string;
  subtotal: number;
  taxPercent: number;
  taxAmount: number;
  discountAmount: number;
  totalAmount: number;
  paidAmount: number;
  outstandingAmount: number;
  currency: string;
  notes: string | null;
  status: InvoiceStatus;
  sentAt: string | null;
  items: InvoiceItem[];
}
export interface InvoiceFilter extends PageQuery {
  status?: InvoiceStatus | null;
  customerId?: string | null;
  from?: string | null;
  to?: string | null;
}

// ---- Payments
export type PaymentMethod = 'CASH' | 'BANK_TRANSFER' | 'UPI' | 'CARD' | 'OTHER';
export const PAYMENT_METHODS: PaymentMethod[] = ['CASH', 'BANK_TRANSFER', 'UPI', 'CARD', 'OTHER'];

export interface PaymentRequest {
  invoiceId: string;
  amount: number;
  paymentDate?: string | null;
  method: PaymentMethod;
  reference?: string | null;
  notes?: string | null;
}
export interface Payment {
  id: string;
  invoiceId: string;
  amount: number;
  paymentDate: string;
  method: PaymentMethod;
  reference: string | null;
  notes: string | null;
  createdAt: string;
}

// ---- Expenses
export type ExpenseCategory =
  | 'RENT' | 'SALARY' | 'SOFTWARE' | 'MARKETING' | 'TRAVEL' | 'UTILITIES' | 'EQUIPMENT' | 'OTHER';
export const EXPENSE_CATEGORIES: ExpenseCategory[] =
  ['RENT', 'SALARY', 'SOFTWARE', 'MARKETING', 'TRAVEL', 'UTILITIES', 'EQUIPMENT', 'OTHER'];

export interface ExpenseRequest {
  description: string;
  category: ExpenseCategory;
  amount: number;
  expenseDate: string;
  paymentMethod: PaymentMethod;
  vendor?: string | null;
  notes?: string | null;
}
export interface Expense {
  id: string;
  description: string;
  category: ExpenseCategory;
  amount: number;
  expenseDate: string;
  paymentMethod: PaymentMethod;
  vendor: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}
export interface ExpenseFilter extends PageQuery {
  category?: ExpenseCategory | null;
  from?: string | null;
  to?: string | null;
}

// ---- Dashboard
export interface DashboardSummary {
  totalInvoiced: number;
  totalCollected: number;
  outstandingReceivables: number;
  overdueReceivables: number;
  totalExpenses: number;
  netCashMovement: number;
  customerCount: number;
  invoiceCount: number;
  paidInvoiceCount: number;
  pendingInvoiceCount: number;
  overdueInvoiceCount: number;
}
export interface TransactionView {
  type: 'PAYMENT' | 'EXPENSE';
  reference: string;
  amount: number;
  date: string;
}
export interface MonthlyPoint {
  month: string;
  revenue: number;
  expenses: number;
}

// ---- Reminders
export type ReminderStatus = 'OPEN' | 'COMPLETED' | 'DISMISSED';
export type ReminderType = 'UPCOMING' | 'OVERDUE';
export interface Reminder {
  id: string;
  invoiceId: string;
  reminderType: ReminderType;
  status: ReminderStatus;
  message: string;
  reminderDate: string;
}

// ---- Cash flow
export type ForecastGranularity = 'DAILY' | 'WEEKLY';
export type ForecastHorizon = 30 | 60 | 90;
export interface ForecastPeriod {
  periodStart: string;
  periodEnd: string;
  openingBalance: number;
  confirmedInflow: number;
  expectedInflow: number;
  scheduledOutflow: number;
  projectedOutflow: number;
  netChange: number;
  closingBalance: number;
}
export interface ExpectedInvoice {
  invoiceId: string;
  invoiceNumber: string;
  customerId: string;
  customerName: string;
  outstandingAmount: number;
  dueDate: string;
  expectedDate: string;
  overdue: boolean;
  withinHorizon: boolean;
}
export interface CashflowForecast {
  horizonDays: number;
  granularity: ForecastGranularity;
  asOfDate: string;
  startDate: string;
  endDate: string;
  confirmedCashIn: number;
  confirmedCashOut: number;
  openingBalance: number;
  totalConfirmedInflow: number;
  totalExpectedInflow: number;
  totalScheduledOutflow: number;
  totalProjectedOutflow: number;
  averageDailyExpense: number;
  projectedClosingBalance: number;
  lowestProjectedBalance: number;
  lowestBalanceDate: string | null;
  expectedInflowBeyondHorizon: number;
  periods: ForecastPeriod[];
  expectedInvoices: ExpectedInvoice[];
  assumptions: string[];
}
export interface WhatIfRequest {
  delayDays: number;
  days?: ForecastHorizon | null;
  customerId?: string | null;
  invoiceIds?: string[] | null;
}
export interface ScenarioSummary {
  totalExpectedInflow: number;
  projectedClosingBalance: number;
  lowestProjectedBalance: number;
  lowestBalanceDate: string | null;
}
export interface AffectedInvoice {
  invoiceId: string;
  invoiceNumber: string;
  customerName: string;
  outstandingAmount: number;
  originalExpectedDate: string;
  revisedExpectedDate: string;
  movedBeyondHorizon: boolean;
}
export interface WhatIfResponse {
  horizonDays: number;
  delayDays: number;
  baseline: ScenarioSummary;
  simulated: ScenarioSummary;
  closingBalanceImpact: number;
  lowestBalanceImpact: number;
  inflowPushedBeyondHorizon: number;
  affectedInvoices: AffectedInvoice[];
  baselinePeriods: ForecastPeriod[];
  simulatedPeriods: ForecastPeriod[];
}

// ---- Insights
export type InsightSeverity = 'CRITICAL' | 'WARNING' | 'INFO';
export interface Insight {
  code: string;
  title: string;
  description: string;
  severity: InsightSeverity;
  amount: number | null;
  suggestedAction: string;
  relevantDate: string | null;
}

// ---- Email (reminders)
export interface EmailPreview { to: string; subject: string; htmlBody: string; }
export interface EmailDelivery { deliveryId: string; reminderId: string; recipientEmail: string; subject: string; status: 'PENDING' | 'SENT' | 'FAILED'; sentAt: string | null; failureReason: string | null; }

// ---- Audit log (read-only; GET /api/audit-logs)
export type AuditAction = 'CREATE' | 'UPDATE' | 'DELETE' | 'PAYMENT' | 'SEND';
export type AuditEntityType = 'CUSTOMER' | 'INVOICE' | 'PAYMENT' | 'EXPENSE' | 'REMINDER';
export interface AuditLogEntry {
  id: string;
  action: AuditAction;
  entityType: AuditEntityType;
  entityId: string;
  summary: string;
  createdAt: string;
}
/** Paged audit log, unwrapped from ApiResponse<SpringPage<AuditLogEntry>> the same way as other lists. */
export type AuditLogPage = PageResult<AuditLogEntry>;

