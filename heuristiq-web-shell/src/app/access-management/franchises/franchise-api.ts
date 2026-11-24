import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { Observable, catchError, map, of, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FranchiseSearchApiService } from '../../shared/services/franchise-search-api.service';
import { UamCommonRequest, UamCommonResponse } from '../../shared/models/uam.models';
import { packageQueryString } from './franchise-packages.helpers';
import { NAMED_PLAN_PATHS } from './franchise-packages.constants';
import {
  NamedPlanKind, NamedPlanRow, PackageTemplate, PackageTemplatePage, PackageTemplateQuery, PackageTemplateRequest,
} from './franchise-packages.models';
import { appliedRulesQueryString, namedPlanQueryString } from './franchise-named-plans.helpers';
import { earningsQuery } from './franchise-earnings.helpers';
import { IDEMPOTENCY_KEY_HEADER } from './franchise-earnings.constants';
import { locationQueryString, planQueryString } from './franchise-locations.helpers';
import { demandQueryString } from './franchise-demand.helpers';
import { DemandQuery, PlaceDemand } from './franchise-demand.models';
import { FranchiseHierarchy } from './franchise-hierarchy.models';
import { EVENT_TYPES_PATH, RULE_NOT_IN_FORCE_STATUS, RULE_RESOLVE_PATH, RuleInForceKind } from './franchise-rules-in-force.constants';
import { eventTypesQueryString, ruleResolveQueryString } from './franchise-rules-in-force.helpers';
import { EventTypeOption, RuleResolution } from './franchise-rules-in-force.models';
import { DriverSubscriptionPlanOption, DriverSubscriptionResult } from './driver-subscription.models';
import {
  AddFranchiseAgentRequest, EarningsEntry, EarningsFilter, Location, LocationImportResult, LocationQuery, LocationRequest, LocationStatusRequest, PlanQuery, PlanResolution, FranchiseMembers, MyEarnings, PageResult, PayoutRequestBody, WalletPayout, Franchise, FranchiseAgent, FranchiseApplicationTerms, FranchisePlan, FranchisePlanRequest, FranchisePlans,
  OpenFranchiseRequest, FranchiseStatus, FranchiseDriver,
} from './franchise.models';

/**
 * The endpoints of Rider_FranchiseService (gateway /api/v1/franchise/**; they used to be served by UAM under
 * /api/v1/uam/franchises). Same request and response envelope as UAM; the organization and the caller always come from the
 * sign-in token.
 */
@Injectable({ providedIn: 'root' })
export class FranchiseApi {
  private readonly base = environment.franchiseApiUrl;

  constructor(private http: HttpClient, private translate: TranslateService) {}

  /** Every franchise of the organization (administrators). */
  list(): Observable<Franchise[]> {
    return this.get<Franchise[]>('');
  }

  /** One franchise of the organization (administrators). */
  byId(franchiseId: string): Observable<Franchise> {
    return this.get<Franchise>(`/${encodeURIComponent(franchiseId)}`);
  }

  /** Where a franchise sits in the hierarchy: its parent, the franchises directly under it, and everything below it (administrators). */
  hierarchy(franchiseId: string): Observable<FranchiseHierarchy> {
    return this.get<FranchiseHierarchy>(`/${encodeURIComponent(franchiseId)}/hierarchy`);
  }

  /** Puts a franchise under another active franchise, or at the top when the parent is empty (administrators). */
  setParent(franchiseId: string, parentFranchiseId: string): Observable<FranchiseHierarchy> {
    return this.http.put<UamCommonResponse<FranchiseHierarchy>>(`${this.base}/${encodeURIComponent(franchiseId)}/parent`,
      this.wrap({ parentFranchiseId })).pipe(map((res) => this.unwrap(res)));
  }

  /** Suspends, reactivates or closes a franchise (administrators); a closed franchise cannot be reopened. */
  setStatus(franchiseId: string, status: FranchiseStatus): Observable<Franchise> {
    return this.http.put<UamCommonResponse<Franchise>>(`${this.base}/${encodeURIComponent(franchiseId)}/status`, this.wrap({ status }))
      .pipe(map((res) => this.unwrap(res)));
  }

