<#
.SYNOPSIS
  Fills a BizPilot account with interrelated demo data through the public REST API.

.DESCRIPTION
  Creates 6 customers, 18 invoices (paid, partially paid, overdue, upcoming, draft, cancelled),
  payments linked to those invoices, 6 months of expenses, and reminders generated from the
  open invoices. Every invoice belongs to a customer, every payment to an invoice, and every
  reminder to an overdue/upcoming invoice, so all modules (dashboard, forecast, what-if,
  insights) show connected numbers.

  Dates are relative to today, so the data always looks current.
  Nothing is deleted. The seed is skipped if the account already contains the demo customers.

.PARAMETER ApiBase
  Backend API base URL. Default: http://localhost:8080/api

.PARAMETER Email
  Account to seed. Registered if it does not exist. Default: demo@bizpilot.local

.PARAMETER Password
  Password for that account. If omitted for a new account, a random one is generated and saved
  to scripts/.demo-credentials.txt (git-ignored).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/seed-demo-data.ps1
#>
param(
    [string]$ApiBase = 'http://localhost:8080/api',
    [string]$Email = 'demo@bizpilot.local',
    [string]$Password
)

$ErrorActionPreference = 'Stop'
$credFile = Join-Path $PSScriptRoot '.demo-credentials.txt'

function Invoke-Api {
    param([string]$Method, [string]$Path, $Body, [string]$Token)
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $params = @{ Method = $Method; Uri = "$ApiBase$Path"; Headers = $headers; ContentType = 'application/json; charset=utf-8' }
    if ($null -ne $Body) { $params['Body'] = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 10)) }
    try {
        return (Invoke-RestMethod @params).data
    } catch {
        $msg = $_.ErrorDetails.Message
        throw "API $Method $Path failed: $($_.Exception.Message) $msg"
    }
}

function D([int]$offsetDays) { (Get-Date).Date.AddDays($offsetDays).ToString('yyyy-MM-dd') }

# ---------- Account ----------
if (-not $Password -and (Test-Path $credFile)) {
    $saved = Get-Content $credFile | Where-Object { $_ -like "$Email *" } | Select-Object -First 1
    if ($saved) { $Password = $saved.Split(' ', 2)[1] }
}
if (-not $Password -and $Email -ne 'demo@bizpilot.local') {
    # Your own account: ask for the password without echoing it (never saved anywhere).
    $secure = Read-Host "Password for $Email" -AsSecureString
    $Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}
$generated = $false
if (-not $Password) {
    $chars = 'abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'
    $Password = -join (1..14 | ForEach-Object { $chars[(Get-Random -Maximum $chars.Length)] })
    $generated = $true
}

$token = $null
try {
    $token = (Invoke-Api POST '/auth/login' @{ email = $Email; password = $Password }).token
    Write-Host "Logged in as $Email"
} catch {
    if (-not $generated) { throw "Login failed for $Email - check the email and password. ($($_.Exception.Message))" }
    Write-Host "Registering $Email ..."
    $token = (Invoke-Api POST '/auth/register' @{
        fullName = 'Demo User'; businessName = 'Demo Business Pvt Ltd'
        email = $Email; password = $Password; phone = '+91-98765-43210'
    }).token
    if (-not $token) { $token = (Invoke-Api POST '/auth/login' @{ email = $Email; password = $Password }).token }
    if ($generated) {
        Add-Content -Path $credFile -Value "$Email $Password"
        Write-Host "Credentials saved to $credFile"
    }
}

# ---------- Idempotency ----------
$existing = Invoke-Api GET '/customers?search=anita.rao@example.com&size=5' $null $token
if ($existing.content | Where-Object { $_.email -eq 'anita.rao@example.com' }) {
    Write-Host 'Demo data already present for this account; nothing to do.'
    exit 0
}

