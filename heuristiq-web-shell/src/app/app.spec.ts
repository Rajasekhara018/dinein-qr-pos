import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';
import { HqSharedLibModule } from 'hq-shared-lib';
import { TranslateModule } from '@ngx-translate/core';
import { App } from './app';
import { BrandingService } from './shared/services/branding-service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        RouterModule.forRoot([]),
        TranslateModule.forRoot(),
        HqSharedLibModule.forRoot({ gatewayUrl: '', adminApiUrl: '', currentUserId: () => null }),
      ],
      declarations: [App],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('applies the deployment branding to the page title', () => {
    TestBed.createComponent(App);

    expect(TestBed.inject(Title).getTitle()).toBe(TestBed.inject(BrandingService).branding.appName);
  });

  it('points the favicon at the branded icon when the page has one', () => {
    const link = document.createElement('link');
    link.rel = 'icon';
    link.href = 'http://localhost/old.ico';
    document.head.appendChild(link);
    try {
      TestBed.createComponent(App);

      expect(link.getAttribute('href')).toBe(TestBed.inject(BrandingService).branding.favicon);
    } finally {
      link.remove();
    }
  });

  it('renders the toast host and the router outlet', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('hq-toast-host')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('router-outlet')).not.toBeNull();
  });
});
