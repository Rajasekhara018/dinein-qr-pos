import { describe, expect, it } from 'vitest';
import { Franchise, FranchiseApplication } from './franchise.models';
import { OpenFranchiseForm, PlanForm, districtOptions, stateOptions, countryOptions, currencyOptions, emptyPlanForm, planFormError, planToForm, toPlanRequest, addAgentError, applicationsWithRoom, applyPlan, countryFromTimeZone, currencySymbol, emptyAgentRequest, emptyOpenForm,
  emptyTermsRow, formatMoney, offerCountries, openFieldErrors, openFranchiseError, placesLeft, planKey, planRegion, plansForApplication, roundFee,
  toAgentRequest, moneyText, toOpenRequest, toTerms, usagePercent, applyResolution, noPlanNote, placeNote, applicationCodeForLabel, applicationPickLabel, nextStatuses, ownerLabel, placeLabel, planPlaceType, activeInDirectCount,
  isLocationBased, placeRequiredError } from './franchises.helpers';

describe('applications sold by place', () => {
  it('needs a place for Best Ride and Royal Food only', () => {
    expect(isLocationBased('BESTRIDE_RIDER_APP')).toBe(true);
    expect(isLocationBased(' food_deliver_app ')).toBe(true);
    expect(isLocationBased('PASUPU_KUMKUMA_MATRIMONY_APP')).toBe(false);
    expect(isLocationBased('')).toBe(false);
    expect(isLocationBased(null)).toBe(false);
  });

  it('asks for a place only when the row has none and the application holds none yet', () => {
    const ride = { applicationCode: 'BESTRIDE_RIDER_APP', agentLimit: 1, fee: 0 };
    expect(placeRequiredError(ride)).toBe('accessManagement.franchises.errors.placeRequired');
    expect(placeRequiredError({ ...ride, locationId: 'w-12' })).toBeNull();
    expect(placeRequiredError(ride, 'held-already')).toBeNull();
    expect(placeRequiredError({ ...ride, applicationCode: 'PASUPU_KUMKUMA_MATRIMONY_APP' })).toBeNull();
  });
});
import { FranchisePlan, PlanResolution } from './franchise.models';

const MATRI = 'PASUPU_KUMKUMA_MATRIMONY_APP';
const RIDE = 'BESTRIDE_RIDER_APP';

function form(overrides: Partial<OpenFranchiseForm> = {}): OpenFranchiseForm {
  return {
    ownerEmail: 'ravi@example.com',
    ownerName: 'Ravi',
    ownerPhone: '',
    rows: [{ applicationCode: MATRI, agentLimit: 20, fee: 100000 }, { applicationCode: RIDE, agentLimit: 30, fee: 200000, locationId: 'w-12' }],
    ...overrides,
  };
}

const application = (code: string, limit: number, count: number): FranchiseApplication => ({ applicationCode: code, agentLimit: limit, agentCount: count, fee: { code: 'INR', value: 1 } });
const franchise = (applications: FranchiseApplication[]): Franchise => ({
  unitId: 'u1', code: 'RBM-00001', name: 'RBM-00001', ownerUserId: 'o1', status: 'ACTIVE',
  totalAgentLimit: applications.reduce((sum, a) => sum + a.agentLimit, 0), totalAgentCount: applications.reduce((sum, a) => sum + a.agentCount, 0), applications,
});

describe('which owner field a refusal of the opening is about', () => {
  it('names the field the franchise service names, whatever the case', () => {
    expect(openFieldErrors('Owner phone is required')).toEqual({ ownerPhone: 'Owner phone is required' });
    expect(openFieldErrors('  owner EMAIL is required ')).toEqual({ ownerEmail: 'owner EMAIL is required' });
    expect(openFieldErrors('Owner name is required')).toEqual({ ownerName: 'Owner name is required' });
  });

  it('gives nothing for a message about something else, or none', () => {
    expect(openFieldErrors('This place is already allocated to franchise RBM-00002')).toEqual({});
    expect(openFieldErrors('')).toEqual({});
    expect(openFieldErrors('   ')).toEqual({});
    expect(openFieldErrors(null)).toEqual({});
    expect(openFieldErrors(undefined)).toEqual({});
  });
});

