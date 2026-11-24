# DineHub Kiosk & POS — Business Requirements Document (BRD)

| Item | Value |
| --- | --- |
| Version | 0.4 — multi-region (India; Europe = Germany, UK, France; USA; Australia) from day 1, Stripe or Adyen chosen per restaurant, multi-country tenants |
| Date | 2026-11-25 |
| Status | Draft for business review |
| Pilot tenant | SVS Temple, Madhapur branch, Hyderabad (India) |
| Supersedes | v0.1 BRD (India-only, Claude Docs draft) |
| Related | [COMPLIANCE.md](COMPLIANCE.md) (compliance & regulations register), [REQUIREMENTS.md](REQUIREMENTS.md) (original MVP brief), [DECISIONS.md](DECISIONS.md), [../kioski.md](../kioski.md) (kiosk plan) |

> **How to maintain this file.** This BRD is the source of truth for business requirements. Change it in the same
> pull request as any feature that changes behaviour, bump the version in the table above, and add a line to the
> [change log](#14-change-log). Country rules (tax rates, fiscal laws, payment rules) change often: they are
> recorded here as requirements, but the live values must sit in configuration tables, never in code.

---

## 1. Purpose, objectives and scope

DineHub is a multi-tenant SaaS platform that lets a restaurant group run counter POS, waiter ordering, QR table
ordering, self-order kiosks and a kitchen display system (KDS) across all its branches, **in India, Europe (EU + UK),
the USA and Australia from the first release**. Country-specific behaviour (tax, currency, payments, fiscal
receipts, legal labelling, language, privacy) is driven by configuration attached to each branch, not by separate
code bases.

### 1.1 Business objectives

1. Cut order-taking time and queue length by moving guests to QR and kiosk self-ordering.
2. Remove order errors between front-of-house and kitchen through a digital KDS.
3. Give restaurant and branch admins real-time sales, order and staff visibility per branch, in local currency and consolidated.
4. Take payments through branch-mapped payment terminals and local payment methods (UPI, cards, wallets, EFTPOS).
5. Be legally compliant in each launch country on day 1: tax invoices, fiscal receipts, allergens, privacy, accessibility.
6. Earn recurring revenue through subscription, kiosk rental and transaction-based plans priced per region.

### 1.2 In scope

- Platform onboarding of restaurants (tenants), legal entities and branches in any supported country
- Role-based access: platform admin, restaurant admin, branch admin, billing counter, waiter, kitchen
- Branch settings: business hours, holidays, tax and charge rules, counters and payment terminals
- Menu planner: sessions, categories, items, variants, add-ons, combos, suggestions, specials of the day
- Ordering channels: counter POS, waiter, QR at table/counter, self-order kiosk
- Order management, KDS, billing, payments, tips, receipts, notifications
- Kiosk branding: splash, login, idle/attract screens with ads
- Dashboards and reports, per branch and consolidated across currencies
- **Regionalisation layer**: country packs for tax, fiscalisation, payments, formats, languages, legal content, data residency

### 1.3 Out of scope for v1 (later phases)

- Inventory, recipe costing and purchase orders
- Third-party delivery aggregators (Swiggy, Zomato, Uber Eats, DoorDash, Deliveroo, Just Eat, Menulog)
- Loyalty, wallet and gift cards
- Table reservation and waitlist
- Payroll and staff attendance

---

## 2. Regional strategy (new in v0.2)

**Decision:** build one global product with a *country pack* per country. Every branch belongs to exactly one country
and one legal entity. The country pack supplies defaults; the branch can override only what the law allows.

### 2.1 Launch markets

| Region | Countries at launch | Notes |
| --- | --- | --- |
| India | IN | Pilot market; GST, UPI, FSSAI |
| Europe | **Germany (DE), United Kingdom (UK), France (FR)** — decided 2026-11-25 | Each country has its own VAT rules. **Germany and France have fiscal cash-register laws** (§2.4) that must be met before the first sale there; the UK has none, so it is the fastest European go-live. Other EU countries are later additions through new country packs. |
| USA | US (all states) | Sales tax differs by state, county and city; tips are standard |
| Australia | AU (all states/territories) | GST 10%, prices shown GST-inclusive |

### 2.2 What varies by country (country pack contents)

| Area | India | Europe (EU + UK) | USA | Australia |
| --- | --- | --- | --- | --- |
| Currency | INR (₹) | EUR (€) for DE and FR, GBP (£) for UK | USD ($) | AUD ($) |
| Tax name & structure | GST split CGST + SGST (intra-state) or IGST; restaurant service usually 5% without input credit, 18% in specified premises | VAT (DE: MwSt/USt, FR: TVA, UK: VAT). Rate depends on **eat-in vs takeaway** and **food vs drink/alcohol** — see §2.5 | Sales tax = state + county + city (+ special district); some cities add a meals tax; rates change often | GST 10%; some food GST-free when sold cold for takeaway (basic food) |
| Price display | Tax may be shown exclusive on the menu, added on the bill | **Tax-inclusive** consumer prices required | **Tax-exclusive** menu prices; tax added on the bill | **Tax-inclusive** prices required (Australian Consumer Law) |
| Business tax ID | GSTIN (15 chars), PAN | VAT number (validate EU via VIES), UK VAT number | EIN + state sales-tax permit per state | ABN (11 digits), GST registration |
| Tax invoice / receipt | Tax invoice: GSTIN, HSN/SAC, tax split, sequential no. per financial year (Apr–Mar); B2B e-invoice (IRN) above turnover threshold | VAT receipt with rate breakdown; **fiscal signature / QR** in countries with fiscal laws | Itemised receipt with sales tax; tip line | "Tax invoice" wording + ABN; tax invoice on request for sales above AUD 82.50 incl. GST; buyer details for sales ≥ AUD 1,000 |
| Fiscal device / certified software | Not required | **DE: TSE required. FR: certified POS software required. UK: not required** (§2.4) | Not required | Not required |
| Payment methods | UPI dynamic QR, cards, wallets; EDC terminals with TID | Cards (contactless, SCA under PSD2), Apple/Google Pay; DE: girocard; FR: Cartes Bancaires; meal vouchers (FR titres-restaurant, DE Essensgutschein) | EMV cards, contactless, Apple/Google Pay; tip on terminal | EFTPOS, cards, Apple/Google Pay, PayID; least-cost routing |
| Payment provider (decided) | Razorpay / PayU / Pine Labs (existing) until Stripe or Adyen is confirmed for Indian merchants and UPI (Q5b) | **Stripe or Adyen — restaurant picks one** | **Stripe or Adyen — restaurant picks one** | **Stripe or Adyen — restaurant picks one** |
| Card surcharge | Not permitted by networks | Banned on consumer cards (EU PSD2, UK) | Allowed in most states with caps and disclosure; banned in some | Allowed up to cost of acceptance (rules under RBA review) |
| Tipping | Optional | Optional; UK Employment (Allocation of Tips) Act 2023: all tips to workers + written policy | Expected; tip prompt (e.g. 15/18/20% + custom), tip on pre- or post-tax amount | Optional |
| Service charge | Must be optional for the guest (CCPA guidelines, upheld by Delhi HC) | FR: service is included in menu prices by law ("service compris"), no separate service charge line; UK: discretionary service charge common, shown clearly; DE: not customary | Mandatory fees must be disclosed (e.g. California SB 478 / SB 1524) | Must be disclosed up-front; public-holiday surcharge must be shown on menu |
| Cash rounding | Nearest ₹1 | None in DE, FR, UK | None by law (penny phase-out: optional nearest 5¢ on cash) | Nearest 5 cents on cash totals |
| Food labelling | Veg/non-veg mark; FSSAI licence no.; calories + allergens for chains with ≥10 outlets | 14 allergens must be available for every item (EU 1169/2011; UK equivalent + Natasha's Law for pre-packed); DE: declarable additives (Zusatzstoffe) on menu; FR: origin of meat (beef, pork, poultry, lamb) and optional "fait maison" mark; UK: calories on menus for businesses with 250+ employees | Major 9 allergens; calories on menus for chains with ≥20 outlets (FDA) | Allergen info (FSANZ); kilojoule display for chains in several states |
| Privacy | DPDP Act 2023 | GDPR / UK GDPR; cookie consent on QR web app | CCPA/CPRA and other state laws | Privacy Act 1988 (APPs) |
| Accessibility | RPwD Act; WCAG 2.1 AA | European Accessibility Act (from 28 Jun 2025) for self-service and payment terminals; EN 301 549 | ADA (kiosk lawsuits common); WCAG 2.1 AA | Disability Discrimination Act; WCAG 2.1 AA |
| Data residency | Payment data handled by RBI-regulated gateway; restaurant data may be hosted in India region | EU/UK personal data hosted in EU region by default | US region | AU region preferred |
| Date / time / units | DD/MM/YYYY, 12/24 h, metric, Indian digit grouping (1,00,000) | DD/MM/YYYY or DD.MM.YYYY, 24 h, metric, comma decimal in many countries | MM/DD/YYYY, 12 h, US units (oz) | DD/MM/YYYY, 12 h, metric |
| Time zones | Single (IST) | Several, with DST | Several, with DST | Several, with DST (not all states) |
| Address | Line 1/2, city, state, 6-digit PIN | Line 1/2, city, postcode (format per country), region optional | Line 1/2, city, state, ZIP / ZIP+4 | Line 1/2, suburb, state, 4-digit postcode |
| Languages (day 1) | English, Hindi, Telugu | DE: German + English; FR: French + English; UK: English | English, Spanish | English |

> All rates and thresholds above are examples for requirement-setting. They must be verified with a local tax
> advisor before go-live in each country and are stored as dated configuration (§2.3).

### 2.3 Regionalisation requirements

| ID | Requirement |
| --- | --- |
| RG-01 | Each **branch** has `country`, `currency`, `locale`, `time_zone`, `legal_entity`, `tax_profile`, `fiscal_profile`, `payment_profile`. Set at creation from the country pack; country and currency cannot change once the branch has orders. |
| RG-02 | A **tenant** can own branches in several countries through several **legal entities** (company name, address, tax IDs, invoice numbering). **Confirmed: one restaurant will run branches in more than one country** — see RG-21. |
| RG-03 | **Money** is stored as integer minor units + ISO 4217 currency code everywhere (orders, payments, reports). No hard-coded currency symbol or default currency. |
| RG-04 | **Tax engine** is rule-based: tax components per jurisdiction, rate, inclusive/exclusive, compound or not, applies-to (item tax category × order type), effective-from/to dates. Supports GST split, VAT multi-rate, US stacked sales tax, AU GST. US rates can come from a tax-rate provider (Avalara, TaxJar, Stripe Tax) by branch address. |
| RG-05 | **Items carry a tax category** (e.g. hot food, cold food, soft drink, alcohol, packaged good, service), not a tax percentage. The branch's tax profile maps category + order type → rate. |
| RG-06 | **Prices-include-tax** flag per branch, defaulted by country (on for EU/UK/AU, off for US, configurable for IN). Bills show tax extracted or added accordingly. |
| RG-07 | **Fiscal adapter interface**: every completed sale, void and refund is passed to the branch's fiscal adapter (none, DE-TSE, FR-NF525, IT-RT, AT-RKSV…), which returns the signature/QR printed on the receipt. A sale cannot be completed if the fiscal device is required and unreachable, except under the country's documented outage procedure. |
| RG-08 | **Payment provider interface**: gateway and terminal integrations are plug-ins selected per branch; the "TID" field from v0.1 becomes a generic **payment terminal** record (provider, terminal/reader ID, serial). **Decided providers: Stripe and Adyen**, both built behind the same interface, plus the existing Indian providers for India. **Each restaurant (tenant) picks one of Stripe or Adyen at onboarding**; that choice applies to all its non-India branches and legal entities, and each legal entity gets its own connected/merchant account under it. Terminals are ordered and paired from the chosen provider. Switching provider later is an admin-assisted migration (new merchant accounts, new terminals, refunds of old payments still routed to the old provider), not a self-service toggle. Per provider: online checkout (QR/kiosk web), card-present terminals (Stripe Terminal / Adyen Terminal API), tap-to-pay on phone, tips on terminal, refunds, webhooks, payouts per legal entity (Stripe Connect / Adyen for Platforms) so DineHub can collect transaction-plan fees. |
| RG-09 | **Invoice/bill numbering** per legal entity with country rules: gap-free, prefix, reset at financial year (India Apr–Mar; others calendar year or configurable). |
| RG-10 | **Locale formatting** of dates, times, numbers and currency through the platform locale (no manual string formatting). Translations stored per language for UI text and per menu item for name/description. |
| RG-11 | **Time zone**: all timestamps stored in UTC; business hours, sessions, day-end, reports and holidays evaluated in the branch time zone, including DST changes and shifts past midnight. |
| RG-12 | **Holiday calendars** seeded per country and state/region, editable by the branch. |
| RG-13 | **Address and phone**: address form fields and validation per country; phones stored in E.164 with country code. |
| RG-14 | **Tips**: enabled/disabled per branch; preset percentages, tip base (pre- or post-tax), tip on terminal or on screen, tip allocation report per staff. Default on for US. |
| RG-15 | **Surcharges and service charges**: configurable with country guards (e.g. card surcharge blocked for EU/UK; AU public-holiday surcharge needs menu disclosure text). |
| RG-16 | **Cash rounding** rule per country, shown as a separate rounding line on the bill. |
| RG-17 | **Allergens and nutrition**: the 14 EU allergens + US major 9 (superset list), calories/kJ, dietary marks (veg/non-veg per FSSAI, vegan, gluten-free). Required fields per country enforced before an item can be published. |
| RG-18 | **Data residency**: each **branch** is pinned to the hosting region of its country (India → IN, Germany/France/UK → EU, USA → US, Australia → AU). Its personal and transactional data stay in that region. The tenant's home region holds the tenant profile, brand, master menu and **consolidated reporting aggregates without personal data**. A global directory routes logins. UK branches are hosted in the EU region (UK adequacy decision). |
| RG-19 | **Privacy**: consent capture for guest contact details, privacy notice per country, data export and deletion on request, cookie consent for the QR web app in EU/UK. |
| RG-20 | **Legal content per country**: receipt footers, privacy notice, terms, allergen statement, service-charge text, all editable per legal entity. |
| RG-21 | **Multi-country tenant** (confirmed requirement): (a) Restaurant Admin sees all branches across countries; (b) master menu is kept per country (price in local currency, local tax category, local translations and legal fields), with shared item images and descriptions; (c) consolidated dashboard and reports in the tenant reporting currency with daily FX, plus local-currency figures; (d) tax and fiscal reports always per legal entity, never mixed; (e) a user can be granted branches in several countries and the UI switches currency, language and formats with the selected branch; (f) brand and kiosk content shared, with per-country overrides (language, legal text, ad rules); (g) DineHub subscription billed per legal entity in its currency with that country's tax. |

### 2.4 Fiscal-receipt laws for the European launch countries

Germany and France block go-live until their fiscal requirement is met; plan the work and the certification lead time
before signing customers there.

| Country | Requirement | What DineHub must build | Lead time / cost driver |
| --- | --- | --- | --- |
| Germany | **KassenSichV / §146a AO**: every transaction (incl. training, voids, cancelled orders, open orders) is signed by a certified **TSE** (technical security element); receipt must show TSE serial, transaction number, start/end time and signature (usually as QR); receipt must be offered for every sale (paper or e-receipt); **DSFinV-K** data export for tax audits; each POS/kiosk and its TSE must be **notified to the tax office** (ELSTER). | DE fiscal adapter using a certified **cloud TSE** provider (e.g. fiskaly SIGN DE, Deutsche Fiskal) — one TSE client per register/kiosk; DSFinV-K export; device notification data; TSE outage handling (sell, mark "TSE failure" on receipt, report). | Provider contract + integration; no product certification of DineHub itself, but the integration must follow the TSE provider's certified usage. |
| France | **Anti-fraud law (CGI art. 286-I-3° bis)**: POS software must guarantee **inalterability, security, storage and archiving** of sales data, with a **certificate from an accredited body** (NF525 from Infocert or LNE certification) — self-declaration is no longer accepted. Requires chained signatures (hash chain) of tickets, grand totals (period, daily, monthly, annual), closures, archive files, audit log (JET), and no deletion of recorded sales. Restaurant bill ("note") rules apply. | FR fiscal module inside DineHub: signed and chained tickets and closures, perpetual grand totals, periodic archives, event journal, version control of the software, certification audit. | **Certification of DineHub software** (several months, audit fees, re-certification on major versions). Must be planned now. |
| United Kingdom | No fiscal device. VAT receipts on request with VAT number, rates and VAT amounts; Making Tax Digital for the restaurant's VAT returns (via their accounting software). | No-op fiscal adapter; VAT report and accounting export (Xero, QuickBooks, Sage). | None — fastest European go-live. |

### 2.5 VAT behaviour for Germany, UK and France

Rates are examples for requirement-setting and must be confirmed with a tax advisor; they live in the tax rule table
with effective dates.

| Case | Germany | United Kingdom | France |
| --- | --- | --- | --- |
| Food eaten in | Reduced rate (7% from 2026-01-01; confirm) | 20% | 10% |
| Food takeaway | 7% | Hot food 20%; most cold food 0% | 10% for immediate consumption; 5.5% when not for immediate consumption |
| Soft drinks | 19% | 20% | 10% eat-in / immediate; 5.5% otherwise |
| Alcohol | 19% | 20% | 20% |
| Combos / menus with mixed rates | Split by component (e.g. by cost ratio) | Split by component | Split by component |
| Same item, different rate by order type | Yes | Yes | Yes |

Requirements: the kiosk/QR/POS must capture the **order type before payment** (eat-in vs takeaway) because it changes
the VAT; changing order type after payment creates a correction document (fiscal in DE/FR); combos store the tax split
per component.

---

## 3. Stakeholders, tenant hierarchy and roles

Hierarchy: **Platform → Region → Restaurant (tenant) → Legal entity → Branch → Counter / Device**. Every record carries
`tenant_id` and `branch_id` so data is isolated and roll-ups work.

| Role | Created by | Scope | Main screens |
| --- | --- | --- | --- |
| Platform Super Admin (DineHub) | System | All tenants in a region (global admins: all regions) | Tenant onboarding, country packs, plans & billing, device registry, platform reports |
| Restaurant Admin | Platform Super Admin at onboarding | All branches of one restaurant, all countries | Legal entities, branches, branch managers, consolidated dashboard & reports, brand/kiosk content, master menu |
| Branch Admin / Manager | Restaurant Admin | One branch | Dashboard, new order, kitchen, orders, billing, menu, branch QR, staff, settings |
| Billing Counter | Branch Admin | One branch, assigned counter | New order, orders, billing |
| Waiter / Server | Branch Admin | One branch, assigned tables/sections | New order (table), orders, table status, request bill, tips |
| Kitchen | Branch Admin | One branch, assigned station | KDS, menu (availability), orders |
| Guest (QR / kiosk) | n/a (anonymous, optional OTP) | One branch | Menu, cart, pay, order status |

**Pilot seed accounts** (credentials are not stored in this repo; share via a password manager):

| Role | Username |
| --- | --- |
| Restaurant Admin | skyapparel |
| Branch Admin — Madhapur | meghadeep12 |
| Kitchen — Madhapur | kitchen.1 |
| Billing Counter — Madhapur | counter1 |

The original brief also listed `counter1` for a second Kitchen user; usernames must be unique per tenant, so that user needs its own name (e.g. `kitchen.2`).

**Account rules**

- Username unique per tenant; email and phone (E.164) unique per tenant.
- First login forces password change; password policy min 8 chars with upper, lower, digit, symbol.
- Optional 4–6 digit PIN login for counter/kitchen/waiter on shared devices, with quick user switch.
- Lock after 5 failed attempts; admin can reset password and deactivate (never hard-delete) a user.
- Activation links by email/SMS; passwords are never sent in plain text.
- Staff UI language chosen per user from the branch's enabled languages.

---

## 4. Commercial model: kiosk types and pricing

**Recommendation:** per-branch SaaS subscription with per-device add-on licences, kiosk hardware on rent or purchase,
and an optional transaction-fee plan for small outlets — priced **per region in local currency**.

### 4.1 Kiosk / device types supported

| Type | Typical hardware | Where used | Payment on device |
| --- | --- | --- | --- |
| Floor-standing self-order kiosk | 21–32" touch, thermal printer, card/UPI terminal | QSR, food courts, temples | Yes |
| Countertop / tabletop kiosk | 15–22" touch on stand | Cafes, small counters | Yes, or pay at counter |
| Wall-mounted kiosk | 21–32" touch | Space-constrained outlets | Yes |
| Tablet kiosk (Android / iPad kiosk mode) | Tablet + enclosure | Low-cost pilots | QR / pay at counter |
| QR self-order (bring your own device) | Guest's phone | Tables, counters | Online payment or postpaid |
| Waiter handheld / POS tablet | Android POS with printer | Table service | Yes |
| KDS screen | 15–24" display + bump bar | Kitchen stations | n/a |
| Customer-facing / pickup display | TV / monitor | Pickup area | n/a |

Hardware must be certified for the target region (CE/UKCA, FCC/UL, RCM, BIS) and payment terminals must be certified
by the region's acquirer. Kiosks must meet the accessibility rules in §2.2.

### 4.2 Pricing / licensing models

| Model | How it is charged | Fits |
| --- | --- | --- |
| Per-branch subscription | Monthly/annual fee per branch, tiered (Basic / Pro / Enterprise) | Most chains |
| Per-device licence | Fee per active kiosk, POS, KDS or waiter device | Many devices per branch |
| Hardware rental (kiosk-as-a-service) | Monthly rent incl. software, maintenance, replacement; 12–36 month term, deposit | Restaurants avoiding capex |
| Purchase + subscription | One-time hardware + annual software + optional maintenance | Large chains |
| Transaction-based | % of order value or fee per order | Small outlets, temples, seasonal |
| Usage tiers | Limits on items, branches or orders per month | Free/entry plan |
| Hybrid | Low base fee + lower transaction fee | Mid-size |

### 4.3 Platform requirements arising from this

- Plan catalogue per region and currency: billing basis, price, limits, trial days, tax treatment.
- Subscription per tenant/branch with renewal, grace period and read-only suspension on non-payment.
- Device registry: serial, branch, counter, ownership (rented/owned), rent, warranty and maintenance dates, region certification.
- Usage metering (orders, sales value, active devices) per branch per month.
- DineHub's own invoices with the right tax per customer country (India GST on SaaS, EU VAT incl. B2B reverse charge, UK VAT, US state sales tax on SaaS where applicable, AU GST).

---

## 5. Onboarding, branches, staff and branch settings

### 5.1 Restaurant (tenant) onboarding — Platform Super Admin

| Field | Rule |
| --- | --- |
| Organization code | Unique, uppercase, 3–10 chars, immutable |
| Restaurant name | Required |
| Email, phone | Required; phone in E.164 |
| Logo | PNG/JPG/SVG ≤ 2 MB; used on receipts, kiosk, QR menu |
| Home region | India / EU / US / AU — hosts the tenant profile, master menu and consolidated reports; each branch's own data stays in its country's region (RG-18) |
| Reporting currency | Currency for consolidated reports |
| Brand | Colours, fonts |
| Subscription plan | From the region's plan catalogue |
| Payment provider | Stripe or Adyen (one per restaurant, RG-08); India branches use the Indian provider list |

### 5.2 Legal entity — Restaurant Admin (new)

Company legal name, country, registered address, tax IDs per country (GSTIN/PAN, VAT no., EIN + state permits, ABN),
food licence (FSSAI no. in India, local registration elsewhere), invoice number series, financial-year start, receipt
legal footer.

### 5.3 Branch creation — Restaurant Admin

| Field | Rule |
| --- | --- |
| Branch name, branch code | Required; code unique in tenant, used in order/bill numbers |
| Country, legal entity | Required; loads the country pack defaults |
| Currency, locale, time zone, languages | Defaulted from country; time zone from address |
| Payment terminals (was "TIDs") | One or more: provider, terminal ID, serial |
| Payment type | Postpaid (dine-in, takeaway) and/or Prepaid (counter order, takeaway) — multi-select; open question Q4 on per-order-type setting |
| Email, phone, tagline | Required / optional |
| Area, cuisines | Area text; cuisines from master list |
| Address | Country-specific form (RG-13) |
| Branch tax registration | Branch GSTIN (India, state-wise) / VAT no. if different / US state permit |
| Other | Geo-location, seating capacity, order types enabled, receipt header/footer, active flag |
| Create branch manager (checkbox) | Username, email, first name, last name, phone, photo |

### 5.4 Staff accounts — Branch Admin

Roles: Billing Counter, Waiter, Kitchen. Fields: first name, last name, username, email, mobile, photo, role, assigned
counter / table section / kitchen station, PIN, active flag, employee code, preferred language.

### 5.5 Branch settings — Branch Admin

1. **Business hours** — Mon–Sun: open checkbox, opening and closing time; multiple shifts per day, shifts past midnight, last-order time; evaluated in branch time zone with DST. Outside hours, QR/kiosk show "Closed — opens at…".
2. **Holiday list** — name and date for the year; pre-filled from country/state calendar; copy to next year; "special hours" instead of closed; public-holiday surcharge option where legal.
3. **Tax and charges** — tax rules come from the branch tax profile (RG-04/05); the branch admin can add charges: name, type (service charge, packing, delivery, public-holiday surcharge, card surcharge where legal), percentage or fixed, value, applies to order types, taxable flag, optional-for-guest flag, sequence. Country guards block illegal combinations (RG-15).
4. **Counters and terminals** — counter name/code mapped to payment terminals, bill printer, KOT printers per station, cash drawer, opening float, fiscal device (where required).
5. **Tips** — on/off, presets, tip base, tip pooling/allocation rule (RG-14).
6. **Other** — tables and sections, kitchen stations, order/bill number format, discount rules and approval limits, void/cancel reasons, auto-close of unpaid bills at day end, notification preferences, enabled languages.

---

## 6. Menu planner

The menu shown on any channel = items that are active, available, in the current session (branch time), scheduled for
today's weekday, not out of stock, and complete for the country's mandatory fields (RG-17).

- **Sessions** — name, start, end; no overlap unless allowed; an item can be in many sessions.
- **Categories** — with sort order and image; optional sub-categories.
- **Menu item**

| Field | Rule |
| --- | --- |
| Name, description | Required; translations per enabled language |
| Image | JPG/PNG/WebP ≤ 2 MB, auto-resized |
| Base price | In branch currency; entered tax-inclusive or exclusive per branch setting (RG-06) |
| Tax category | Required (RG-05); India also HSN/SAC code |
| Preparation time | Minutes; drives KDS timers and ETA |
| Serves, quantity per serving | e.g. serves 2; quantity in metric or US units per locale |
| Dietary marks | Veg / non-veg / egg (mandatory in India), vegan, gluten-free, halal, Jain |
| Allergens | From the 14 EU + US major-9 list; mandatory in EU/UK, recommended elsewhere |
| Nutrition | Calories (kcal) / energy (kJ); mandatory for chains above the country threshold |
| Alcohol flag | Triggers age confirmation on kiosk/QR and alcohol tax category; can be blocked per channel |
| Available | Toggle; kitchen "out of stock today" auto-resets |
| Send to kitchen (KOT) | Yes/No; No skips the KDS |
| Kitchen station | Target KDS |
| Sessions, days | Multi-select; Sun–Sat or every day |
| Variants | Half/Full, Small/Regular/Large — own price, optional own prep time, one default |
| Add-ons / modifiers | Groups with min/max selection and prices |
| Suggested items | Cross-sell "goes well with" |
| Tags, sort, channels | Bestseller, new, chef's special; visibility per channel; channel price |

- **Combos** — fixed or build-your-own with choice groups; tax split by component category where the country requires.
- **Special items of the day** — date or date range, optional special price and quantity limit, banner on kiosk/QR/POS/idle screen, auto-expiry.
- **Governance** — master menu at restaurant level per country (different currency/tax), branch override only if allowed, CSV/Excel import/export, logged and scheduled price changes.

---

## 7. Ordering, order lifecycle, KDS, billing and payments

### 7.1 Channels

| Channel | Who | Flow |
| --- | --- | --- |
| Counter POS | Billing counter | Order type → items/variants/add-ons/qty → discount → payment (cash, card, QR/UPI, wallet) → KOT + receipt with token |
| Waiter | Waiter | Table → items with notes → KOT → more rounds on same order → request bill → tip |
| QR | Guest phone | Scan branch/table QR → live menu in guest's language → cart → pay (prepaid) or add to table tab (postpaid) → status page |
| Kiosk | Guest | Idle → tap → language → dine-in/takeaway → menu → upsell → cart → pay on terminal or "pay at counter" → token |

Branch QR: one per table and counter, print-ready PDF, regenerate/disable, optional waiter confirmation for table orders.

### 7.2 Order lifecycle

```
Prepaid  (counter, kiosk, takeaway): Placed → Paid → Sent to KDS → Preparing → Ready → Picked up → Closed
Postpaid (dine-in):                  Placed → Sent to KDS → Preparing → Ready → Served → Bill printed → Paid → Closed
```

- Ready notifies the owning counter/waiter, guest status page and pickup display.
- Cancel allowed until Preparing; after that only Branch Admin can void, with reason. In fiscal countries a void after payment is a fiscal cancellation/refund document, never a deletion.
- No-KOT items go straight to Ready.

### 7.3 Order record

Header: order no., branch, counter, table, channel, order type, status, guest name/contact (with consent), staff,
currency, per-status timestamps (UTC), totals. Lines: item, variant, add-ons, qty, unit price, tax category, tax
amounts per component, notes, line status, station. Prices and tax are **snapshotted** at order time. Every status
change is logged.

### 7.4 Kitchen Display System

Tickets per station, oldest first; item and order bump (Start → Done) and recall; colour timers against prep time and
sound alert; all-day item totals; out-of-stock from KDS; fallback KOT printing when offline; allergen notes
highlighted on the ticket.

### 7.5 Billing (reference: Madhapur billing screen)

- Tabs: Open Bills (count), Bill History, Payment History; search by order, table or phone; current counter and branch shown.
- Left: ready-to-pay list. Centre: bill preview with legal-entity header, tax IDs, bill no., local date/time, items, subtotal, each charge and tax component, rounding, tip, total, fiscal signature/QR where required. Right: amount due, payment methods available in that country, Show QR, Void bill, payment history.
- **Currency fix:** the reference screen shows `$` for an Indian branch; all amounts must use the branch currency (₹ for Madhapur).
- Split bill, split payment, discounts with approval, tips, refunds, reprint, e-receipt by email/SMS/WhatsApp, B2B invoice with customer tax ID (GSTIN / VAT no. / ABN).
- Cash: tendered and change; country cash rounding. Card/QR: amount pushed to the counter's terminal or dynamic QR, auto-confirmed by webhook; manual reference only as fallback.
- Shift open/close per counter, cash float, cash count, variance, Z-report (fiscal Z-report where required).

### 7.6 Notifications

New order to KDS, Ready to counter/waiter, bill requested, payment received, order delayed to Branch Admin; guest
SMS/WhatsApp/push on Ready (optional, consent-based). Channel per country (WhatsApp common in IN/EU; SMS in US/AU).

---

## 8. Kiosk and screen customisation (branding and ads)

Restaurant Admin sets brand content; Branch Admin can override per branch or device.

| Screen | Content | Rules |
| --- | --- | --- |
| Splash | Logo, background image/video, tagline | ≤ 3 s |
| Staff login | Background, logo, welcome text | Same on POS, KDS, waiter app |
| Kiosk idle / attract | Playlist of images/videos (ads, specials, combos, offers), "Tap to order" | Starts after N s idle (default 60); "Still there?" then cart cleared (default 30 s) |
| Menu banners | Carousel, Today's Special strip | Tap adds to cart |
| Order confirmation | Thank-you, token, feedback QR | Back to idle after 15 s |
| Pickup display | Now-serving tokens + ads | Split screen |

- Media library per restaurant; portrait 1080×1920 and landscape 1920×1080; offline cache on device.
- Campaigns: media, target countries/branches/devices, dates, sessions, duration, priority; content per language.
- Third-party brand ads with impression counts (later revenue line); ad content must respect local rules (e.g. alcohol advertising limits).
- Kiosk settings: languages, accessibility mode (high contrast, larger text, lower reach zone, screen-reader/audio support, keypad navigation where required), dine-in/takeaway prompt, upsell prompts, receipt print/no-print, age confirmation for alcohol.

---

## 9. Reports, permissions and data entities

### 9.1 Reports

| Report | Restaurant Admin | Branch Admin | Counter |
| --- | --- | --- | --- |
| Live dashboard: sales, orders, average order value, open bills, kitchen queue | All branches (local + reporting currency) | Branch | Own shift |
| Sales by day/hour/session/channel/order type | Yes | Yes | No |
| Item sales, top/slow movers, category mix | Yes | Yes | No |
| Payment method & terminal collection, settlement reconciliation | Yes | Yes | Own counter |
| Tax report per jurisdiction: GST (CGST/SGST/IGST + HSN), VAT by rate, US sales tax by state/county/city, AU GST (BAS-ready) | Yes | Yes | No |
| Fiscal exports (e.g. DSFinV-K for Germany) | Yes | Yes | No |
| Discounts, voids, refunds with user and reason | Yes | Yes | No |
| Tips and tip allocation per staff | Yes | Yes | Own |
| Kitchen performance | Yes | Yes | No |
| Staff performance | Yes | Yes | No |
| Shift / Z-report, cash variance | Yes | Yes | Own shift |
| Kiosk/QR adoption, upsell conversion, ad impressions | Yes | Yes | No |

Consolidated reports convert each branch's local currency to the tenant reporting currency using the daily exchange
rate stored with the report date; local-currency figures are always shown alongside. Reports are filterable, exportable
(Excel/PDF/CSV) and schedulable.

### 9.2 Role-permission matrix

| Screen | Rest. Admin | Branch Admin | Billing | Waiter | Kitchen |
| --- | --- | --- | --- | --- | --- |
| Dashboard | All branches | Branch | – | – | – |
| New Order | View | Yes | Yes | Yes (tables) | – |
| Orders | All branches | Yes | Yes | Own tables | Read |
| Billing | View | Yes | Yes | Request bill | – |
| Kitchen / KDS | View | Yes | – | – | Yes |
| Menu | Master menu | Branch menu | View | View | Availability |
| Branch QR | Yes | Yes | – | – | – |
| Staff accounts | Branch managers | Branch staff | – | – | – |
| Settings | Yes | Yes (within country guards) | – | – | – |
| Legal entities, tax profiles | Yes | View | – | – | – |
| Kiosk content / ads | Yes | Branch override | – | – | – |
| Void / discount above limit | Yes | Yes | Request approval | – | – |

Permissions are configurable role → permission mappings so tenants can add custom roles later.

### 9.3 Core data entities

Region, Country, CountryPack, Tenant, LegalEntity, TaxRegistration, Subscription, Plan, Branch, BranchAddress, User,
Role, Permission, UserBranchMap, Device, Counter, PaymentTerminal, FiscalDevice, Printer, Table, Section,
KitchenStation, BusinessHours, Holiday, TaxProfile, TaxRule, TaxCategory, ChargeComponent, TipSetting, Session,
Category, MenuItem, MenuItemTranslation, ItemVariant, AddonGroup, Addon, Allergen, ItemSuggestion, Combo, DailySpecial,
Order, OrderLine, OrderLineAddon, OrderLineTax, OrderStatusLog, KOT, Bill, BillCharge, BillTax, FiscalRecord, Payment,
Tip, Refund, Shift, CashMovement, ExchangeRate, MediaAsset, Campaign, Notification, Consent, AuditLog.

---

## 10. Non-functional requirements

| Area | Requirement |
| --- | --- |
| Performance | Menu load < 2 s on kiosk/QR over 4G; order to KDS < 2 s; billing actions < 1 s |
| Availability | 99.5% monthly per region; POS and KDS keep working offline on the local network and sync later (fiscal countries: per local outage rules) |
| Regional hosting | One deployment cell per region (India, EU, US, AU); each branch's data in its country's cell, tenant profile and aggregates in the home region (RG-18); global directory for login routing |
| Real-time | WebSocket/push for KDS, status, notifications |
| Scalability | 1,000 tenants, 5,000 branches, 50 orders/min per branch at peak |
| Multi-tenancy | Strict tenant isolation in every query and file path |
| Security | HTTPS, bcrypt/argon2 passwords, JWT + refresh tokens, server-side role checks, rate limiting, audit log; card data never stored (PCI-DSS via providers; SAQ A/P2PE scope) |
| Devices | Kiosk lock-down, remote status (online, printer paper, last sync), remote logout, OTA updates |
| Accessibility | WCAG 2.1 AA for QR web and admin; kiosk to EN 301 549 / ADA guidance |
| Localisation | All UI text externalised; locale-aware formatting; right-to-left not required in v1 |
| Time | UTC storage; branch time zone for business logic |
| Backup | Daily, 30-day retention, point-in-time restore, backups kept in the tenant's region |
| Retention | Financial and fiscal records kept per country rule (e.g. India 8 years, Germany 10 years, US/AU 5–7 years); configurable per country pack |

---

## 11. Integrations

| Integration | Purpose | Region | Phase |
| --- | --- | --- | --- |
| Stripe (Payments, Terminal, Connect, Tax) | Online, kiosk/QR, card-present, tips, refunds, payouts per legal entity | DE, UK, FR, US, AU | 1 |
| Adyen (Checkout, Terminal API, for Platforms) | Same as Stripe, alternative per tenant | DE, UK, FR, US, AU | 1 |
| Razorpay / PayU / Pine Labs (existing) | UPI, cards, EDC terminals | IN | 1 |
| Cloud TSE provider (e.g. fiskaly) | German fiscal signatures, DSFinV-K | DE | Before DE go-live |
| FR certification body (Infocert NF525 or LNE) | Certification of the FR fiscal module | FR | Before FR go-live |
| Tax-rate provider (Avalara / TaxJar / Stripe Tax) | US jurisdiction rates by address | US | 1 |
| EU VIES | VAT number validation | EU | 1 |
| India e-invoice IRP | IRN/QR on B2B invoices | IN | 2 |
| Thermal printers (ESC/POS 58/80 mm) | Bills, KOTs, tokens | All | 1 |
| SMS / WhatsApp / email | Credentials, e-receipts, alerts | All | 1 |
| Accounting export (Tally, Zoho Books, Xero, QuickBooks, Sage, DATEV) | Daily sales journal | Per country | 2 |
| Aggregators / ONDC | Delivery orders into KDS | Per country | 3 |
| Inventory / ERP | Stock by recipe | All | 3 |

---

## 12. Impact on the current codebase (gap list, not yet implemented)

The existing `backend` (dinein) and Flutter apps were built India-first. Items found that block day-1 multi-region:

| Area | Current state | Required change |
| --- | --- | --- |
| Tax | `gst_percent` on menu items/order lines, `prices_include_gst`, `gstin` columns in `backend/src/main/resources/db/migration/V1__init.sql` | Replace with tax category + tax profile/rules (RG-04/05/06); keep GST as the India rule set; per-component tax lines on orders |
| Currency | `currency CHAR(3) DEFAULT 'INR'` | No default; required from branch; minor-unit amounts (RG-03) |
| Tenancy | Restaurant-level only (no branch / legal entity layer, per [kioski.md](../kioski.md)) | Add LegalEntity and Branch with country pack fields (RG-01/02) |
| Payments | Razorpay, PayU, Pine Labs behind `PaymentGateway` SPI | Keep SPI; add Stripe and Adyen adapters (online + terminal + platform payouts); generic payment terminal record (RG-08) |
| Fiscal | None | Fiscal adapter SPI (RG-07); DE cloud-TSE adapter; FR certified fiscal module; no-op adapter for IN/UK/US/AU |
| Multi-country tenant | Single restaurant, single currency | Legal entities, per-country master menu, branch-pinned data, consolidated FX reporting (RG-21) |
| Formatting | India-specific strings and ₹ found in ~37 backend files, 9 kiosk-app files, 7 counter-app files | Locale-driven formatting and translations (RG-10) |
| Time | Check for IST/`Asia/Kolkata` assumptions | Branch time zone everywhere (RG-11) |
| Hosting | Single deployment | Regional cells + tenant directory (RG-18) |

---

## 13. Gap analysis, open questions and phasing

### 13.1 Gaps found in the original brief (all now covered above)

| # | Gap | Section |
| --- | --- | --- |
| 1 | Platform Super Admin, plans, subscriptions, device registry | §3, §4 |
| 2 | Tax identity and legal invoices — now per country (GSTIN, VAT, EIN/permits, ABN) | §2, §5.2 |
| 3 | `$` shown for an Indian branch; currency must come from the branch | §7.5 |
| 4 | Tables, sections, kitchen stations | §5.5 |
| 5 | Categories, add-ons, combos, allergens, nutrition | §6 |
| 6 | Order status model, cancel/void rules (fiscal-safe) | §7.2 |
| 7 | Split bill/payment, discounts with approval, refunds, tips, rounding | §7.5 |
| 8 | Shifts, cash float, Z-report | §7.5 |
| 9 | Bills left open for 155 h in the reference screen — day-end auto-close | §5.5 |
| 10 | Printers and offline mode | §5.5, §10 |
| 11 | Kiosk idle, languages, accessibility, ad campaigns | §8 |
| 12 | Reports incl. per-jurisdiction tax and settlement | §9.1 |
| 13 | Configurable permissions, audit log | §9.2 |
| 14 | Security: password policy, PIN, lockout | §3 |
| 15 | Duplicate username `counter1` | §3 |
| 16 | Privacy consent and service-charge rules | §2.2 |
| 17 | **Multi-region**: currency, tax engine, fiscal laws, payments, tips, formats, time zones, data residency, legal content | §2 |

### 13.2 Open questions

- [x] **Q1** European launch countries: **Germany, UK, France** (decided 2026-11-25).
- [x] **Q5** Payment provider: **Stripe or Adyen** (decided 2026-11-25).
- [x] **Q5a** Stripe or Adyen: **each restaurant picks one** (decided 2026-11-25); both adapters are built → RG-08.
- [ ] **Q5b** Can Stripe or Adyen serve Indian merchants with UPI? Until confirmed, India branches keep Razorpay/PayU/Pine Labs.
- [x] **Q12** One restaurant runs branches in more than one country: **yes** (decided 2026-11-25) → RG-21.
- [ ] **Q13** France certification route: NF525 (Infocert) or LNE, and the budget/timeline for it.
- [ ] **Q14** Germany TSE provider choice.
- [ ] **Q2** Launch pricing model per region: per-branch subscription, kiosk rental, transaction fee or hybrid?
- [ ] **Q3** Does DineHub supply certified kiosk hardware in each region, or do restaurants bring their own?
- [ ] **Q4** Can a branch run prepaid and postpaid by order type (e.g. dine-in postpaid, takeaway prepaid)?
- [ ] **Q6** US tax rates: buy a tax-rate provider or maintain rates in-house?
- [ ] **Q7** Can branches override master-menu prices?
- [ ] **Q8** Should QR table orders need waiter confirmation?
- [ ] **Q9** Which languages per launch country?
- [ ] **Q10** Do temple branches need donation/prasadam items with different tax treatment?
- [ ] **Q11** Hosting provider and regions for the four cells?

### 13.3 Phasing

1. **Phase 1 — Multi-region foundation + India pilot (Madhapur):** country packs for IN, DE, UK, FR, US, AU; legal entity and branch layer (multi-country tenants); tax engine; money/currency; locale and time zone; payment SPI with India providers + Stripe and Adyen; fiscal adapter SPI; tenant/branch onboarding, staff, settings, menu planner, counter POS, QR, KDS, billing, basic reports. **Start the France certification and German TSE provider selection in this phase** (long lead times).
2. **Phase 2 — Kiosk + go-lives without fiscal devices (UK, US, AU):** self-order kiosk with branding and ads, waiter app, add-ons/combos, split bills, tips, shifts/Z-report, subscriptions and device registry, accessibility, EU/US/AU hosting cells.
3. **Phase 3 — Germany and France go-live:** DE cloud-TSE adapter + DSFinV-K; FR certified fiscal module and certificate; then ecosystem (aggregators, loyalty, inventory, accounting exports, reservations, third-party ads).

Compliance obligations for every country, with controls, evidence and review dates, are tracked in
[COMPLIANCE.md](COMPLIANCE.md). No country may go live while any of its "Go-live blocker" items there are open.

---

## 14. Change log

| Version | Date | Change |
| --- | --- | --- |
| 0.1 | 2026-11-25 | First BRD, India only (Claude Docs draft) |
| 0.2 | 2026-11-25 | Added multi-region scope (India, Europe, USA, Australia) from day 1: §2 regional strategy and country packs, legal entities, tax engine, fiscal adapters, payments per region, tips, formats, data residency, codebase gap list (§12). Moved BRD into the repo as `docs/BRD.md`. |
| 0.3 | 2026-11-25 | Europe launch = Germany, UK, France; payment provider = Stripe or Adyen; multi-country tenants confirmed (RG-21); branch-pinned data residency (RG-18); §2.4 fiscal laws for DE/FR/UK; §2.5 VAT behaviour; phasing updated; compliance register split out to `docs/COMPLIANCE.md`. |
| 0.4 | 2026-11-25 | Q5a decided: each restaurant picks Stripe or Adyen at onboarding (RG-08, §5.1); added Q5b on India support. |
