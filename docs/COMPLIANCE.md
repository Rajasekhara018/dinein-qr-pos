# DineHub — Compliance & Regulations Register

| Item | Value |
| --- | --- |
| Version | 0.1 |
| Date | 2026-11-25 |
| Markets | India (IN), Germany (DE), United Kingdom (UK), France (FR), USA (US), Australia (AU) |
| Requirements source | [BRD.md](BRD.md) — section references below point there |
| Status | Draft; no item has had legal review yet |

> This register lists every law, regulation and standard DineHub must meet in each market, the product control that
> meets it, the evidence that proves it, and when it is next reviewed. It is **not legal advice**: each country's
> entries need sign-off from a local tax/legal advisor before that country goes live.

---

## 1. How this register is maintained

### 1.1 Roles

| Role | Responsibility |
| --- | --- |
| Compliance owner (to be named) | Owns this file; runs the reviews; gets legal sign-off; decides go-live readiness per country |
| Country advisor (external, one per country) | Confirms rates, thresholds and interpretations; signs off the country section |
| Engineering lead | Maps each obligation to a control in code/config; keeps evidence links current |
| Product owner | Updates [BRD.md](BRD.md) when an obligation changes a requirement |

### 1.2 Process

1. **Monitor** — check the regulatory watchlist (§10) monthly, and subscribe to the tax authority, data-protection authority and payment-provider bulletins for each market.
2. **Assess** — for each change: affected countries, affected features, effective date, whether it is a go-live blocker.
3. **Change** — update this register; update the BRD (requirement) and the country pack configuration (rates, thresholds, legal text) with an **effective-from date**. Rates and thresholds are never hard-coded.
4. **Verify** — add or update the automated test for the control (e.g. tax calculation, receipt fields), and attach evidence.
5. **Sign off** — country advisor approves; record the date in the country section.
6. **Record** — add a line to the change log (§12).

### 1.3 Review cadence

| Trigger | Action |
| --- | --- |
| Monthly | Watchlist check (§10) |
| Quarterly | Review every item marked Partial or Not started; refresh evidence |
| Annually per country | Full country review with the advisor; tax rates and holiday calendars rolled forward |
| Before a country go-live | All "Go-live blocker = Yes" items for that country must be **Done** with evidence |
| Major product release | Re-check fiscal (DE/FR), payments (PCI) and accessibility items |
| Incident or authority request | Ad-hoc review of the affected items |

### 1.4 Status values

`Not started` · `In progress` · `Partial` (control exists, evidence missing) · `Done` · `N/A`

---

## 2. Shared responsibility

DineHub provides the tools; the restaurant (merchant) remains the legal taxpayer and food business operator.

| Area | DineHub (platform) | Restaurant (tenant) |
| --- | --- | --- |
| Tax calculation & receipts | Correct calculation from configured rules; legally complete receipts; tax reports | Choosing correct tax categories for items; filing returns; paying tax |
| Fiscal devices (DE/FR) | Integration with TSE; certified FR software; exports for audits | Registering registers/TSE with the tax office (DE); keeping archives available; using the system as certified |
| Payments | PCI-compliant integration through Stripe/Adyen/Indian providers; no card data stored | Merchant agreement with the provider; KYC; chargeback handling |
| Food information | Fields and display for allergens, dietary marks, nutrition, origin | Accurate data entry for every item |
| Privacy | Processor duties: security, DPA, sub-processor list, data-subject request tools, breach notice to the restaurant | Controller duties: privacy notice content, lawful basis, answering guests |
| Accessibility | Accessible kiosk/QR/admin UI | Physical placement of kiosks (height, reach, space) |
| Employment (tips) | Tip records and allocation reports | Tip policy and distribution to staff |

---

## 3. Platform-wide obligations (all markets)