describe('opening a franchise', () => {
  it('accepts an owner with applications that each have places and a fee', () => {
    expect(openFranchiseError(form())).toBeNull();
    expect(openFranchiseError(form({ rows: [{ applicationCode: MATRI, agentLimit: 1, fee: 0 }] }))).toBeNull();
  });

  it('asks for at least one application', () => {
    expect(openFranchiseError(form({ rows: [] }))).toBe('accessManagement.franchises.errors.noApplications');
  });

  it('asks for the owner name and email', () => {
    expect(openFranchiseError(form({ ownerEmail: ' ' }))).toBe('accessManagement.franchises.errors.owner');
    expect(openFranchiseError(form({ ownerName: '' }))).toBe('accessManagement.franchises.errors.owner');
  });

  it('rejects an application with no choice, fewer than one place, or a missing or negative fee', () => {
    const terms = 'accessManagement.franchises.errors.terms';
    expect(openFranchiseError(form({ rows: [{ applicationCode: '', agentLimit: 5, fee: 1 }] }))).toBe(terms);
    expect(openFranchiseError(form({ rows: [{ applicationCode: MATRI, agentLimit: 0, fee: 1 }] }))).toBe(terms);
    expect(openFranchiseError(form({ rows: [{ applicationCode: MATRI, agentLimit: null, fee: 1 }] }))).toBe(terms);
    expect(openFranchiseError(form({ rows: [{ applicationCode: MATRI, agentLimit: 5, fee: null }] }))).toBe(terms);
    expect(openFranchiseError(form({ rows: [{ applicationCode: MATRI, agentLimit: 5, fee: -1 }] }))).toBe(terms);
  });

  it('rejects the same application twice', () => {
    expect(openFranchiseError(form({ rows: [{ applicationCode: MATRI, agentLimit: 5, fee: 1 }, { applicationCode: MATRI, agentLimit: 6, fee: 2 }] })))
      .toBe('accessManagement.franchises.errors.duplicate');
  });

  it('builds the request from the form, trimmed, leaving out a blank phone', () => {
    expect(toOpenRequest(form({ ownerEmail: ' ravi@example.com ', ownerName: ' Ravi ' }))).toEqual({
      applications: [{ applicationCode: MATRI, agentLimit: 20, fee: { code: 'INR', value: 100000 } }, { applicationCode: RIDE, agentLimit: 30, fee: { code: 'INR', value: 200000 }, locationId: 'w-12' }],
      ownerEmail: 'ravi@example.com', ownerName: 'Ravi',
    });
    expect(toOpenRequest(form({ ownerPhone: ' +918247487750 ' })).ownerPhone).toBe('+918247487750');
  });

  it('turns a row into the terms sent, the fee as { code, value } to two decimals, in the row\'s currency or the default one', () => {
    expect(toTerms({ applicationCode: RIDE, agentLimit: 30, fee: 200000 })).toEqual({ applicationCode: RIDE, agentLimit: 30, fee: { code: 'INR', value: 200000 } });
    expect(toTerms({ applicationCode: RIDE, agentLimit: 30, fee: 200000 }, 'USD').fee).toEqual({ code: 'USD', value: 200000 });
    expect(toTerms({ applicationCode: RIDE, agentLimit: 30, fee: 1999.999, currency: 'AED' }))
      .toEqual({ applicationCode: RIDE, agentLimit: 30, fee: { code: 'AED', value: 2000 } });
    expect(toOpenRequest(form({}), 'AED').applications[0].fee).toEqual({ code: 'AED', value: 100000 });
  });

  it('starts blank, with one application row to fill in', () => {
    expect(emptyTermsRow()).toMatchObject({ applicationCode: '', agentLimit: null, fee: null, currency: null, planKey: '', templateId: '' });
    expect(emptyOpenForm().rows).toHaveLength(1);
    expect(emptyOpenForm().ownerEmail).toBe('');
  });
});