  /** Takes an agent out of a franchise the signed-in person owns; the place becomes free. */
  removeAgent(franchiseId: string, memberId: string): Observable<unknown> {
    return this.remove(`/${encodeURIComponent(franchiseId)}/agents`, encodeURIComponent(memberId));
  }

  /** Takes an agent out of any franchise of the organization (administrators); the place becomes free. */
  adminRemoveAgent(franchiseId: string, memberId: string): Observable<unknown> {
    return this.remove(`/admin/${encodeURIComponent(franchiseId)}/agents`, encodeURIComponent(memberId));
  }

  /** Adds an agent to any franchise of the organization (administrators), while the chosen application has a place free. */
  adminAddAgent(franchiseId: string, request: AddFranchiseAgentRequest): Observable<FranchiseAgent> {
    return this.create<AddFranchiseAgentRequest, FranchiseAgent>(`/admin/${encodeURIComponent(franchiseId)}/agents`, request);
  }

  /** The preconfigured agent places and fees per application, country and state, and the default currency. */
  plans(): Observable<FranchisePlans> {
    return this.get<FranchisePlans>('/plans');
  }

  /** The current source record for editing; list/search rows can be stale or partial. */
  plan(planId: string): Observable<FranchisePlan> {
    return this.get<FranchisePlan>(`/plans/${encodeURIComponent(planId)}`);
  }

  /** One page of the organization's plans, narrowed by the filter (server paging and filtering). */
  plansPage(query: PlanQuery): Observable<FranchisePlans> {
    return this.get<FranchisePlans>(`/plans${planQueryString(query)}`);
  }

  /** The plan that applies to a place (or the application's general plan without a place) and the rule that matched. */
  resolvePlan(applicationCode: string, locationId?: string): Observable<PlanResolution> {
    const params = new URLSearchParams({ applicationCode });
    if (locationId) { params.set('locationId', locationId); }
    return this.get<PlanResolution>(`/plans/resolve?${params.toString()}`);
  }

  /** The shared places reference, one page. */
  locations(query: LocationQuery): Observable<PageResult<Location>> {
    return this.get<PageResult<Location>>(`/locations${locationQueryString(query)}`);
  }

  location(id: string): Observable<Location> {
    return this.get<Location>(`/locations/${encodeURIComponent(id)}`);
  }

  /** The places directly under a place (only usable ones unless {@code includeInactive}). */
  locationChildren(id: string, query: LocationQuery = {}): Observable<PageResult<Location>> {
    return this.get<PageResult<Location>>(`/locations/${encodeURIComponent(id)}/children${locationQueryString(query)}`);
  }

  /** The places a franchise can cover (usable only), for the franchise form's picker: needs no place administration privilege. */
  placeOptions(query: LocationQuery): Observable<PageResult<Location>> {
    return this.get<PageResult<Location>>(`/place-options${locationQueryString(query)}`);
  }

  /** The usable places directly under a place, for the franchise form's picker. */
  placeOptionChildren(id: string, query: LocationQuery = {}): Observable<PageResult<Location>> {
    return this.get<PageResult<Location>>(`/place-options/${encodeURIComponent(id)}/children${locationQueryString(query)}`);
  }

  /** Demand votes per place for one application: the places under a parent (top places without one), most votes first. */
  demandSummary(query: DemandQuery): Observable<PageResult<PlaceDemand>> {
    return this.get<PageResult<PlaceDemand>>(`/demand-votes/summary${demandQueryString(query)}`);
  }

  addLocation(request: LocationRequest): Observable<Location> {
    return this.create<LocationRequest, Location>('/locations', request);
  }

  changeLocation(id: string, request: LocationRequest): Observable<Location> {
    return this.update<LocationRequest, Location>('/locations', id, request);
  }

  setLocationActive(id: string, request: LocationStatusRequest): Observable<Location> {
    return this.update<LocationStatusRequest, Location>('/locations', `${id}/status`, request);
  }

  /** Imports places from CSV text; {@code dryRun} only checks. HTTP 200 with row errors is a normal answer. */
  importLocations(csv: string, dryRun: boolean): Observable<LocationImportResult> {
    return this.create<{ csv: string; dryRun: boolean }, LocationImportResult>('/locations/imports', { csv, dryRun });
  }

