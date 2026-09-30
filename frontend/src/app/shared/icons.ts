import {
  LucideBan,
  LucideBarChart3,
  LucideBell,
  LucideCheck,
  LucideCheckCheck,
  LucideChefHat,
  LucideChevronDown,
  LucideChevronLeft,
  LucideChevronRight,
  LucideChevronUp,
  LucideCircleCheck,
  LucideCircleX,
  LucideClipboardList,
  LucideClock,
  LucideCommand,
  LucideCopy,
  LucideEye,
  LucideEyeOff,
  LucideFilter,
  LucideFolderTree,
  LucideLayoutDashboard,
  LucideLayoutGrid,
  LucideLock,
  LucideLogOut,
  LucideMail,
  LucideMenu,
  LucideMoon,
  LucidePalette,
  LucideQrCode,
  LucideRows3,
  LucideSearch,
  LucideSettings,
  LucideStickyNote,
  LucideSun,
  LucideTabletSmartphone,
  LucideTimerOff,
  LucideUser,
  LucideUsers,
  LucideUtensilsCrossed,
  LucideX,
} from '@lucide/angular';

/**
 * Icon set used across the shared UI kit. `@lucide/angular` v1 ships each icon as its own standalone Angular
 * component (attribute selector, e.g. `svg[lucideChefHat]`) — there is no `LucideAngularModule` to `.pick()` from
 * any more, so these are imported/exported directly on `SharedModule` like any other standalone building block an
 * NgModule can consume.
 *
 * Use an icon as an attribute directive on an inline `<svg>`: `<svg lucideChefHat class="size-4"></svg>`. Every
 * icon-only button must still carry its own `aria-label` — icons are always `aria-hidden` decoration, never the
 * accessible name by themselves.
 *
 * This set covers `shared/order-status.ts` (Clock, CircleCheck, ChefHat, Bell, CheckCheck, TimerOff, CircleX, Ban)
 * plus common chrome (chevrons, search, filter, density toggle) and `StickyNote` (kitchen ticket notes callout).
 * The admin panel (phase 4) adds nav/section icons (LayoutDashboard, ClipboardList, FolderTree, UtensilsCrossed,
 * QrCode, BarChart3, Settings, Users), chrome (Menu, Sun/Moon for the theme toggle, Command for the command
 * palette, LogOut), form icons (Mail, Eye/EyeOff for password show/hide) and small actions (Copy, Check).
 * Add more icons here as components need them.
 */
export const APP_ICONS = [
  LucideBan,
  LucideBarChart3,
  LucideBell,
  LucideCheck,
  LucideCheckCheck,
  LucideChefHat,
  LucideChevronDown,
  LucideChevronLeft,
  LucideChevronRight,
  LucideChevronUp,
  LucideCircleCheck,
  LucideCircleX,
  LucideClipboardList,
  LucideClock,
  LucideCommand,
  LucideCopy,
  LucideEye,
  LucideEyeOff,
  LucideFilter,
  LucideFolderTree,
  LucideLayoutDashboard,
  LucideLayoutGrid,
  LucideLock,
  LucideLogOut,
  LucideMail,
  LucideMenu,
  LucideMoon,
  LucidePalette,
  LucideQrCode,
  LucideRows3,
  LucideSearch,
  LucideSettings,
  LucideStickyNote,
  LucideSun,
  LucideTabletSmartphone,
  LucideTimerOff,
  LucideUser,
  LucideUsers,
  LucideUtensilsCrossed,
  LucideX,
] as const;