| ID | Regulation / standard | Obligation | Product control | BRD | Evidence | Go-live blocker | Status | Next review |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| GL-01 | PCI DSS v4.0 | No storage of card data; reduced scope via hosted checkout and certified terminals | Stripe/Adyen hosted fields and Terminal; P2PE terminals; no PAN in logs | RG-08, §10 | SAQ (A / P2PE) per provider, network diagram | Yes | Not started | Before first card sale |
| GL-02 | Payment provider rules (Stripe, Adyen) | Platform/marketplace terms, merchant KYC, prohibited business list, tipping and surcharge rules | Restaurant picks Stripe or Adyen; Stripe Connect / Adyen for Platforms onboarding (KYC) per legal entity | RG-08 | Signed platform agreements with **both** Stripe and Adyen | Yes | Not started | 2027-02 |
| GL-03 | Information security (target ISO 27001 / SOC 2 Type II) | Access control, encryption in transit and at rest, logging, backups, vendor management | HTTPS, encrypted DB/backups, audit log, least privilege | §10 | Policies, pen-test report | No (needed for enterprise sales) | Not started | 2027-06 |
| GL-04 | WCAG 2.1 AA | Accessible QR web app, admin and kiosk UI | Accessibility mode, contrast, focus order, screen-reader labels | §8, §10 | Audit report per app | Yes for EU kiosks (EAA), recommended elsewhere | Not started | Before kiosk release |
| GL-05 | Data residency & transfers | Data kept in the branch's region; transfer safeguards where data leaves it | Regional cells, branch pinning, aggregates-only consolidation | RG-18 | Architecture doc, sub-processor list with locations | Yes | Not started | 2027-02 |
| GL-06 | Record retention | Keep financial/fiscal records for the country period; delete personal data when no longer needed | Retention settings per country pack (§11); purge jobs | §10 | Retention config + job logs | Yes | Not started | Annual |
| GL-07 | Open-source licences | Licence compliance of dependencies | Dependency licence scan in CI | — | Scan report | No | Not started | Quarterly |
| GL-08 | Consumer price transparency | Prices shown are the prices charged; all mandatory fees disclosed before ordering | Fee disclosure on menu/cart; tax-inclusive display where required | RG-06, RG-15 | Screenshots per country | Yes | Not started | Annual |

---

## 4. India (IN)

| ID | Regulation | Obligation | Product control | BRD | Evidence | Go-live blocker | Status | Next review |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| IN-01 | CGST/SGST/IGST Acts & Rules | Tax invoice with GSTIN, HSN/SAC, CGST+SGST or IGST split, place of supply, sequential gap-free number per financial year (Apr–Mar) | Tax engine (India rule set), invoice series per legal entity | RG-04, RG-09 | Sample invoices, tests | Yes | Partial (GST exists in code, single-country) | 2027-04 |
| IN-02 | GST rate notifications | Restaurant service rate (5% without ITC; 18% in specified premises) | Dated tax rules | §2.2 | Advisor sign-off | Yes | Not started | 2027-04 |
| IN-03 | GST e-invoicing | IRN + QR on B2B invoices for entities above the turnover threshold | IRP integration (phase 2) | §11 | IRP sandbox test | No (only for tenants above threshold) | Not started | 2027-04 |
| IN-04 | FSS Act; FSS (Labelling & Display) Regulations 2020 | FSSAI licence no. on bills/menus; veg/non-veg mark; calories and allergen info for chains above the outlet threshold | Item dietary/nutrition fields; licence on receipt | RG-17, §6 | Screenshots | Yes | Partial | 2027-04 |
| IN-05 | CCPA guidelines on service charge (2022) | Service charge cannot be compulsory or added by default | Service charge flagged optional and removable at billing | RG-15 | Billing test | Yes | Not started | 2027-04 |
| IN-06 | Legal Metrology (Packaged Commodities) | MRP and declarations on packaged goods sold | Packaged-good tax category + MRP field | §6 | — | No | Not started | 2027-04 |
| IN-07 | Digital Personal Data Protection Act 2023 + DPDP Rules | Notice and consent for guest data, purpose limitation, erasure, breach notice to the Board and affected persons | Consent capture, data-subject tools, breach runbook | RG-19 | Privacy notice, runbook | Yes | Not started | 2027-02 |
| IN-08 | CERT-In Directions (2022) | Report cyber incidents to CERT-In within 6 hours; keep ICT logs 180 days in India; NTP time sync | Incident runbook; log retention in IN region | §10 | Runbook, log config | Yes | Not started | 2027-02 |
| IN-09 | RBI payment rules (PA guidelines, data localisation) | Use RBI-authorised payment aggregators; payment data stored in India | Razorpay/PayU/Pine Labs (or Stripe/Adyen only if authorised for Indian merchants) | RG-08 | Provider authorisation | Yes | Partial | 2027-02 |
| IN-10 | Rights of Persons with Disabilities Act 2016 | Accessible ICT | GL-04 | §10 | Audit | No | Not started | 2027-06 |