  addPlan(request: FranchisePlanRequest): Observable<FranchisePlan> {
    return this.create<FranchisePlanRequest, FranchisePlan>('/plans', request);
  }

  changePlan(planId: string, request: FranchisePlanRequest): Observable<FranchisePlan> {
    return this.update<FranchisePlanRequest, FranchisePlan>('/plans', planId, request);
  }

  deletePlan(planId: string): Observable<unknown> {
    return this.remove('/plans', planId);
  }

  /** One page of the organization's package templates (section 23 of the franchise API). */
  packageTemplates(query: PackageTemplateQuery): Observable<PackageTemplatePage> {
    return this.get<PackageTemplatePage>(`/package-templates${packageQueryString(query)}`);
  }

  packageTemplate(id: string): Observable<PackageTemplate> {
    return this.get<PackageTemplate>(`/package-templates/${encodeURIComponent(id)}`);
  }

  addPackageTemplate(request: PackageTemplateRequest): Observable<PackageTemplate> {
    return this.create<PackageTemplateRequest, PackageTemplate>('/package-templates', request);
  }

  /** Replaces a template; also how it is switched on or off. The request carries the version last read. */
  changePackageTemplate(id: string, request: PackageTemplateRequest): Observable<PackageTemplate> {
    return this.update<PackageTemplateRequest, PackageTemplate>('/package-templates', id, request);
  }

  /** The named (scope PLAN) fee schedules, commission plans or referral plans of one application, every version, for a picker. */
  namedPlans(kind: NamedPlanKind, applicationCode: string): Observable<NamedPlanRow[]> {
    return this.get<PageResult<NamedPlanRow>>(`${NAMED_PLAN_PATHS[kind]}${namedPlanQueryString(applicationCode)}`)
      .pipe(map((page) => page?.items ?? []));
  }

  /** Every plan row of one application (all levels), to show what a franchise follows when its package picks no plan. */
  appliedRules(kind: NamedPlanKind, applicationCode: string): Observable<NamedPlanRow[]> {
    return this.get<PageResult<NamedPlanRow>>(`${NAMED_PLAN_PATHS[kind]}${appliedRulesQueryString(applicationCode)}`)
      .pipe(map((page) => page?.items ?? []));
  }

  /**
   * The commission or referral rule in force today for one application of a franchise, and the scope it comes from (its own plan,
   * the named plan its package linked, or an organization rule); null when none is in force (404). Read live, never from the index.
   */
  ruleInForce(kind: RuleInForceKind, franchiseId: string, applicationCode: string, eventType?: string): Observable<RuleResolution | null> {
    return this.getLive<RuleResolution>(`${RULE_RESOLVE_PATH}${ruleResolveQueryString(kind, franchiseId, applicationCode, eventType)}`).pipe(
      catchError((err: { status?: number }) => (err?.status === RULE_NOT_IN_FORCE_STATUS ? of(null) : throwError(() => err))),
    );
  }

  /** The active event types of one application (the organization's catalogue). */
  eventTypes(applicationCode: string): Observable<EventTypeOption[]> {
    return this.getLive<EventTypeOption[]>(`${EVENT_TYPES_PATH}${eventTypesQueryString(applicationCode)}`).pipe(map((rows) => rows ?? []));
  }

  /**
   * Switches an application of a franchise to another package template, or to none (null): from now on it follows that package's
   * plans. The answer is the franchise as it is now.
   */
  changePackage(franchiseId: string, applicationCode: string, templateId: string | null): Observable<Franchise> {
    return this.http.put<UamCommonResponse<Franchise>>(
      `${this.base}/${encodeURIComponent(franchiseId)}/applications/${encodeURIComponent(applicationCode)}/package`,
      this.wrap({ templateId })).pipe(map((res) => this.unwrap(res)));
  }

  /** The franchises the signed-in person owns. */
  mine(): Observable<Franchise[]> {
    return this.get<Franchise[]>('/mine');
  }