describe('agent places', () => {
  it('counts the places left and never goes below zero', () => {
    expect(placesLeft(application(MATRI, 20, 5))).toBe(15);
    expect(placesLeft(application(MATRI, 20, 20))).toBe(0);
    expect(placesLeft(application(MATRI, 20, 25))).toBe(0);
  });

  it('offers only the applications that still have a place free', () => {
    const f = franchise([application(MATRI, 20, 20), application(RIDE, 30, 29)]);
    expect(applicationsWithRoom(f).map((a) => a.applicationCode)).toEqual([RIDE]);
    expect(applicationsWithRoom(null)).toEqual([]);
  });

  it('shows the share used, capped at 100 and zero when there is no limit', () => {
    expect(usagePercent(25, 50)).toBe(50);
    expect(usagePercent(60, 50)).toBe(100);
    expect(usagePercent(0, 0)).toBe(0);
    expect(usagePercent(1, 3)).toBe(33);
  });
});

describe('adding an agent', () => {
  const agent = { applicationCode: MATRI, fullName: 'Sita', email: 'sita@example.com', phone: '' };

  it('needs the application, a name and a valid email', () => {
    expect(addAgentError(agent)).toBeNull();
    expect(addAgentError({ ...agent, applicationCode: '' })).toBe('accessManagement.franchises.errors.agentApplication');
    expect(addAgentError({ ...agent, fullName: ' ' })).toBe('accessManagement.franchises.errors.agent');
    expect(addAgentError({ ...agent, email: 'nope' })).toBe('accessManagement.franchises.errors.agent');
    expect(addAgentError({ ...agent, email: 'a@b' })).toBe('accessManagement.franchises.errors.agent');
  });

  it('trims the request and leaves out a blank phone', () => {
    expect(toAgentRequest({ ...agent, fullName: ' Sita ', email: ' sita@example.com ', phone: '  ' }))
      .toEqual({ applicationCode: MATRI, fullName: 'Sita', email: 'sita@example.com' });
    expect(toAgentRequest({ ...agent, phone: ' 9000000001 ' }).phone).toBe('9000000001');
  });

  it('starts blank', () => {
    expect(emptyAgentRequest()).toEqual({ applicationCode: '', fullName: '', email: '', phone: '' });
  });
});

const offer = (application: string, country: string, state: string, limit: number, fee: number, currency = 'INR', city = ''): FranchisePlan => ({
  id: [application, country, state, city].join('|'), applicationCode: application,
  countryCode: country, countryName: country === 'IN' ? 'India' : country === 'AE' ? 'United Arab Emirates' : '',
  stateCode: state, stateName: state === 'TS' ? 'Telangana' : state === 'KA' ? 'Karnataka' : '', city, districtCode: '', agentLimit: limit, fee: { code: currency, value: fee },
});

describe('money', () => {
  it('rounds a fee to two decimals and keeps an empty one empty', () => {
    expect(roundFee(1999.999)).toBe(2000);
    expect(roundFee(10.005)).toBe(10.01);
    expect(roundFee(100000)).toBe(100000);
    expect(roundFee(null)).toBeNull();
    expect(roundFee(Number.NaN)).toBeNull();
  });

  it('shows a fee with its currency symbol and two decimals, grouped by the locale', () => {
    expect(formatMoney(100000, 'INR', 'en-IN')).toBe('₹1,00,000.00');
    expect(formatMoney(1234.5, 'USD', 'en-US')).toBe('$1,234.50');
    expect(formatMoney(0, 'INR', 'en-IN')).toBe('₹0.00');
  });

  it('shows nothing for no amount, and the plain amount with its code when the currency is not understood', () => {
    expect(formatMoney(null, 'INR')).toBe('');
    expect(formatMoney(5, 'NOPE')).toBe('5.00 NOPE');
  });

  it('shows an API amount in its own currency, and an older bare number in the fallback one', () => {
    expect(moneyText({ code: 'USD', value: 1234.5 }, 'INR', 'en-US')).toBe('$1,234.50');
    expect(moneyText(100000, 'INR', 'en-IN')).toBe('₹1,00,000.00');
    expect(moneyText(null, 'INR')).toBe('');
  });

  it('gives the symbol of a currency, or its code when it cannot', () => {
    expect(currencySymbol('INR', 'en-IN')).toBe('₹');
    expect(currencySymbol('USD', 'en-US')).toBe('$');
    expect(currencySymbol('NOPE')).toBe('NOPE');
  });
});

