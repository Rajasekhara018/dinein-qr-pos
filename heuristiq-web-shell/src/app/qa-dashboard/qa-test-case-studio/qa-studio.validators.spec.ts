import { FormArray, FormControl, FormGroup } from '@angular/forms';

import { QA_STUDIO_ISSUE_KEYS as K } from './qa-studio.constants';
import {
  STUDIO_ISSUE_ERROR,
  issueOf,
  studioAssertionRowValidator,
  studioAssertionsValidator,
  studioBodyValidator,
  studioDescriptionValidator,
  studioKeyValueRowValidator,
  studioKeyValueTableValidator,
  studioNameValidator,
  studioPathValidator,
  studioSubModuleValidator,
} from './qa-studio.validators';

const kv = (name: string, value: string): FormGroup => new FormGroup({ name: new FormControl(name), value: new FormControl(value) });
const assertion = (type: string, expected: string): FormGroup =>
  new FormGroup({ type: new FormControl(type), path: new FormControl(''), name: new FormControl(''), expected: new FormControl(expected) });

describe('studio validators', () => {
  it('reports a broken rule as an issue the page can translate, and nothing when the value is fine', () => {
    const name = new FormControl('x', studioNameValidator);
    expect(name.errors?.[STUDIO_ISSUE_ERROR]).toMatchObject({ key: K.nameLength });
    expect(issueOf(name)?.key).toBe(K.nameLength);
    name.setValue('A good name');
    expect(name.errors).toBeNull();
    expect(issueOf(name)).toBeNull();
    expect(issueOf(null)).toBeNull();
  });

  it('checks the description, the sub-module and the path', () => {
    expect(issueOf(new FormControl('x'.repeat(501), studioDescriptionValidator))?.key).toBe(K.descriptionLength);
    expect(issueOf(new FormControl('x'.repeat(101), studioSubModuleValidator))?.key).toBe(K.subModuleLength);
    expect(issueOf(new FormControl('/v1/x', studioPathValidator))?.key).toBe(K.pathPrefix);
    expect(issueOf(new FormControl('/api/v1/x', studioPathValidator))).toBeNull();
    expect(issueOf(new FormControl(null, studioPathValidator))?.key).toBe(K.required);
    expect(issueOf(new FormControl(null, studioNameValidator))?.key).toBe(K.required);
  });

  it('checks the body against the method of the form it is in', () => {
    const form = new FormGroup({ method: new FormControl('GET'), body: new FormControl('{"a":1}', studioBodyValidator) });
    expect(issueOf(form.controls.body)?.key).toBe(K.bodyNotAllowed);
    form.controls.method.setValue('POST');
    form.controls.body.updateValueAndValidity();
    expect(issueOf(form.controls.body)).toBeNull();
    form.controls.body.setValue('{a');
    expect(issueOf(form.controls.body)?.key).toBe(K.bodyJson);
    // Outside a form the method is not known: it is read as GET, which carries no body.
    expect(issueOf(new FormControl('{"a":1}', studioBodyValidator))?.key).toBe(K.bodyNotAllowed);
    expect(issueOf(new FormControl(null, studioBodyValidator))).toBeNull();
  });

  it('checks a row of the headers or query table', () => {
    const header = kv('Authorization', 'x');
    header.addValidators(studioKeyValueRowValidator('headers'));
    header.updateValueAndValidity();
    expect(issueOf(header)?.key).toBe(K.headerForbidden);
    const query = kv('a', 'b');
    query.addValidators(studioKeyValueRowValidator('query'));
    query.updateValueAndValidity();
    expect(issueOf(query)).toBeNull();
  });

  it('checks the table as a whole: too many rows and a name twice', () => {
    const table = new FormArray([kv('A', '1'), kv('a', '2')], studioKeyValueTableValidator('headers'));
    expect(issueOf(table)?.key).toBe(K.duplicate);
    table.removeAt(1);
    expect(issueOf(table)).toBeNull();
    const query = new FormArray(Array.from({ length: 21 }, (_, i) => kv(`p${i}`, '1')), studioKeyValueTableValidator('query'));
    expect(issueOf(query)?.key).toBe(K.tooMany);
  });

  it('checks one assertion row and the list of them', () => {
    const row = assertion('STATUS', '99');
    row.addValidators(studioAssertionRowValidator);
    row.updateValueAndValidity();
    expect(issueOf(row)?.key).toBe(K.statusCodes);
    row.controls['expected'].setValue('200');
    expect(issueOf(row)).toBeNull();

    const list = new FormArray([assertion('MAX_TIME_MS', '100')], studioAssertionsValidator);
    expect(issueOf(list)?.key).toBe(K.statusRequired);
    list.push(assertion('STATUS', '200'));
    expect(issueOf(list)).toBeNull();
  });
});
