import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { environment } from '../../environments/environment';
import { BrandingService } from '../shared/services/branding-service';
import { ApiDocs } from './api-docs';

describe('ApiDocs', () => {
  const original = environment.apiDocsUrl;

  beforeEach(() => {
    // about:blank keeps the test window from trying to load the real Swagger UI.
    (environment as { apiDocsUrl: string }).apiDocsUrl = 'about:blank';
    TestBed.configureTestingModule({ declarations: [ApiDocs], imports: [TranslateModule.forRoot()] });
  });

  afterEach(() => {
    (environment as { apiDocsUrl: string }).apiDocsUrl = original;
  });

  it('embeds the Swagger UI configured for the environment, titled with the deployment name', () => {
    const fixture = TestBed.createComponent(ApiDocs);
    fixture.detectChanges();
    const iframe = fixture.nativeElement.querySelector('iframe') as HTMLIFrameElement;

    expect(iframe.getAttribute('src')).toBe('about:blank');
    expect(iframe.title).toBe(`${TestBed.inject(BrandingService).branding.appName} API documentation`);
    expect(iframe.getAttribute('loading')).toBe('lazy');
  });

  it('shows the heading and the intro with the deployment name', () => {
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { apiDocs: { eyebrow: 'Developers', apiDocs: { title: 'API docs', copy: 'Endpoints of {{appName}}' } } });
    translate.use('en');
    const fixture = TestBed.createComponent(ApiDocs);
    fixture.detectChanges();
    const text: string = fixture.nativeElement.textContent;

    expect(text).toContain('Developers');
    expect(text).toContain('API docs');
    expect(text).toContain(`Endpoints of ${fixture.componentInstance.branding.appName}`);
  });
});