describe('preconfigured offers', () => {
  const plans = [
    offer(MATRI, 'AE', '', 10, 900, 'AED'),
    offer(MATRI, 'IN', 'TS', 20, 100000),
    offer(MATRI, 'IN', 'TS', 18, 90000, 'INR', 'Hyderabad'),
    offer(MATRI, 'IN', '', 15, 80000),
    offer(MATRI, '', '', 5, 50000),
    offer(RIDE, 'IN', '', 30, 200000),
  ];

  it('lists the plans of the application only, the user\'s country first (the whole country, then its states, then its cities), then the general plan, then the rest', () => {
    const codes = plansForApplication(plans, MATRI, 'IN').map((p) => [p.countryCode, p.stateCode, p.city].join('/'));
    expect(codes).toEqual(['IN//', 'IN/TS/', 'IN/TS/Hyderabad', '//', 'AE//']);
  });

  it('lists the general offer first when the user\'s country has no offer, and keeps every offer selectable', () => {
    expect(plansForApplication(plans, MATRI, null).map((p) => [p.countryCode, p.stateCode, p.city].join('/'))).toEqual(['//', 'IN//', 'IN/TS/', 'IN/TS/Hyderabad', 'AE//']);
    expect(plansForApplication(plans, MATRI, 'SG')).toHaveLength(5);
    expect(plansForApplication(plans, 'OTHER_APP', 'IN')).toEqual([]);
  });

  it('does not change the list it is given', () => {
    const copy = [...plans];
    plansForApplication(plans, MATRI, 'IN');
    expect(plans).toEqual(copy);
  });

  it('names an offer by country and state, or as general when it has no country', () => {
    expect(planRegion(offer(MATRI, 'IN', 'TS', 1, 1), 'General')).toBe('India - Telangana');
    expect(planRegion(offer(MATRI, 'IN', '', 1, 1), 'General')).toBe('India');
    expect(planRegion(offer(MATRI, 'IN', 'TS', 1, 1, 'INR', 'Hyderabad'), 'General')).toBe('India - Telangana - Hyderabad');
    expect(planRegion(offer(MATRI, '', '', 1, 1), 'General')).toBe('General');
    expect(planRegion({ ...offer(MATRI, 'XX', 'ZZ', 1, 1), countryName: '', stateName: '' }, 'General')).toBe('XX - ZZ');
  });

  it('gives each offer its own key, its id', () => {
    expect(planKey(offer(MATRI, 'IN', 'TS', 1, 1))).toBe(`${MATRI}|IN|TS|`);
    expect(new Set(plans.map(planKey)).size).toBe(plans.length);
  });

  it('fills a row with an offer\'s places, fee and currency', () => {
    const row = applyPlan({ applicationCode: MATRI, agentLimit: null, fee: null }, offer(MATRI, 'AE', '', 10, 900.456, 'AED'));
    expect(row).toEqual({ applicationCode: MATRI, agentLimit: 10, fee: 900.46, currency: 'AED', planKey: `${MATRI}|AE||` });
  });

  it('lists each country with offers once', () => {
    expect(offerCountries(plans).sort()).toEqual(['AE', 'IN']);
    expect(offerCountries([])).toEqual([]);
  });
});

describe('the country of a time zone', () => {
  it('finds it among the countries that have offers', () => {
    expect(countryFromTimeZone('Asia/Kolkata', ['AE', 'IN'])).toBe('IN');
    expect(countryFromTimeZone('Asia/Dubai', ['AE', 'IN'])).toBe('AE');
  });

  it('knows nothing of an unknown time zone, or of a country that has no offer', () => {
    expect(countryFromTimeZone('Nowhere/Land', ['IN'])).toBeNull();
    expect(countryFromTimeZone('Asia/Kolkata', ['AE'])).toBeNull();
    expect(countryFromTimeZone(undefined, ['IN'])).toBeNull();
    expect(countryFromTimeZone('Asia/Kolkata', [])).toBeNull();
  });
});

