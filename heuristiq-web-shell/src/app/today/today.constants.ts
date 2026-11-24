import { SectionSpec } from './today.models';

/** Audit screen key of the Today screen (never rename or translate once released). */
export const TODAY_SCREEN_KEY = 'today.overview';

/** Route path of the screen (also the landing page after sign-in). */
export const TODAY_PATH = '/today';

/** How often the figures are re-read while the screen is open. */
export const TODAY_REFRESH_MS = 60_000;
/** How often the clock in the page header ticks (it shows seconds). */
export const TODAY_CLOCK_TICK_MS = 1_000;

/** Currency shown on the money figures (the platform trades in rupees). */
export const TODAY_CURRENCY = 'INR';
export const TODAY_LOCALE = 'en-IN';

/**
 * The screen's content, one card per application area. Every figure is read from an endpoint that already exists
 * (the dashboards of each application); nothing here needs a new API. A figure shows only when the user holds the
 * privilege of the endpoint behind it, and a card with no visible figure is left out.
 */
/** matrimonial-api: the signed-in agent's own desk figures (MATRIMONY_AGENT_DASHBOARD_READ), below environment.matrimonyApiUrl. */
export const TODAY_AGENT_DESK_PATH = '/agent/dashboard';

/** How many high-priority items the Priority card loads (the newest ones). */
export const TODAY_PRIORITY_LIMIT = 12;