# ---------- Customers ----------
function New-Customer($name, $email, $phone, $company, $address, $notes) {
    (Invoke-Api POST '/customers' @{ name = $name; email = $email; phone = $phone; companyName = $company; billingAddress = $address; notes = $notes } $token).id
}
$anita  = New-Customer 'Anita Rao'    'anita.rao@example.com'    '+91-98100-00001' 'Rao Design Studio'      '12 MG Road, Bengaluru 560001'      'Prefers invoices by email'
$vikram = New-Customer 'Vikram Mehta' 'vikram.mehta@example.com' '+91-98100-00002' 'Mehta Logistics LLP'    '45 Ring Road, Ahmedabad 380015'    $null
$priya  = New-Customer 'Priya Nair'   'priya.nair@example.com'   '+91-98100-00003' 'Nair Boutique'          '7 Marine Drive, Kochi 682011'      $null
$arjun  = New-Customer 'Arjun Patel'  'arjun.patel@example.com'  '+91-98100-00004' 'Patel Foods'            '88 Station Road, Pune 411001'      'Net 15 terms - often pays late'
$sara   = New-Customer 'Sara Thomas'  'sara.thomas@example.com'  '+91-98100-00005' 'Thomas & Co Accounting' '3 Park Street, Kolkata 700016'     $null
$kabir  = New-Customer 'Kabir Khan'   'kabir.khan@example.com'   '+91-98100-00006' 'Khan Creative'          '21 Brigade Road, Bengaluru 560025' $null
$neha   = New-Customer 'Neha Sharma'  'neha.sharma@example.com'  '+91-98100-00007' 'Sharma Interiors'       '9 Civil Lines, Jaipur 302006'      'Large projects, pays on time'
$rohan  = New-Customer 'Rohan Gupta'  'rohan.gupta@example.com'  '+91-98100-00008' 'Gupta Tech Solutions'   '56 Sector 18, Noida 201301'        $null
$meera  = New-Customer 'Meera Iyer'   'meera.iyer@example.com'   '+91-98100-00009' 'Iyer Clinics'           '14 TTK Road, Chennai 600018'       'Monthly retainer client'
$dev    = New-Customer 'Dev Malhotra' 'dev.malhotra@example.com' '+91-98100-00010' 'Malhotra Retail'        '30 Linking Road, Mumbai 400050'    'Slow payer - follow up early'
Write-Host 'Created 10 customers'

# ---------- Invoices + payments ----------
function Item($desc, $qty, $price) { @{ description = $desc; quantity = $qty; unitPrice = $price } }
function New-Invoice($customer, $issued, $due, $tax, $notes, $items) {
    Invoke-Api POST '/invoices' @{
        customerId = $customer; invoiceDate = (D $issued); dueDate = (D $due)
        taxPercent = $tax; discountAmount = 0; currency = 'INR'; notes = $notes; items = @($items)
    } $token
}
function Send-Invoice($inv) { Invoke-Api POST "/invoices/$($inv.id)/send" $null $token | Out-Null }
function Pay($inv, $amount, $day, $method, $ref, $notes) {
    if ($null -eq $amount) { $amount = $inv.totalAmount }
    Invoke-Api POST '/payments' @{ invoiceId = $inv.id; amount = $amount; paymentDate = (D $day); method = $method; reference = $ref; notes = $notes } $token | Out-Null
}
$invoices = 0; $payments = 0