Country sign-off: _pending_

---

## 5. Germany (DE)

| ID | Regulation | Obligation | Product control | BRD | Evidence | Go-live blocker | Status | Next review |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DE-01 | §146a AO, KassenSichV | Every transaction recorded and signed by a certified TSE; protected, complete, unaltered records | DE fiscal adapter with cloud TSE, one TSE client per register/kiosk | RG-07, §2.4 | TSE provider contract, integration test log | **Yes** | Not started | Before DE go-live |
| DE-02 | Belegausgabepflicht (§146a(2) AO) | Receipt offered for every sale, with TSE data (serial, transaction no., times, signature/QR) | Receipt template DE; e-receipt option | §2.4, §7.5 | Sample receipts | **Yes** | Not started | Before DE go-live |
| DE-03 | DSFinV-K | Standardised export of POS data for tax audits | DSFinV-K export per register and period | §9.1 | Export validated with provider tool | **Yes** | Not started | Before DE go-live |
| DE-04 | §146a(4) AO notification | Registers and TSEs reported to the tax office (via ELSTER) | Device registry exports the required data | §4.3 | Export sample | **Yes** (restaurant files; DineHub supplies data) | Not started | Before DE go-live |
| DE-05 | GoBD | Proper bookkeeping: immutability, traceability, 10-year retention of records | No deletion of sales; corrections as new documents; retention 10 years | §7.2, §11 | Procedure documentation (Verfahrensdokumentation) template | **Yes** | Not started | Before DE go-live |
| DE-06 | UStG (VAT) | Correct VAT rates: reduced rate for food (eat-in and takeaway), standard rate for drinks/alcohol; receipt shows rates and amounts | Tax rules DE with effective dates; order type before payment | §2.5 | Tax tests, advisor sign-off | **Yes** | Not started | Annual |
| DE-07 | LMIV (EU 1169/2011) + LMIDV | 14 allergens available for non-prepacked food | Allergen fields mandatory for DE items | RG-17 | Screenshots | **Yes** | Not started | Annual |
| DE-08 | ZZulV (additives) | Declarable additives marked on menus | Additive fields + footnotes on menu | §2.2 | Screenshots | Yes | Not started | Annual |
| DE-09 | Preisangabenverordnung (PAngV) | Final prices incl. VAT and all mandatory charges shown | Tax-inclusive display | RG-06 | Screenshots | **Yes** | Not started | Annual |
| DE-10 | GDPR + BDSG; TTDSG/TDDDG | Lawful processing, DPA with restaurants, records of processing, cookie consent on QR web app, 72-hour breach notice | Privacy tools, consent banner, DPA template | RG-19 | DPA, RoPA, consent screenshots | **Yes** | Not started | 2027-02 |
| DE-11 | BFSG (European Accessibility Act) | Accessible self-service/payment terminals and e-commerce services | GL-04; certified accessible payment terminals | §8 | Accessibility statement | **Yes** for kiosks | Not started | Before kiosk release DE |
| DE-12 | PSD2 SCA | Strong customer authentication for online payments | Handled by Stripe/Adyen (3DS) | RG-08 | Provider config | Yes | Not started | 2027-02 |

Country sign-off: _pending_

---

## 6. United Kingdom (UK)

