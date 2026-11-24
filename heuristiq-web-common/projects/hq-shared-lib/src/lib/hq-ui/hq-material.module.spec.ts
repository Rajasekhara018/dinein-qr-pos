import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatButton } from '@angular/material/button';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { By } from '@angular/platform-browser';
import { HqMaterialModule } from './hq-material.module';

@Component({
  standalone: true,
  imports: [HqMaterialModule],
  template: `<button mat-button>Go</button><mat-slide-toggle>On</mat-slide-toggle>`,
})
class MaterialHost {}

describe('HqMaterialModule', () => {
  it('re-exports the Material modules so their components work in a consumer template', () => {
    TestBed.configureTestingModule({ imports: [MaterialHost] });
    const fixture = TestBed.createComponent(MaterialHost);
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.directive(MatButton))).not.toBeNull();
    expect(fixture.debugElement.query(By.directive(MatSlideToggle))).not.toBeNull();
  });
});