describe('the franchise plan form', () => {
  const valid: PlanForm = {
    applicationCode: MATRI, countryCode: 'IN', stateCode: ' ts ', stateName: ' Telangana ', city: ' Hyderabad ', districtCode: '536', agentLimit: 20, fee: 100000.456, currency: 'INR',
  };

  it('starts blank in the default currency', () => {
    expect(emptyPlanForm('AED')).toEqual({ applicationCode: '', countryCode: '', stateCode: '', stateName: '', city: '', districtCode: '', agentLimit: null, fee: null, currency: 'AED' });
  });

  it('accepts a plan for a country, state and city, and a general plan', () => {
    expect(planFormError(valid)).toBeNull();
    expect(planFormError({ ...valid, countryCode: '', stateCode: '', stateName: '', city: '' })).toBeNull();
  });

  it('needs an application, places of at least one and a fee of zero or more', () => {
    expect(planFormError({ ...valid, applicationCode: '' })).toBe('accessManagement.franchisePlans.errors.application');
    expect(planFormError({ ...valid, agentLimit: 0 })).toBe('accessManagement.franchises.errors.terms');
    expect(planFormError({ ...valid, agentLimit: null })).toBe('accessManagement.franchises.errors.terms');
    expect(planFormError({ ...valid, fee: null })).toBe('accessManagement.franchises.errors.terms');
    expect(planFormError({ ...valid, fee: -1 })).toBe('accessManagement.franchises.errors.terms');
  });

  it('needs a country for a state or a city', () => {
    expect(planFormError({ ...valid, countryCode: '' })).toBe('accessManagement.franchisePlans.errors.area');
    expect(planFormError({ ...valid, countryCode: '', stateCode: '', stateName: '' })).toBe('accessManagement.franchisePlans.errors.area');
  });

  it('builds the request: codes upper-cased, text trimmed, the fee to two decimals and the country named', () => {
    expect(toPlanRequest(valid, [{ code: 'IN', name: 'India' }])).toEqual({
      applicationCode: MATRI, countryCode: 'IN', countryName: 'India', stateCode: 'TS', stateName: 'Telangana', city: 'Hyderabad', districtCode: '536',
      agentLimit: 20, fee: { code: 'INR', value: 100000.46 },
    });
    expect(toPlanRequest({ ...valid, city: ' ' }, []).districtCode).toBe('');
    expect(toPlanRequest({ ...valid, countryCode: '' }, []).countryName).toBe('');
  });

  it('turns a saved plan back into a form', () => {
    expect(planToForm(offer(MATRI, 'IN', 'TS', 20, 100000, 'AED', 'Hyderabad'))).toEqual({
      applicationCode: MATRI, countryCode: 'IN', stateCode: 'TS', stateName: 'Telangana', city: 'Hyderabad', districtCode: '', agentLimit: 20, fee: 100000, currency: 'AED',
    });
  });

  it('reads an older plan whose fee is a bare number in the fallback currency', () => {
    const legacy = { ...offer(MATRI, 'IN', '', 5, 1), fee: 750 as unknown as FranchisePlan['fee'] };
    expect(planToForm(legacy, 'USD')).toMatchObject({ fee: 750, currency: 'USD' });
    expect(planToForm(legacy)).toMatchObject({ fee: 750, currency: 'INR' });
  });

  it('lists the countries by name without codes the browser does not know, and the currencies', () => {
    const countries = countryOptions('en');
    expect(countries.find((c) => c.code === 'IN')?.name).toBe('India');
    expect(countries.find((c) => c.code === 'AA')).toBeUndefined();
    expect(countries.map((c) => c.name)).toEqual([...countries.map((c) => c.name)].sort((a, b) => a.localeCompare(b)));
    expect(countryOptions('not a language')).toEqual([]);
    expect(currencyOptions()).toEqual(['INR']);
  });

  it('offers only the rupee, but keeps the currency an older plan already has so editing it loses nothing', () => {
    expect(currencyOptions('INR')).toEqual(['INR']);
    expect(currencyOptions('USD')).toEqual(['INR', 'USD']);
    expect(currencyOptions(null)).toEqual(['INR']);
  });

});