| ID | Regulation | Obligation | Product control | BRD | Evidence | Go-live blocker | Status | Next review |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UK-01 | VAT Act 1994 + HMRC notices (catering, takeaway) | 20% on eat-in and hot takeaway; most cold takeaway food zero-rated; VAT receipt on request with VAT number and rate breakdown | UK tax rules; hot/cold tax categories; order type before payment | §2.5 | Tax tests, advisor sign-off | **Yes** | Not started | Annual |
| UK-02 | Making Tax Digital | Restaurant's VAT records kept digitally and filed via compatible software | Accounting export (Xero, QuickBooks, Sage) | §11 | Export sample | No (restaurant duty; DineHub supports) | Not started | Annual |
| UK-03 | Food Information Regulations 2014 | 14 allergens available for non-prepacked food | Allergen fields mandatory | RG-17 | Screenshots | **Yes** | Not started | Annual |
| UK-04 | Natasha's Law (PPDS labelling) | Full ingredient + allergen labels on food pre-packed for direct sale | Label print with ingredients for PPDS items | §6 | Label sample | Only if tenant sells PPDS | Not started | Annual |
| UK-05 | Calorie Labelling (Out of Home Sector) Regulations 2021 | Calories on menus for businesses with 250+ employees | Calories field + display toggle | RG-17 | Screenshots | Yes for large tenants | Not started | Annual |
| UK-06 | Employment (Allocation of Tips) Act 2023 + Code of Practice | All tips passed to workers fairly; written tipping policy; records kept 3 years | Tip records, allocation report, policy text on receipt/site | RG-14 | Report sample | **Yes** if tips enabled | Not started | Annual |
| UK-07 | Consumer Protection (DMCC Act 2024), price marking | Total price incl. mandatory charges shown; discretionary service charge clearly marked | Fee disclosure; service charge marked discretionary | RG-15 | Screenshots | **Yes** | Not started | Annual |
| UK-08 | Card surcharging ban (Consumer Rights (Payment Surcharges) Regs 2012) | No surcharges on consumer card payments | Country guard blocks card surcharge | RG-15 | Test | **Yes** | Not started | Annual |
| UK-09 | UK GDPR + DPA 2018 + PECR | Lawful processing, DPA, cookie consent, 72-hour breach notice to ICO; transfers to EU hosting under adequacy | Privacy tools, consent, DPA | RG-18, RG-19 | DPA, RoPA | **Yes** | Not started | 2027-02 |
| UK-10 | Equality Act 2010 | Reasonable adjustments for disabled customers (kiosk alternative, accessible UI) | GL-04; staff-assisted ordering always available | §8 | Accessibility statement | Yes | Not started | Annual |

Country sign-off: _pending_

---

## 7. France (FR)

| ID | Regulation | Obligation | Product control | BRD | Evidence | Go-live blocker | Status | Next review |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FR-01 | CGI art. 286-I-3° bis (anti-fraud law) | POS software guarantees inalterability, security, storage, archiving; **certificate from an accredited body** (NF525 / LNE) | FR fiscal module: chained signed tickets, grand totals, closures, archives, event journal (JET), versioning | RG-07, §2.4 | Certificate | **Yes** | Not started | Before FR go-live; on each major version |
| FR-02 | CGI / BOI on VAT (TVA) | 10% food eat-in and immediate takeaway; 5.5% deferred consumption; 20% alcohol; mixed menus split | FR tax rules; combo tax split | §2.5 | Tax tests, advisor sign-off | **Yes** | Not started | Annual |
| FR-03 | Arrêté on restaurant bills ("note") and price display | Prices displayed incl. taxes and service ("prix service compris"); bill (note) issued per rules | Tax-inclusive prices; no separate service charge line in FR | RG-06, RG-15 | Screenshots, bill sample | **Yes** | Not started | Annual |
| FR-04 | Code de la consommation; AGEC law (receipts) | Price display rules; receipts not printed by default, given on request or digitally | Receipt print = "on request" default in FR; e-receipt | §8 | Config screenshot | Yes | Not started | Annual |
| FR-05 | INCO (EU 1169/2011) + décret 2015-447 | 14 allergens available for non-prepacked food, in writing | Allergen fields mandatory | RG-17 | Screenshots | **Yes** | Not started | Annual |
| FR-06 | Décret 2002-1465 as amended (meat origin) | Origin of beef, pork, poultry, lamb shown in restaurants | Meat-origin field and display | §2.2 | Screenshots | **Yes** | Not started | Annual |
| FR-07 | "Fait maison" décret 2015-505 | Correct use of homemade mark when displayed | Optional flag with legal wording | §2.2 | — | No | Not started | Annual |
| FR-08 | Titres-restaurant rules | Meal vouchers accepted within daily limits and eligible items | Meal voucher payment method with limit and eligibility | §2.2 | Test | No (commercially expected) | Not started | Annual |
| FR-09 | GDPR + Loi Informatique et Libertés; CNIL cookie guidance | Lawful processing, DPA, cookie consent, 72-hour breach notice to CNIL | Privacy tools, consent, DPA | RG-19 | DPA, RoPA | **Yes** | Not started | 2027-02 |
| FR-10 | Accessibility (EAA transposition, RGAA) | Accessible kiosk/payment terminals and digital services | GL-04 | §8 | Accessibility statement | **Yes** for kiosks | Not started | Before kiosk release FR |
| FR-11 | B2B e-invoicing reform | Businesses must receive and later issue B2B e-invoices via approved platforms | Watch item; restaurant B2C receipts not affected; B2B invoices via accounting partner | §10 watchlist | — | No | Not started | Quarterly |

