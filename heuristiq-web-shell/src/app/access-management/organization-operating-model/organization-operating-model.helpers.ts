import { OPERATING_MODELS } from './organization-operating-model.constants';
import { OperatingModel, OperatingModelView, ScheduledOperatingModelChange } from './organization-operating-model.types';

/** The changes that have not started yet, in the shape UAM takes them. */
export function futureChanges(view: OperatingModelView): ScheduledOperatingModelChange[] {
  return view.schedule.filter((c) => !c.inForce).map((c) => ({ model: c.model, effectiveFrom: c.effectiveFrom }));
}

/** The model in force after every change in the schedule (today's when nothing is scheduled). */
export function lastModel(view: OperatingModelView): OperatingModel {
  return view.schedule.length ? view.schedule[view.schedule.length - 1].model : view.currentModel;
}

/** The models a new change may switch to: every model except the one in force before it (UAM refuses that one). */
export function switchOptions(view: OperatingModelView): OperatingModel[] {
  const last = lastModel(view);
  return OPERATING_MODELS.filter((model) => model !== last);
}

/** The first day a new change may start: today, or the day after the last scheduled change when that is later. */
export function earliestStart(view: OperatingModelView): string {
  const last = view.schedule.length ? view.schedule[view.schedule.length - 1].effectiveFrom : null;
  if (!last || last < view.today) return view.today;
  const next = new Date(`${last}T00:00:00Z`);
  next.setUTCDate(next.getUTCDate() + 1);
  return next.toISOString().slice(0, 10);
}