# Paid history (spread over 6 months so the revenue chart has a trend)
$history = @(
    @{ c = $anita;  i = -170; d = -155; t = 0;  n = 'Thank you for your business'; items = @((Item 'Website redesign' 1 45000), (Item 'Hosting setup' 2 1500)); p = -152; m = 'BANK_TRANSFER'; r = 'NEFT-1001' },
    @{ c = $vikram; i = -140; d = -125; t = 18; n = $null;              items = @((Item 'Fleet consulting - monthly' 3 8000));           p = -121; m = 'UPI';           r = 'UPI-2002' },
    @{ c = $arjun;  i = -130; d = -115; t = 0;  n = 'Milestone billing'; items = @((Item 'Catering contract - Q1' 1 25000));             p = -98;  m = 'CASH';          r = $null },
    @{ c = $sara;   i = -110; d = -80;  t = 18; n = $null;              items = @((Item 'Quarterly bookkeeping - Q2' 1 18000));          p = -82;  m = 'BANK_TRANSFER'; r = 'NEFT-1004' },
    @{ c = $priya;  i = -95;  d = -80;  t = 0;  n = $null;              items = @((Item 'Store photoshoot' 1 22000), (Item 'Product catalogue' 1 8000)); p = -79; m = 'CARD'; r = 'CARD-3005' },
    @{ c = $arjun;  i = -80;  d = -65;  t = 0;  n = $null;              items = @((Item 'Festival menu design' 1 18000));                p = -48;  m = 'UPI';           r = 'UPI-2006' },
    @{ c = $anita;  i = -60;  d = -45;  t = 18; n = 'Monthly retainer'; items = @((Item 'Website maintenance retainer' 1 9000));         p = -44;  m = 'BANK_TRANSFER'; r = 'NEFT-1007' },
    @{ c = $kabir;  i = -55;  d = -40;  t = 0;  n = $null;              items = @((Item 'Social media kit' 1 12000));                    p = -41;  m = 'UPI';           r = 'UPI-2008' },
    @{ c = $vikram; i = -40;  d = -25;  t = 0;  n = $null;              items = @((Item 'Warehouse layout review' 1 15000));             p = -27;  m = 'UPI';           r = 'UPI-2009' },
    @{ c = $sara;   i = -35;  d = -20;  t = 18; n = $null;              items = @((Item 'GST filing support' 1 10000));                  p = -18;  m = 'BANK_TRANSFER'; r = 'NEFT-1010' },
    @{ c = $neha;   i = -175; d = -160; t = 18; n = 'Phase 1';          items = @((Item 'Showroom interior concept' 1 40000));           p = -158; m = 'BANK_TRANSFER'; r = 'NEFT-1011' },
    @{ c = $neha;   i = -120; d = -105; t = 18; n = 'Phase 2';          items = @((Item '3D walkthrough renders' 5 6000));               p = -104; m = 'BANK_TRANSFER'; r = 'NEFT-1012' },
    @{ c = $rohan;  i = -150; d = -135; t = 18; n = $null;              items = @((Item 'Mobile app UI design' 1 55000));                p = -133; m = 'BANK_TRANSFER'; r = 'NEFT-1013' },
    @{ c = $rohan;  i = -75;  d = -60;  t = 18; n = $null;              items = @((Item 'Design system handover' 1 20000), (Item 'Icon set' 1 5000)); p = -61; m = 'CARD'; r = 'CARD-3014' },
    @{ c = $meera;  i = -160; d = -145; t = 0;  n = 'Monthly retainer'; items = @((Item 'Clinic website care' 1 8000));                  p = -146; m = 'UPI';           r = 'UPI-2015' },
    @{ c = $meera;  i = -130; d = -115; t = 0;  n = 'Monthly retainer'; items = @((Item 'Clinic website care' 1 8000));                  p = -116; m = 'UPI';           r = 'UPI-2016' },
    @{ c = $meera;  i = -100; d = -85;  t = 0;  n = 'Monthly retainer'; items = @((Item 'Clinic website care' 1 8000));                  p = -86;  m = 'UPI';           r = 'UPI-2017' },
    @{ c = $meera;  i = -70;  d = -55;  t = 0;  n = 'Monthly retainer'; items = @((Item 'Clinic website care' 1 8000));                  p = -56;  m = 'UPI';           r = 'UPI-2018' },
    @{ c = $meera;  i = -40;  d = -25;  t = 0;  n = 'Monthly retainer'; items = @((Item 'Clinic website care' 1 8000));                  p = -26;  m = 'UPI';           r = 'UPI-2019' },
    @{ c = $dev;    i = -100; d = -85;  t = 18; n = $null;              items = @((Item 'Store signage design' 1 16000));                p = -60;  m = 'CASH';          r = $null },
    @{ c = $dev;    i = -65;  d = -50;  t = 18; n = $null;              items = @((Item 'Festive sale banners' 10 1200));                p = -30;  m = 'UPI';           r = 'UPI-2021' }
)
foreach ($h in $history) {
    $inv = New-Invoice $h.c $h.i $h.d $h.t $h.n $h.items; Send-Invoice $inv
    Pay $inv $null $h.p $h.m $h.r 'Paid in full'
    $invoices++; $payments++
}

# Partially paid, due in the future
$inv = New-Invoice $priya -20 10 0 $null @((Item 'Brand identity package' 1 30000)); Send-Invoice $inv
Pay $inv 10000 -15 'CARD' 'CARD-3011' 'First instalment'; $invoices++; $payments++

# Partially paid and overdue (third late invoice for Arjun -> repeated late payer)
$inv = New-Invoice $arjun -50 -35 0 'Milestone billing' @((Item 'Catering contract - Q2' 2 12500)); Send-Invoice $inv
Pay $inv 8000 -33 'CASH' $null 'Part payment in cash'; $invoices++; $payments++

# Overdue, nothing paid
$inv = New-Invoice $sara -45 -15 18 $null @((Item 'Quarterly bookkeeping - Q3' 1 18000)); Send-Invoice $inv; $invoices++
$inv = New-Invoice $kabir -30 -5 0 $null @((Item 'UI audit - freelance' 4 3500)); Send-Invoice $inv; $invoices++

