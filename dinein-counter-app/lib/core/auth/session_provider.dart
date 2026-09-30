import 'package:equatable/equatable.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../network/api_client.dart';
import 'session.dart';

enum SessionStatus { restoring, signedOut, signedIn }

class SessionState extends Equatable {
  const SessionState({required this.status, this.user, this.error, this.busy = false});

  final SessionStatus status;
  final StaffUser? user;
  final String? error;
  final bool busy;

  @override
  List<Object?> get props => [status, user, error, busy];
}

final sessionProvider = StateNotifierProvider<SessionNotifier, SessionState>((ref) {
  return SessionNotifier(ref.watch(sessionManagerProvider));
});

class SessionNotifier extends StateNotifier<SessionState> {
  SessionNotifier(this._manager) : super(const SessionState(status: SessionStatus.restoring)) {
    _restore();
  }

  final SessionManager _manager;

  Future<void> _restore() async {
    try {
      final user = await _manager.restore();
      state = user == null
          ? const SessionState(status: SessionStatus.signedOut)
          : SessionState(status: SessionStatus.signedIn, user: user);
    } catch (e) {
      // Could not reach the server. The saved login is kept, so trying again later still works.
      state = SessionState(status: SessionStatus.signedOut, error: apiMessage(e));
    }
  }

  Future<void> signIn(String username, String secret, {required bool isPin}) async {
    state = const SessionState(status: SessionStatus.signedOut, busy: true);
    try {
      final user = await _manager.login(
        username,
        password: isPin ? null : secret,
        pin: isPin ? secret : null,
      );
      state = SessionState(status: SessionStatus.signedIn, user: user);
    } catch (e) {
      state = SessionState(status: SessionStatus.signedOut, error: apiMessage(e));
    }
  }

  Future<void> signOut() async {
    await _manager.logout();
    state = const SessionState(status: SessionStatus.signedOut);
  }

  /// The server ended the session (refresh token expired or revoked).
  void expired() {
    if (state.status == SessionStatus.signedOut) return;
    state = SessionState(status: SessionStatus.signedOut, error: const SessionExpired().toString());
  }
}