describe('the state and district lists', () => {
  const states = { IN: [['AP', 'Andhra Pradesh'], ['TG', 'Telangana']] as [string, string][], AE: [] as [string, string][] };

  it('lists the states of a country, and none for a country without a list', () => {
    expect(stateOptions(states, 'IN')).toEqual([{ code: 'AP', name: 'Andhra Pradesh' }, { code: 'TG', name: 'Telangana' }]);
    expect(stateOptions(states, 'AE')).toEqual([]);
    expect(stateOptions(states, 'ZZ')).toEqual([]);
    expect(stateOptions(states, '')).toEqual([]);
  });

  it('keeps a state the plan already has that the list does not know, in name order, and never repeats a listed one', () => {
    const kept = stateOptions(states, 'IN', { code: 'ts', name: 'Telangana (old code)' });
    expect(kept.map((s) => s.code)).toEqual(['AP', 'TG', 'TS']);
    expect(stateOptions(states, 'IN', { code: 'tg', name: 'Telangana' })).toHaveLength(2);
    expect(stateOptions(states, 'IN', { code: '', name: '' })).toHaveLength(2);
    expect(stateOptions(states, 'AE', { code: 'DU', name: 'Dubai' })).toEqual([]);
  });

  it('lists the districts of a state, and none for a state without a list', () => {
    const districts = { TG: [['532', 'Adilabad'], ['536', 'Hyderabad']] as [string, string][] };
    expect(districtOptions(districts, 'TG')).toEqual([{ code: '532', name: 'Adilabad' }, { code: '536', name: 'Hyderabad' }]);
    expect(districtOptions(districts, 'AP')).toEqual([]);
    expect(districtOptions({}, '')).toEqual([]);
  });
});

describe('the place of a franchise', () => {
  const plan = { applicationCode: MATRI, agentLimit: 12, fee: { code: 'INR', value: 2500.5 } } as FranchisePlan;
  const resolution = (overrides: Partial<PlanResolution>): PlanResolution => ({
    plan, rule: 'OWN', inherited: false, source: null, place: [], ...overrides,
  });

  it('sends the place of an application only when one was chosen', () => {
    const row = { applicationCode: MATRI, agentLimit: 5, fee: 10 };

    expect(toTerms({ ...row, locationId: 'town-1' }).locationId).toBe('town-1');
    expect('locationId' in toTerms({ ...row, locationId: '' })).toBe(false);
    expect('locationId' in toTerms(row)).toBe(false);
    expect(toOpenRequest({ ...emptyOpenForm(), ownerName: 'A', ownerEmail: 'a@x.co', rows: [{ ...row, locationId: 'town-1' }] }).applications[0].locationId).toBe('town-1');
  });

  it('takes the agent places, fee and currency of the plan that applies and clears the offer choice', () => {
    const row = { applicationCode: MATRI, agentLimit: null, fee: null, currency: null, planKey: 'old', locationId: 'town-1', templateId: 't1' };

    expect(applyResolution(row, resolution({}))).toEqual({ ...row, agentLimit: 12, fee: 2500.5, currency: 'INR', planKey: '' });
  });

  it('selects the applied plan in the offer list when it is one of the offers', () => {
    const row = { applicationCode: MATRI, agentLimit: null, fee: null, currency: null, planKey: '' };
    const applied = { ...plan, id: 'plan-ap' } as FranchisePlan;
    const found = resolution({ plan: applied });

    expect(applyResolution(row, found, [applied]).planKey).toBe('plan-ap');
    expect(applyResolution(row, found, [{ ...applied, id: 'other' }]).planKey).toBe('');
  });

  it('keeps the row\'s currency when the plan names none', () => {
    const row = { applicationCode: MATRI, agentLimit: 1, fee: 1, currency: 'USD' };

    expect(applyResolution(row, resolution({ plan: { ...plan, fee: 99 as unknown as FranchisePlan['fee'] } }))).toMatchObject({ fee: 99, currency: 'USD' });
  });

  it('says where the plan came from: the place itself, a place above it, or the country', () => {
    expect(placeNote(resolution({}))).toEqual({ key: 'accessManagement.franchisePlans.resolve.own', params: { name: '' } });
    expect(placeNote(resolution({ inherited: true, rule: 'PARENT', source: { id: 'd1', type: 'DISTRICT', level: 'REGION', code: 'KR', name: 'Krishna' } }))).toEqual({
      key: 'accessManagement.franchisePlans.resolve.inheritedFrom', params: { name: 'Krishna' },
    });
    expect(placeNote(resolution({ inherited: true, rule: 'GENERAL' })).key).toBe('accessManagement.franchisePlans.resolve.inheritedCountry');
    expect(noPlanNote().key).toBe('accessManagement.franchises.placePlanNone');
  });
});

