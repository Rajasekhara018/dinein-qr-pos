import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

import { Javadoc } from './javadoc';

describe('Javadoc', () => {
  let component: Javadoc;
  let fixture: ComponentFixture<Javadoc>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RouterModule.forRoot([]), TranslateModule.forRoot()],
      declarations: [Javadoc],
    }).compileComponents();

    fixture = TestBed.createComponent(Javadoc);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
