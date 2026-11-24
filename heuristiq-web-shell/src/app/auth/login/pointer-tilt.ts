import { Directive, ElementRef, NgZone, OnDestroy, OnInit, inject } from '@angular/core';
import { POINTER_TILT_FINE_POINTER_QUERY, POINTER_TILT_REDUCED_MOTION_QUERY, POINTER_TILT_VARS } from './login.constants';
import { pointerOffset } from './login.helpers';

/**
 * Follows the mouse across the window and writes the pointer's offset from the centre to the host as CSS custom
 * properties (`--tilt-x`, `--tilt-y`, -1 to 1), which the stylesheet turns into a 3D tilt and parallax drift. Resets to
 * 0 when the pointer leaves the window. Runs outside Angular (no change detection per mouse move), at most once per
 * animation frame, and does nothing on touch screens or when the user prefers reduced motion.
 */
@Directive({
  selector: '[appPointerTilt]',
  standalone: false,
})
export class PointerTilt implements OnInit, OnDestroy {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly zone = inject(NgZone);
  private readonly cleanups: (() => void)[] = [];
  private frame = 0;

  ngOnInit(): void {
    const view = this.host.nativeElement.ownerDocument.defaultView;
    if (!view || !PointerTilt.enabled(view)) return;
    this.zone.runOutsideAngular(() => {
      const move = (event: PointerEvent) => this.schedule(view, event.clientX, event.clientY);
      const leave = () => this.write(0, 0);
      view.addEventListener('pointermove', move, { passive: true });
      view.document.addEventListener('pointerleave', leave);
      this.cleanups.push(
        () => view.removeEventListener('pointermove', move),
        () => view.document.removeEventListener('pointerleave', leave),
      );
    });
  }

  ngOnDestroy(): void {
    this.cleanups.forEach((cleanup) => cleanup());
    const view = this.host.nativeElement.ownerDocument.defaultView;
    if (this.frame && view) view.cancelAnimationFrame(this.frame);
  }

  /** True when the device has a mouse-like pointer and the user has not asked for reduced motion. */
  static enabled(view: Window): boolean {
    if (typeof view.matchMedia !== 'function') return false;
    return view.matchMedia(POINTER_TILT_FINE_POINTER_QUERY).matches && !view.matchMedia(POINTER_TILT_REDUCED_MOTION_QUERY).matches;
  }

  private schedule(view: Window, clientX: number, clientY: number): void {
    if (this.frame) return;
    this.frame = view.requestAnimationFrame(() => {
      this.frame = 0;
      const offset = pointerOffset(clientX, clientY, view.innerWidth, view.innerHeight);
      this.write(offset.x, offset.y);
    });
  }

  private write(x: number, y: number): void {
    const style = this.host.nativeElement.style;
    style.setProperty(POINTER_TILT_VARS.x, x.toFixed(3));
    style.setProperty(POINTER_TILT_VARS.y, y.toFixed(3));
  }
}
