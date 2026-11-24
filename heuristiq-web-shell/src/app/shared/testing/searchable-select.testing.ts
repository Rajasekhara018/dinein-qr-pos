import { ComponentFixture } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { HqSearchableSelect } from 'hq-shared-lib';

/** The searchable dropdown found by a CSS selector (for example its class), as a component instance. */
export function searchableSelect(fixture: ComponentFixture<unknown>, selector: string): HqSearchableSelect {
  return fixture.debugElement.query(By.css(selector)).componentInstance as HqSearchableSelect;
}

/**
 * The values a searchable dropdown offers, in order. A native select listed its blank "any" choice as an option; the searchable
 * dropdown shows that text as its placeholder, so the blank choice is not among them.
 */
export function offeredValues(fixture: ComponentFixture<unknown>, selector: string): unknown[] {
  const choices = (searchableSelect(fixture, selector) as unknown as { choices: ReadonlyArray<{ value: unknown }> | null }).choices;
  return (choices ?? []).map((choice) => choice.value);
}
