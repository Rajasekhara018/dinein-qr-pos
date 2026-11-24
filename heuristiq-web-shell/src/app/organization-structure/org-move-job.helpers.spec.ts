import { matchPositionForMove } from './org-structure.helpers';

describe('matchPositionForMove', () => {
  const ladder = [{ id: 'p1', name: 'Agent' }, { id: 'p2', name: 'Agent Manager' }];

  it('keeps the same job when the ladder has it', () => {
    expect(matchPositionForMove(ladder, 'p2', 'whatever')?.id).toBe('p2');
  });

  it('finds the job with the same name in another ladder, ignoring case', () => {
    expect(matchPositionForMove(ladder, 'other', '  agent ')?.id).toBe('p1');
  });

  it('is null when the ladder has no such job', () => {
    expect(matchPositionForMove(ladder, 'other', 'Director')).toBeNull();
    expect(matchPositionForMove([], 'p1', 'Agent')).toBeNull();
    expect(matchPositionForMove(ladder, null, null)).toBeNull();
  });
});