Country sign-off: _pending_

---

## 8. USA (US)

| ID | Regulation | Obligation | Product control | BRD | Evidence | Go-live blocker | Status | Next review |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| US-01 | State and local sales tax laws (incl. meals taxes) | Correct combined rate by branch address; taxability of food, drinks, alcohol, tips, service charges per state | Tax-rate provider (Q6) or maintained tables; tax-exclusive pricing | RG-04, §2.2 | Rate tests per launch state | **Yes** | Not started | Quarterly |
| US-02 | FDA menu labeling (21 CFR 101.11) | Calories on menus for chains with 20+ locations; other nutrition info on request | Calories field + display | RG-17 | Screenshots | Yes for chains | Not started | Annual |
| US-03 | FALCPA + FASTER Act | Major 9 allergens (incl. sesame) available | Allergen fields | RG-17 | Screenshots | Yes | Not started | Annual |
| US-04 | ADA Title III | Accessible kiosk and web ordering | GL-04; staff-assisted alternative | §8 | Accessibility statement | **Yes** for kiosks | Not started | Annual |
| US-05 | State card-surcharge rules + card network rules | Surcharge only where legal, capped, disclosed; not on debit | Surcharge guard per state | RG-15 | Test | Yes if surcharging | Not started | Quarterly |
| US-06 | State fee-disclosure laws (e.g. California SB 478 / SB 1524) | Mandatory fees included in or clearly disclosed with prices | Fee disclosure on menu/cart | RG-15 | Screenshots | **Yes** in affected states | Not started | Quarterly |
| US-07 | FLSA tip rules + state tip laws | Tips belong to employees; tip-pool rules; records | Tip records and allocation report | RG-14 | Report | Yes if tips enabled | Not started | Annual |
| US-08 | State privacy laws (CCPA/CPRA and others) | Notice at collection, opt-out of sale/sharing, access/deletion rights | Privacy tools, notice | RG-19 | Privacy notice | **Yes** | Not started | Quarterly |
| US-09 | State breach-notification laws | Notify affected residents/regulators within state deadlines | Incident runbook | §10 | Runbook | Yes | Not started | Annual |
| US-10 | TCPA / CAN-SPAM | Consent for SMS; unsubscribe for marketing email | Consent flags; opt-out handling | RG-19 | Test | Yes | Not started | Annual |

Country sign-off: _pending_

---

## 9. Australia (AU)

