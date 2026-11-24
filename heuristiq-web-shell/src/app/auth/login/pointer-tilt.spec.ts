import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { POINTER_TILT_FINE_POINTER_QUERY } from './login.constants';
import { PointerTilt } from './pointer-tilt';

@Component({ template: '<div appPointerTilt></div>', standalone: false })
class Host {}

/** Answers the media queries: a mouse pointer, and whether reduced motion is asked for. */
function mockMedia(finePointer: boolean, reducedMotion: boolean): void {
  vi.spyOn(window, 'matchMedia').mockImplementation(
    (query: string) => ({ matches: query === POINTER_TILT_FINE_POINTER_QUERY ? finePointer : reducedMotion }) as MediaQueryList,
  );
}

function render(): HTMLElement {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ declarations: [Host, PointerTilt] });
  const fixture = TestBed.createComponent(Host);
  fixture.detectChanges();
  return (fixture.nativeElement as HTMLElement).querySelector('[appPointerTilt]') as HTMLElement;
}

describe('PointerTilt', () => {
  afterEach(() => vi.restoreAllMocks());

  it('writes the pointer offset as CSS variables once per frame and resets when the pointer leaves', () => {
    mockMedia(true, false);
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((cb: FrameRequestCallback) => {
      cb(0);
      return 1;
    });
    const el = render();
    window.dispatchEvent(new PointerEvent('pointermove', { clientX: window.innerWidth, clientY: 0 }));
    expect(el.style.getPropertyValue('--tilt-x')).toBe('1.000');
    expect(el.style.getPropertyValue('--tilt-y')).toBe('-1.000');
    document.dispatchEvent(new PointerEvent('pointerleave'));
    expect(el.style.getPropertyValue('--tilt-x')).toBe('0.000');
  });

  it('stays still on a touch screen or when reduced motion is asked for', () => {
    mockMedia(false, false);
    let el = render();
    window.dispatchEvent(new PointerEvent('pointermove', { clientX: 0, clientY: 0 }));
    expect(el.style.getPropertyValue('--tilt-x')).toBe('');
    mockMedia(true, true);
    el = render();
    window.dispatchEvent(new PointerEvent('pointermove', { clientX: 0, clientY: 0 }));
    expect(el.style.getPropertyValue('--tilt-x')).toBe('');
  });
});
