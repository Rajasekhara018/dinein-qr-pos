import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { HqSharedLibModule, HqTimezoneService } from 'hq-shared-lib';
import { TimezonePicker } from './timezone-picker';

describe('TimezonePicker', () => {
  let service: HqTimezoneService;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      declarations: [TimezonePicker],
      imports: [TranslateModule.forRoot(), HqSharedLibModule],
    }).compileComponents();
    service = TestBed.inject(HqTimezoneService);
  });

  function create(value = ''): { picker: TimezonePicker; emitted: string[] } {
    const fixture = TestBed.createComponent(TimezonePicker);
    fixture.componentInstance.value = value;
    const emitted: string[] = [];
    fixture.componentInstance.valueChange.subscribe((v: string) => emitted.push(v));
    fixture.detectChanges();
    return { picker: fixture.componentInstance, emitted };
  }

  it('starts on the device timezone when the value is empty', () => {
    const { picker } = create('');
    expect(picker.usingDevice).toBe(true);
    expect(picker.invalid).toBe(false);
    expect(picker.previewZone).toBe(service.effectiveTimezoneFor(null));
  });

  it('previews the chosen zone and emits it', () => {
    const { picker, emitted } = create('');
    picker.choose('Asia/Kolkata');
    expect(emitted).toEqual(['Asia/Kolkata']);
    expect(picker.previewZone).toBe('Asia/Kolkata');
    expect(picker.usingDevice).toBe(false);
  });

  it('emits the typed text and flags an invalid zone', () => {
    const { picker, emitted } = create('');
    picker.onType('Mars/Phobos');
    expect(emitted).toEqual(['Mars/Phobos']);
    expect(picker.invalid).toBe(true);
    picker.onType('Asia/Dubai');
    expect(picker.invalid).toBe(false);
  });

  it('the device button clears the value', () => {
    const { picker, emitted } = create('Asia/Dubai');
    picker.useDevice();
    expect(emitted).toEqual(['']);
    expect(picker.usingDevice).toBe(true);
  });

  it('narrows the list while typing', () => {
    const { picker } = create('');
    picker.onType('kolkata');
    expect(picker.options.some((zone) => zone.endsWith('Kolkata') || zone.endsWith('Calcutta'))).toBe(true);
    expect(picker.options.length).toBeLessThan(picker.maxOptions + 1);
  });
});