  open(request: OpenFranchiseRequest): Observable<Franchise> {
    return this.create<OpenFranchiseRequest, Franchise>('', request);
  }

  addApplication(franchiseId: string, terms: FranchiseApplicationTerms): Observable<Franchise> {
    return this.create<FranchiseApplicationTerms, Franchise>(`/${franchiseId}/applications`, terms);
  }

  /** New agent places and fee of an application the franchise already has. */
  updateApplication(franchiseId: string, terms: FranchiseApplicationTerms): Observable<Franchise> {
    return this.update<Omit<FranchiseApplicationTerms, 'applicationCode'>, Franchise>(
      `/${franchiseId}/applications`, terms.applicationCode,
      { agentLimit: terms.agentLimit, fee: terms.fee, ...(terms.locationId ? { locationId: terms.locationId } : {}) });
  }

  /** The owner asks the organization's administrator for more agent places in one application. */
  requestMoreAgents(franchiseId: string, applicationCode: string, agents: number, note: string): Observable<unknown> {
    return this.create<{ agents: number; note: string }, unknown>(
      `/${franchiseId}/applications/${applicationCode}/more-agents`, { agents, note });
  }

  agents(franchiseId: string): Observable<FranchiseAgent[]> {
    return this.get<FranchiseAgent[]>(`/${franchiseId}/agents`);
  }

  addAgent(franchiseId: string, request: AddFranchiseAgentRequest): Observable<FranchiseAgent> {
    return this.create<AddFranchiseAgentRequest, FranchiseAgent>(`/${franchiseId}/agents`, request);
  }

  /** The members that use a franchise's places, with the places per application; only the franchise's owner may read them. */
  members(franchiseId: string, applicationCode: string, page: number, size: number): Observable<FranchiseMembers> {
    const params = new URLSearchParams();
    if (applicationCode) { params.set('applicationCode', applicationCode); }
    params.set('page', String(page));
    params.set('size', String(size));
    return this.get<FranchiseMembers>(`/${encodeURIComponent(franchiseId)}/members?${params.toString()}`);
  }