# Sent, due soon / later (feed the cash-flow forecast and upcoming reminders)
$inv = New-Invoice $vikram -1 5 0 $null @((Item 'Route optimisation workshop' 1 15000)); Send-Invoice $inv; $invoices++
$inv = New-Invoice $priya -2 14 0 $null @((Item 'Logo animation' 1 6000)); Send-Invoice $inv; $invoices++
$inv = New-Invoice $anita -3 27 18 'Monthly retainer' @((Item 'Website maintenance retainer' 1 9000)); Send-Invoice $inv; $invoices++

$inv = New-Invoice $meera -10 5 0 'Monthly retainer' @((Item 'Clinic website care' 1 8000)); Send-Invoice $inv; $invoices++
$inv = New-Invoice $rohan -8 22 18 $null @((Item 'Dashboard redesign' 1 48000)); Send-Invoice $inv
Pay $inv 20000 -4 'BANK_TRANSFER' 'NEFT-1022' 'Advance'; $invoices++; $payments++
$inv = New-Invoice $neha -5 40 18 'Phase 3' @((Item 'Site supervision' 10 4500)); Send-Invoice $inv; $invoices++
$inv = New-Invoice $dev -40 -20 18 $null @((Item 'Shelf talker printing design' 1 9500)); Send-Invoice $inv; $invoices++

# Drafts
New-Invoice $neha 0 45 18 'Awaiting approval' @((Item 'Office furniture sourcing' 1 35000)) | Out-Null; $invoices++
New-Invoice $sara 0 30 18 'Scope to be confirmed' @((Item 'Year-end tax filing support' 1 12000)) | Out-Null; $invoices++
New-Invoice $kabir 0 15 0 $null @((Item 'Podcast cover art' 2 2500)) | Out-Null; $invoices++

# Cancelled
$inv = New-Invoice $arjun -4 10 0 'Client withdrew the order' @((Item 'Menu design' 1 7000))
Invoke-Api POST "/invoices/$($inv.id)/cancel" $null $token | Out-Null; $invoices++
Write-Host "Created $invoices invoices and $payments payments"

# ---------- Expenses (6 months) ----------
function New-Expense($desc, $cat, $amount, $day, $method, $vendor) {
    Invoke-Api POST '/expenses' @{ description = $desc; category = $cat; amount = $amount; expenseDate = (D $day); paymentMethod = $method; vendor = $vendor; notes = $null } $token | Out-Null
}
$expenses = 0
foreach ($m in 0..5) {
    $day = -165 + ($m * 30)
    New-Expense 'Office rent'             'RENT'      18000 $day         'BANK_TRANSFER' 'Skyline Properties'
    New-Expense 'Staff salaries'          'SALARY'    32000 ($day + 2)   'BANK_TRANSFER' 'Internal payroll'
    New-Expense 'Electricity and internet' 'UTILITIES' 2800 ($day + 5)   'UPI'           'City Power & Fibre'
    $expenses += 3
}
New-Expense 'Accounting SaaS subscription' 'SOFTWARE'  4500  -100 'CARD' 'CloudLedger Inc'
New-Expense 'Design tools licence'         'SOFTWARE'  6200  -40  'CARD' 'Creative Suite'
New-Expense 'Search advertising campaign'  'MARKETING' 12000 -70  'CARD' 'Search Ads Ltd'
New-Expense 'Trade fair stall'             'MARKETING' 15000 -25  'BANK_TRANSFER' 'Expo Events'
New-Expense 'Client visit - Chennai'       'TRAVEL'    6800  -60  'UPI'  'Local travel agency'
New-Expense 'Laptop for design team'       'EQUIPMENT' 85000 -10  'CARD' 'TechHub Electronics'
New-Expense 'Bank charges and misc fees'   'OTHER'     850   -4   'CASH' $null
$expenses += 7
Write-Host "Created $expenses expenses"

# ---------- Reminders ----------
$reminders = @(Invoke-Api POST '/reminders/generate' $null $token)
if ($reminders.Count -gt 0) {
    Invoke-Api PATCH "/reminders/$($reminders[0].id)/status?status=COMPLETED" $null $token | Out-Null
}
Write-Host "Generated $($reminders.Count) reminders (1 marked completed)"
Write-Host "Done. Log in to the app as $Email to see the data."
