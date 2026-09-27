import { LucideAngularModule } from '@lucide/angular';
import {
  Ban,
  Bell,
  CheckCheck,
  ChefHat,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  CircleCheck,
  CircleX,
  Clock,
  Filter,
  LayoutGrid,
  Rows3,
  Search,
  TimerOff,
  X,
} from '@lucide/angular';

/**
 * Icon library: `@lucide/angular` (MIT, tree-shaken via `.pick`). Import `AppIconsModule` wherever an icon is
 * used (it's re-exported from `SharedModule`), then use an icon as an attribute directive on an inline `<svg>`:
 * `<svg lucideChefHat class="size-4"></svg>`. Every icon-only button must still carry its own `aria-label` —
 * icons here are always `aria-hidden` decoration, never the accessible name by themselves.
 *
 * This set covers `shared/order-status.ts` (Clock, CircleCheck, ChefHat, Bell, CheckCheck, TimerOff, CircleX, Ban)
 * plus common chrome (chevrons, search, filter, density toggle). Add more icons here as components need them —
 * `.pick` keeps the bundle to only what's listed.
 */
export const AppIconsModule = LucideAngularModule.pick({
  Ban,
  Bell,
  CheckCheck,
  ChefHat,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  CircleCheck,
  CircleX,
  Clock,
  Filter,
  LayoutGrid,
  Rows3,
  Search,
  TimerOff,
  X,
});
