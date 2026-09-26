import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'error' | 'info' | 'warning';

export interface ToastAction {
  label: string;
  run: () => void;
}

export interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
  action?: ToastAction;
  /** Deduplication key: showing a toast with the same key replaces the previous one. */
  key?: string;
  durationMs: number;
}

export interface ToastOptions {
  action?: ToastAction;
  key?: string;
  /** 0 = sticky until dismissed. Default 4s (6s for errors and toasts with an action, e.g. "Undo"). */
  durationMs?: number;
}

/** App-wide toasts, rendered by `<app-toast-host>` (declared in CoreModule, placed once in the root template). */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly _toasts = signal<readonly Toast[]>([]);
  readonly toasts = this._toasts.asReadonly();

  private nextId = 1;
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();

  show(kind: ToastKind, message: string, options: ToastOptions = {}): number {
    const id = this.nextId++;
    const durationMs =
      options.durationMs ?? (kind === 'error' || options.action ? 6000 : 4000);
    const toast: Toast = { id, kind, message, action: options.action, key: options.key, durationMs };
    this._toasts.update((list) => {
      const kept = options.key ? list.filter((t) => t.key !== options.key) : list;
      for (const removed of list) {
        if (!kept.includes(removed)) this.clearTimer(removed.id);
      }
      return [...kept, toast].slice(-4);
    });
    if (durationMs > 0) {
      this.timers.set(
        id,
        setTimeout(() => this.dismiss(id), durationMs),
      );
    }
    return id;
  }

  success(message: string, options?: ToastOptions): number {
    return this.show('success', message, options);
  }

  error(message: string, options?: ToastOptions): number {
    return this.show('error', message, options);
  }

  info(message: string, options?: ToastOptions): number {
    return this.show('info', message, options);
  }

  warning(message: string, options?: ToastOptions): number {
    return this.show('warning', message, options);
  }

  dismiss(id: number): void {
    this.clearTimer(id);
    this._toasts.update((list) => list.filter((t) => t.id !== id));
  }

  clear(): void {
    for (const id of this.timers.keys()) this.clearTimer(id);
    this._toasts.set([]);
  }

  private clearTimer(id: number): void {
    const timer = this.timers.get(id);
    if (timer) clearTimeout(timer);
    this.timers.delete(id);
  }
}