  /**
   * One page of the ACTIVE drivers of a franchise the signed-in person owns (404 for anyone else). Read live from
   * Rider_FranchiseService, not the search index: driver links are not indexed when they change.
   */
  drivers(franchiseId: string, page: number, size: number): Observable<PageResult<FranchiseDriver>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    return this.getLive<PageResult<FranchiseDriver>>(`/${encodeURIComponent(franchiseId)}/drivers?${params.toString()}`);
  }

  /** One page of the ACTIVE drivers of any franchise of the organization, as an administrator reads them (live, as above). */
  adminDrivers(franchiseId: string, page: number, size: number): Observable<PageResult<FranchiseDriver>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    return this.getLive<PageResult<FranchiseDriver>>(`/admin/${encodeURIComponent(franchiseId)}/drivers?${params.toString()}`);
  }

  /**
   * The franchises with their current places used, read live (the driver application's `driverCount` is only current here):
   * every franchise of the organization for an administrator, else the signed-in person's own.
   */
  livePlaces(orgWide: boolean): Observable<Franchise[]> {
    return this.getLive<Franchise[]>(orgWide ? '' : '/mine');
  }

  /** The subscription plans open to a driver, as an administrator reads them (live). */
  adminDriverPlans(driverUserId: string): Observable<DriverSubscriptionPlanOption[]> {
    return this.getLive<DriverSubscriptionPlanOption[]>(`/driver-subscriptions/drivers/${encodeURIComponent(driverUserId)}/plans`);
  }

  /** An administrator subscribes a driver to a plan: the same rules and first charge as when the driver subscribes in the app. */
  adminSubscribeDriver(driverUserId: string, planId: string): Observable<DriverSubscriptionResult> {
    return this.create<{ driverUserId: string; planId: string }, DriverSubscriptionResult>('/driver-subscriptions', { driverUserId, planId });
  }

  /** The subscription plans open to one of the owner's drivers (404 when the franchise or the driver is not theirs). */
  ownerDriverPlans(franchiseId: string, driverUserId: string): Observable<DriverSubscriptionPlanOption[]> {
    return this.getLive<DriverSubscriptionPlanOption[]>(
      `/${encodeURIComponent(franchiseId)}/drivers/${encodeURIComponent(driverUserId)}/subscription-plans`);
  }

  /** The franchise owner subscribes one of their drivers to a plan. */
  ownerSubscribeDriver(franchiseId: string, driverUserId: string, planId: string): Observable<DriverSubscriptionResult> {
    return this.create<{ planId: string }, DriverSubscriptionResult>(
      `/${encodeURIComponent(franchiseId)}/drivers/${encodeURIComponent(driverUserId)}/subscription`, { planId });
  }

  /** The members of any franchise of the organization, as an administrator reads them. */
  adminMembers(franchiseId: string, applicationCode: string, page: number, size: number): Observable<FranchiseMembers> {
    const params = new URLSearchParams();
    if (applicationCode) { params.set('applicationCode', applicationCode); }
    params.set('page', String(page));
    params.set('size', String(size));
    return this.get<FranchiseMembers>(`/admin/${encodeURIComponent(franchiseId)}/members?${params.toString()}`);
  }

  /** The signed-in person's wallet entries of one earnings account (agent, or a franchise they own), newest first. */
  myEntries(filter: EarningsFilter, page: number, size: number): Observable<PageResult<EarningsEntry>> {
    return this.get<PageResult<EarningsEntry>>(`/ledger/mine/entries${earningsQuery(filter, page, size)}`);
  }

  /** The signed-in person's earnings accounts from the wallet: settled, pending, reserved and available. */
  myEarnings(): Observable<MyEarnings> {
    return this.get<MyEarnings>('/ledger/mine/statement');
  }

  /** The signed-in person's wallet payouts of one earnings account, newest first. */
  myPayouts(filter: EarningsFilter, page: number, size: number): Observable<PageResult<WalletPayout>> {
    return this.get<PageResult<WalletPayout>>(`/payouts/mine${earningsQuery({ account: filter.account, franchiseId: filter.franchiseId }, page, size)}`);
  }

  /** Asks the wallet (through Franchise) for a payout; the same key makes a repeat answer the same payout. */
  requestPayout(request: PayoutRequestBody, idempotencyKey: string): Observable<WalletPayout> {
    return this.http.post<UamCommonResponse<WalletPayout>>(`${this.base}/payouts`, this.wrap(request), {
      headers: { [IDEMPOTENCY_KEY_HEADER]: idempotencyKey },
    }).pipe(map((res) => this.unwrap(res)));
  }

  private wrap<T>(payload: T): UamCommonRequest {
    return { requestObject: payload, doesInputHaveEncryptedData: false, success: false, pageNo: 0, pageSize: 0, useOrOperator: false };
  }

  private unwrap<T>(res: UamCommonResponse<T>): T {
    if (!res.success) {
      const fallback = this.translate.instant('shared.errors.requestFailed');
      throw { error: { detail: res.message || fallback, message: res.message || fallback } };
    }
    return res.responseObject as T;
  }

  private get<T>(path: string): Observable<T> {
    const indexed = new FranchiseSearchApiService(this.http).readSource<T>(path);
    if (indexed) return indexed;
    return this.getLive<T>(path);
  }

  /** Reads straight from Rider_FranchiseService, never from the search index. */
  private getLive<T>(path: string): Observable<T> {
    return this.http.get<UamCommonResponse<T>>(`${this.base}${path}`).pipe(map((res) => this.unwrap(res)));
  }

  private create<Req, Res>(path: string, body: Req): Observable<Res> {
    return this.http.post<UamCommonResponse<Res>>(`${this.base}${path}`, this.wrap(body)).pipe(map((res) => this.unwrap(res)));
  }

  private update<Req, Res>(path: string, id: string, body: Req): Observable<Res> {
    return this.http.put<UamCommonResponse<Res>>(`${this.base}${path}/${id}`, this.wrap(body)).pipe(map((res) => this.unwrap(res)));
  }

  private remove(path: string, id: string): Observable<unknown> {
    return this.http.delete<UamCommonResponse<unknown>>(`${this.base}${path}/${id}`).pipe(map((res) => this.unwrap(res)));
  }
}
