import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/session_provider.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _username = TextEditingController();
  final _secret = TextEditingController();
  bool _usePin = true;

  @override
  void dispose() {
    _username.dispose();
    _secret.dispose();
    super.dispose();
  }

  void _submit() {
    if (_username.text.trim().isEmpty || _secret.text.isEmpty) return;
    ref.read(sessionProvider.notifier).signIn(_username.text, _secret.text, isPin: _usePin);
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(sessionProvider);
    final text = Theme.of(context).textTheme;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Icon(Icons.point_of_sale_rounded,
                      size: 72, color: Theme.of(context).colorScheme.primary),
                  const SizedBox(height: 12),
                  Text('Counter POS',
                      textAlign: TextAlign.center,
                      style: text.headlineMedium?.copyWith(fontWeight: FontWeight.w800)),
                  const SizedBox(height: 24),
                  SegmentedButton<bool>(
                    segments: const [
                      ButtonSegment(value: true, label: Text('PIN')),
                      ButtonSegment(value: false, label: Text('Password')),
                    ],
                    selected: {_usePin},
                    onSelectionChanged: session.busy
                        ? null
                        : (s) => setState(() {
                              _usePin = s.first;
                              _secret.clear();
                            }),
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    controller: _username,
                    enabled: !session.busy,
                    autofocus: true,
                    textInputAction: TextInputAction.next,
                    decoration: const InputDecoration(
                        labelText: 'Username', border: OutlineInputBorder()),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _secret,
                    enabled: !session.busy,
                    obscureText: true,
                    keyboardType: _usePin ? TextInputType.number : TextInputType.visiblePassword,
                    inputFormatters: _usePin
                        ? [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(6)]
                        : null,
                    decoration: InputDecoration(
                      labelText: _usePin ? 'PIN' : 'Password',
                      border: const OutlineInputBorder(),
                    ),
                    onSubmitted: (_) => _submit(),
                  ),
                  if (session.error != null) ...[
                    const SizedBox(height: 12),
                    Text(session.error!,
                        style: text.bodyLarge?.copyWith(color: Theme.of(context).colorScheme.error)),
                  ],
                  const SizedBox(height: 20),
                  FilledButton(
                    onPressed: session.busy ? null : _submit,
                    child: session.busy
                        ? const SizedBox(
                            width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 3))
                        : const Text('Sign in'),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'PIN sign-in is for waiter accounts. Managers and owners use their password.',
                    textAlign: TextAlign.center,
                    style: text.bodySmall,
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
