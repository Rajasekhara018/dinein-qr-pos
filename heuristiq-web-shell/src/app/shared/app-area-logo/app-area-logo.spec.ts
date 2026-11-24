import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { NAV_AREAS } from '../../layout/nav-areas.constants';
import { AppAreaLogo } from './app-area-logo';

@Component({
  standalone: true,
  imports: [AppAreaLogo],
  template: '<span style="color: rgb(10, 125, 90)"><app-area-logo [src]="src"></app-area-logo></span>',
})
class Host {
  src = 'assets/apps/bestride.svg';
}

describe('AppAreaLogo', () => {
  const render = (src?: string): HTMLElement => {
    const fixture = TestBed.createComponent(Host);
    if (src) fixture.componentInstance.src = src;
    fixture.detectChanges();
    return fixture.nativeElement.querySelector('app-area-logo') as HTMLElement;
  };

  it('masks the glyph over the current colour, so it follows the theme', () => {
    const logo = render();

    expect(logo.style.getPropertyValue('--app-area-logo')).toBe('url("assets/apps/bestride.svg")');
    expect(logo.childElementCount).toBe(0);
  });

  it('follows a change of logo', () => {
    expect(render('assets/apps/wallet.svg').style.getPropertyValue('--app-area-logo')).toBe('url("assets/apps/wallet.svg")');
  });

  it('is decorative: the application name is always shown next to it', () => {
    expect(render().getAttribute('aria-hidden')).toBe('true');
  });

  it('has a one-colour glyph for every product application, under assets/apps', () => {
    const withLogo = NAV_AREAS.filter((a) => a.logo).map((a) => [a.key, a.logo]);

    expect(withLogo).toEqual([
      ['franchise', 'assets/apps/franchise.svg'],
      ['wallet', 'assets/apps/wallet.svg'],
      ['accounting', 'assets/apps/accounting.svg'],
      ['matrimony', 'assets/apps/pasupu-kumkuma.svg'],
      ['admin', 'assets/apps/bestride.svg'],
      ['food', 'assets/apps/royal-food.svg'],
      ['ims', 'assets/apps/best-purchase.svg'],
    ]);
  });
});
