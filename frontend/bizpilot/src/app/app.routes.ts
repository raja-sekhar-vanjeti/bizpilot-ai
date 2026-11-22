import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth.guard';
import { Shell } from './layout/shell';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Sign in · BizPilot AI',
    loadComponent: () => import('./features/auth/login').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    title: 'Create account · BizPilot AI',
    loadComponent: () => import('./features/auth/register').then((m) => m.RegisterPage),
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Dashboard · BizPilot AI',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.DashboardPage),
      },
      {
        path: 'customers',
        title: 'Customers · BizPilot AI',
        loadComponent: () => import('./features/customers/customers').then((m) => m.CustomersPage),
      },
      {
        path: 'invoices',
        title: 'Invoices · BizPilot AI',
        loadComponent: () => import('./features/invoices/invoice-list').then((m) => m.InvoiceListPage),
      },
      {
        path: 'invoices/new',
        title: 'New invoice · BizPilot AI',
        loadComponent: () => import('./features/invoices/invoice-form').then((m) => m.InvoiceFormPage),
      },
      {
        path: 'invoices/:id/edit',
        title: 'Edit invoice · BizPilot AI',
        loadComponent: () => import('./features/invoices/invoice-form').then((m) => m.InvoiceFormPage),
      },
      {
        path: 'invoices/:id',
        title: 'Invoice · BizPilot AI',
        loadComponent: () => import('./features/invoices/invoice-detail').then((m) => m.InvoiceDetailPage),
      },
      {
        path: 'payments',
        title: 'Payments · BizPilot AI',
        loadComponent: () => import('./features/payments/payments').then((m) => m.PaymentsPage),
      },
      {
        path: 'expenses',
        title: 'Expenses · BizPilot AI',
        loadComponent: () => import('./features/expenses/expenses').then((m) => m.ExpensesPage),
      },
      {
        path: 'reminders',
        title: 'Reminders · BizPilot AI',
        loadComponent: () => import('./features/reminders/reminders').then((m) => m.RemindersPage),
      },
      {
        path: 'cashflow',
        title: 'Cash-flow forecast · BizPilot AI',
        loadComponent: () => import('./features/cashflow/forecast').then((m) => m.ForecastPage),
      },
      {
        path: 'what-if',
        title: 'What-if simulation · BizPilot AI',
        loadComponent: () => import('./features/cashflow/what-if').then((m) => m.WhatIfPage),
      },
      {
        path: 'insights',
        title: 'Insights · BizPilot AI',
        loadComponent: () => import('./features/insights/insights').then((m) => m.InsightsPage),
      },
      {
        path: 'profile',
        title: 'Profile & settings · BizPilot AI',
        loadComponent: () => import('./features/profile/profile').then((m) => m.ProfilePage),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