describe('application pick labels', () => {
  const apps = [{ code: 'PASUPU_KUNKUMA_MATRIMONY', name: 'Matrimony' }, { code: 'RIDE', name: '' }];

  it('shows the name with its code, or only the code when the application has no name', () => {
    expect(applicationPickLabel(apps[0])).toBe('Matrimony (PASUPU_KUNKUMA_MATRIMONY)');
    expect(applicationPickLabel(apps[1])).toBe('RIDE');
  });

  it('finds the code behind a picked label, and gives an empty code for a cleared or unknown label', () => {
    expect(applicationCodeForLabel(apps, 'Matrimony (PASUPU_KUNKUMA_MATRIMONY)')).toBe('PASUPU_KUNKUMA_MATRIMONY');
    expect(applicationCodeForLabel(apps, 'RIDE')).toBe('RIDE');
    expect(applicationCodeForLabel(apps, '')).toBe('');
    expect(applicationCodeForLabel(apps, 'Something else')).toBe('');
  });
});

describe('franchise list and status helpers', () => {
  it('shows the owner by name, then email, then a dash', () => {
    expect(ownerLabel({ ownerName: ' Asha ', ownerEmail: 'a@x.com' })).toBe('Asha');
    expect(ownerLabel({ ownerName: '', ownerEmail: 'a@x.com' })).toBe('a@x.com');
    expect(ownerLabel({ ownerName: null, ownerEmail: null })).toBe('—');
    expect(ownerLabel({})).toBe('—');
  });

  it('lists each covered place once, or a dash', () => {
    const app = (locationName: string | null) => ({ applicationCode: 'A', agentLimit: 1, agentCount: 0, fee: { code: 'INR', value: 0 }, locationName });
    expect(placeLabel({ applications: [app('Hyderabad'), app('Hyderabad'), app('Warangal'), app(null)] })).toBe('Hyderabad, Warangal');
    expect(placeLabel({ applications: [app(null)] })).toBe('—');
    expect(placeLabel({ applications: [] })).toBe('—');
  });

  it('lets an administrator suspend, reactivate or close, and never reopen a closed franchise', () => {
    expect(nextStatuses('ACTIVE')).toEqual(['SUSPENDED', 'CLOSED']);
    expect(nextStatuses('SUSPENDED')).toEqual(['ACTIVE', 'CLOSED']);
    expect(nextStatuses('CLOSED')).toEqual([]);
    expect(nextStatuses('UNKNOWN')).toEqual([]);
  });

  it('names the place type of an offer when it has one', () => {
    expect(planPlaceType({ locationType: 'CITY', scopeType: null })).toBe('CITY');
    expect(planPlaceType({ locationType: null, scopeType: 'DISTRICT' })).toBe('DISTRICT');
    expect(planPlaceType({})).toBeNull();
  });

  it('counts the franchises still active in an organization that is now DIRECT', () => {
    const base = { unitId: 'u', code: 'C', name: 'C', ownerUserId: 'o', status: 'ACTIVE', totalAgentLimit: 0, totalAgentCount: 0, applications: [] };
    expect(activeInDirectCount([
      { ...base, activeInDirectOrganization: true },
      { ...base, unitId: 'u2', activeInDirectOrganization: false },
      { ...base, unitId: 'u3' },
      { ...base, unitId: 'u4', activeInDirectOrganization: true },
    ])).toBe(2);
    expect(activeInDirectCount([])).toBe(0);
    expect(activeInDirectCount(null)).toBe(0);
    expect(activeInDirectCount(undefined)).toBe(0);
  });
});