| ID | Regulation | Obligation | Product control | BRD | Evidence | Go-live blocker | Status | Next review |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| AU-01 | A New Tax System (GST) Act 1999 | GST 10%; GST-free basic food where applicable; tax invoice wording, ABN; tax invoice on request above AUD 82.50 incl. GST; buyer details from AUD 1,000 | AU tax rules; receipt template | §2.2 | Tax tests, advisor sign-off | **Yes** | Not started | Annual |
| AU-02 | Australian Consumer Law (component pricing) | Single total price incl. GST and mandatory fees; surcharges (e.g. public holiday) disclosed | Tax-inclusive display; surcharge disclosure text | RG-06, RG-15 | Screenshots | **Yes** | Not started | Annual |
| AU-03 | RBA surcharging standard | Card surcharge not above cost of acceptance (rules under review) | Surcharge guard + cap | RG-15 | Test | Yes if surcharging | Not started | Quarterly (watchlist) |
| AU-04 | FSANZ Food Standards Code (PEAL) | Allergen information available | Allergen fields | RG-17 | Screenshots | Yes | Not started | Annual |
| AU-05 | State kilojoule labelling laws (e.g. NSW, VIC, QLD, SA, ACT) | kJ on menus for chains above state threshold | Energy (kJ) field + display | RG-17 | Screenshots | Yes for chains | Not started | Annual |
| AU-06 | Privacy Act 1988 (APPs) + Notifiable Data Breaches scheme | Privacy policy, cross-border disclosure (APP 8), breach assessment within 30 days and notice to OAIC | Privacy tools, AU hosting, runbook | RG-18, RG-19 | Policy, runbook | **Yes** | Not started | 2027-02 |
| AU-07 | Spam Act 2003 | Consent + unsubscribe for marketing SMS/email | Consent flags | RG-19 | Test | Yes | Not started | Annual |
| AU-08 | Disability Discrimination Act 1992 | Accessible kiosk/web ordering | GL-04 | §8 | Accessibility statement | Yes | Not started | Annual |
| AU-09 | Fair Work Act (tips/award) | Tip and service-charge handling records | Tip report | RG-14 | Report | No | Not started | Annual |

Country sign-off: _pending_

---

## 10. Regulatory watchlist

Items that are changing or under consultation. Check monthly; move into the country table when they take effect.

| Market | Topic | What to watch | Impact if adopted |
| --- | --- | --- | --- |
| DE | VAT on restaurant food | Confirm the reduced rate for food from 2026-01-01 stays in force | Tax rule update only |
| DE | B2B e-invoicing (Wachstumschancengesetz) | Mandatory issuing phases for B2B invoices; small-value invoices (≤ €250) exempt | Only for B2B invoices above the threshold |
| FR | B2B e-invoicing reform | Receiving and issuing timelines by company size | B2B invoices via approved platform |
| FR | Anti-fraud POS certification | Changes to certification scheme or accredited bodies | Re-certification |
| UK | Tipping code / DMCC fee rules | Guidance updates | Receipt and disclosure text |
| EU | European Accessibility Act enforcement | National enforcement and standards updates | Kiosk/terminal requirements |
| EU | AI Act | Only if AI features (recommendations, chat ordering) are added | Transparency duties |
| US | New state privacy laws and fee-disclosure laws | Each legislative session | Notices, fee display |
| US | Cash rounding (penny phase-out) | State rules on rounding cash totals | Cash rounding rule |
| AU | RBA review of card surcharges | Proposed ban of surcharges on debit/credit cards | Disable AU surcharge |
| IN | DPDP Rules phased commencement | Dates when consent manager, breach and other duties start | Privacy tooling |
| IN | GST rate rationalisation | Rate notifications for restaurant services | Tax rule update |

---

## 11. Record retention by country (configuration defaults)

Values to be confirmed by each country advisor; stored in the country pack, not in code.

| Record | IN | DE | UK | FR | US | AU |
| --- | --- | --- | --- | --- | --- | --- |
| Invoices / receipts / sales records | 8 years | 10 years (GoBD) | 6 years | 10 years (commercial) / 6 years (tax) | 3–7 years by state | 5 years |
| Fiscal data (TSE logs, DSFinV-K, FR archives) | n/a | 10 years | n/a | 6 years minimum | n/a | n/a |
| Tip records | — | — | 3 years | — | 3 years (FLSA payroll) | 7 years (Fair Work) |
| Security / ICT logs | 180 days in India (CERT-In) | Per security policy | Per security policy | Per security policy | Per security policy | Per security policy |
| Guest personal data (contact, consent) | Until purpose ends or erasure request | Same (GDPR) | Same (UK GDPR) | Same (GDPR) | Per state law / request | Per APPs |

---

## 12. Change log

| Version | Date | Change |
| --- | --- | --- |
| 0.1 | 2026-11-25 | First register for IN, DE, UK, FR, US, AU; maintenance process, shared responsibility, watchlist, retention defaults. |