export const TODAY_SECTIONS: readonly SectionSpec[] = [
  {
    id: 'waiting',
    titleKey: 'today.sections.waiting',
    icon: 'bi-hourglass-split',
    link: '/workflow',
    privilege: null,
    metrics: [
      { key: 'mine', icon: 'bi-person-check', labelKey: 'today.metrics.myTasks', path: 'mine', format: 'count', tone: 'pending', link: '/workflow', privilege: 'WORKFLOW_TASK_LIST' },
      { key: 'unassigned', icon: 'bi-inbox', labelKey: 'today.metrics.unassignedTasks', path: 'unassigned', format: 'count', tone: 'pending', link: '/workflow', privilege: 'WORKFLOW_TASK_LIST' },
      { key: 'overdue', icon: 'bi-alarm', labelKey: 'today.metrics.overdueTasks', path: 'overdue', format: 'count', tone: 'bad', link: '/workflow', privilege: 'WORKFLOW_TASK_LIST' },
      { key: 'reviews', icon: 'bi-patch-check', labelKey: 'today.metrics.changeRequests', path: 'reviews', format: 'count', tone: 'pending', link: '/admin/change-requests', privilege: 'WORKFLOW_CHANGE_REQUEST_LIST_READ' },
      { key: 'unread', icon: 'bi-bell', labelKey: 'today.metrics.unreadNotifications', path: 'unread', format: 'count', tone: 'pending', link: '/notifications' },
    ],
  },
  // The matrimony agent's own desk: their customers, open leads, overdue requests, meetings this week and successes. Agents and
  // owners hold the agent desk privileges; HQ staff do not, so they never see this card.
  {
    id: 'agentDesk',
    titleKey: 'today.sections.agentDesk',
    icon: 'bi-person-workspace',
    link: '/matrimony/agent',
    privilege: 'MATRIMONY_AGENT_DASHBOARD_READ',
    metrics: [
      { key: 'customers', icon: 'bi-people', labelKey: 'today.metrics.myCustomers', path: 'customers', format: 'count', tone: 'plain', link: '/matrimony/agent' },
      { key: 'leads', icon: 'bi-chat-heart', labelKey: 'today.metrics.openLeads', path: 'openLeads', format: 'count', tone: 'pending', link: '/matrimony/agent/proposals' },
      { key: 'overdue', icon: 'bi-alarm', labelKey: 'today.metrics.overdueRequests', path: 'overdueRequests', format: 'count', tone: 'bad', link: '/matrimony/agent/proposals' },
      { key: 'meetings', icon: 'bi-calendar-event', labelKey: 'today.metrics.meetingsThisWeek', path: 'meetingsThisWeek', format: 'count', tone: 'plain', link: '/matrimony/agent/proposals' },
      { key: 'successes', icon: 'bi-stars', labelKey: 'today.metrics.successes', path: 'successes', format: 'count', tone: 'plain' },
    ],
  },
  // A franchise member's own earnings (owners and agents alike), summed over their earnings accounts.
  {
    id: 'earnings',
    titleKey: 'today.sections.earnings',
    icon: 'bi-cash-coin',
    link: '/access-management/my-earnings',
    privilege: 'COMMISSION_STATEMENT_MINE_READ',
    metrics: [
      { key: 'available', icon: 'bi-wallet2', labelKey: 'today.metrics.earningsAvailable', path: 'available', format: 'currency', tone: 'plain', link: '/access-management/my-payouts' },
      { key: 'pending', icon: 'bi-hourglass-split', labelKey: 'today.metrics.earningsPending', path: 'pending', format: 'currency', tone: 'plain', link: '/access-management/my-earnings' },
      { key: 'settled', icon: 'bi-check2-circle', labelKey: 'today.metrics.earningsSettled', path: 'settled', format: 'currency', tone: 'plain', link: '/access-management/my-earnings' },
    ],
  },
  {
    id: 'workspace',
    titleKey: 'today.sections.workspace',
    icon: 'bi-journal-check',
    link: '/workspace',
    privilege: 'WORKSPACE_SUMMARY_READ',
    metrics: [
      { key: 'todos', icon: 'bi-check2-square', labelKey: 'today.metrics.openTodos', path: 'openTodos', format: 'count', tone: 'pending', link: '/workspace' },
      { key: 'assigned', icon: 'bi-people', labelKey: 'today.metrics.assignedTasks', path: 'openAssigned', format: 'count', tone: 'pending', link: '/workspace' },
      { key: 'overdue', icon: 'bi-alarm', labelKey: 'today.metrics.overdueItems', path: 'overdue', format: 'count', tone: 'bad', link: '/workspace' },
      { key: 'reminders', icon: 'bi-bell', labelKey: 'today.metrics.remindersDue', path: 'remindersDue', format: 'count', tone: 'pending', link: '/workspace' },
    ],
  },
  {
    id: 'matrimony',
    titleKey: 'today.sections.matrimony',
    icon: 'bi-heart',
    link: '/matrimony/dashboard',
    privilege: 'MATRIMONY_ANALYTICS_SUMMARY_READ',
    metrics: [
      { key: 'profiles', icon: 'bi-person-lines-fill', labelKey: 'today.metrics.profilesToReview', path: 'pendingReviewProfiles', format: 'count', tone: 'pending', link: '/matrimony/profile-review' },
      { key: 'reports', icon: 'bi-flag', labelKey: 'today.metrics.reportsPending', path: 'pendingReports', format: 'count', tone: 'pending', link: '/matrimony/reports' },
      { key: 'stories', icon: 'bi-stars', labelKey: 'today.metrics.storiesPending', path: 'pendingSuccessStories', format: 'count', tone: 'pending', link: '/matrimony/success-stories' },
      { key: 'interests', icon: 'bi-chat-heart', labelKey: 'today.metrics.interestsPending', path: 'pendingInterests', format: 'count', tone: 'plain' },
      { key: 'members', icon: 'bi-people', labelKey: 'today.metrics.activeMembers', path: 'activeUsers', format: 'count', tone: 'plain' },
    ],
  },
  {
    id: 'train',
    titleKey: 'today.sections.train',
    icon: 'bi-train-front',
    link: '/admin/train-admin/dashboard',
    privilege: 'TRAIN_ADMIN_DASHBOARD_SUMMARY_READ',
    metrics: [
      { key: 'bookings', icon: 'bi-ticket-perforated', labelKey: 'today.metrics.bookingsToday', path: 'metrics.bookingsToday', format: 'count', tone: 'plain', link: '/admin/train-admin/bookings' },
      { key: 'revenue', icon: 'bi-currency-rupee', labelKey: 'today.metrics.revenueToday', path: 'metrics.revenueToday', format: 'currency', tone: 'plain' },
      { key: 'success', icon: 'bi-graph-up-arrow', labelKey: 'today.metrics.successRate', path: 'metrics.successRate', format: 'percent', tone: 'plain' },
      { key: 'pending', icon: 'bi-hourglass-split', labelKey: 'today.metrics.pendingBookings', path: 'metrics.pendingBookings', format: 'count', tone: 'pending', link: '/admin/train-admin/bookings' },
      { key: 'failed', icon: 'bi-x-octagon', labelKey: 'today.metrics.failedBookings', path: 'metrics.failedBookings', format: 'count', tone: 'bad', link: '/admin/train-admin/bookings' },
      { key: 'refunds', icon: 'bi-arrow-counterclockwise', labelKey: 'today.metrics.refundsToday', path: 'metrics.refundsToday', format: 'count', tone: 'pending', link: '/admin/train-admin/refunds' },
      { key: 'tdrs', icon: 'bi-file-earmark-text', labelKey: 'today.metrics.pendingTdrs', path: 'metrics.pendingTdrs', format: 'count', tone: 'pending', link: '/admin/train-admin/tdr' },
    ],
  },
  {
    id: 'operations',
    titleKey: 'today.sections.operations',
    icon: 'bi-car-front',
    link: '/admin/dashboard',
    privilege: 'ADMIN_DASHBOARD_SUMMARY_READ',
    metrics: [
      { key: 'liveRides', icon: 'bi-geo-alt', labelKey: 'today.metrics.liveRides', path: 'live.live_rides', format: 'count', tone: 'plain', link: '/admin/rides' },
      { key: 'activeDrivers', icon: 'bi-person-badge', labelKey: 'today.metrics.activeDrivers', path: 'live.active_drivers', format: 'count', tone: 'plain', link: '/admin/drivers' },
      { key: 'rides', icon: 'bi-car-front', labelKey: 'today.metrics.ridesToday', path: 'today.total_rides', format: 'count', tone: 'plain' },
      { key: 'revenue', icon: 'bi-currency-rupee', labelKey: 'today.metrics.revenueToday', path: 'today.revenue', format: 'currency', tone: 'plain' },
      { key: 'cancellations', icon: 'bi-slash-circle', labelKey: 'today.metrics.cancellationsToday', path: 'today.cancellations', format: 'count', tone: 'bad' },
      { key: 'drivers', icon: 'bi-person-check', labelKey: 'today.metrics.driversPending', path: 'pending_approvals.drivers', format: 'count', tone: 'pending', link: '/admin/drivers' },
      { key: 'vendors', icon: 'bi-shop', labelKey: 'today.metrics.vendorsPending', path: 'pending_approvals.vendors', format: 'count', tone: 'pending', link: '/admin/vendors' },
    ],
  },
  {
    id: 'wallet',
    titleKey: 'today.sections.wallet',
    icon: 'bi-wallet2',
    link: '/wallet/admin',
    privilege: 'WALLET_ADMIN_OVERVIEW_READ',
    metrics: [
      { key: 'liability', icon: 'bi-bank', labelKey: 'today.metrics.totalLiability', path: 'totalLiability', format: 'currency', tone: 'plain', link: '/wallet/admin' },
      { key: 'customers', icon: 'bi-people', labelKey: 'today.metrics.customerBalances', path: 'customerOutstanding', format: 'currency', tone: 'plain' },
      { key: 'float', icon: 'bi-wallet2', labelKey: 'today.metrics.agentFloat', path: 'agentFloatOutstanding', format: 'currency', tone: 'plain' },
      { key: 'earnings', icon: 'bi-cash-coin', labelKey: 'today.metrics.agentEarningsOwed', path: 'agentEarningsOwed', format: 'currency', tone: 'plain' },
    ],
  },
];
