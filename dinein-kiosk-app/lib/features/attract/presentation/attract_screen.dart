import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/branding/branding.dart';
import '../../../core/config/app_config.dart';
import '../../../l10n/app_localizations.dart';
import '../../session/session_provider.dart';

/// The welcome page. Every string, colour and image here comes from [Branding].
class AttractScreen extends ConsumerStatefulWidget {
  const AttractScreen({super.key});

  @override
  ConsumerState<AttractScreen> createState() => _AttractScreenState();
}

class _AttractScreenState extends ConsumerState<AttractScreen> {
  @override
  void initState() {
    super.initState();
    // Whatever brings the kiosk back here, the next customer starts clean.
    Future.microtask(() => ref.read(kioskSessionProvider).reset());
  }

  @override
  Widget build(BuildContext context) {
    final branding = ref.watch(brandingProvider).valueOrNull ?? Branding.defaults;
    final background = AppConfig.resolveUrl(branding.backgroundUrl);
    final logo = AppConfig.resolveUrl(branding.logoUrl);

    return Scaffold(
      body: Stack(
        fit: StackFit.expand,
        children: [
          GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: () => context.go('/order-type'),
            child: Stack(
              fit: StackFit.expand,
              children: [
                if (background != null)
                  CachedNetworkImage(
                    imageUrl: background,
                    fit: BoxFit.cover,
                    errorWidget: (_, _, _) => const SizedBox.shrink(),
                  ),
                DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        branding.primary.withValues(alpha: background == null ? 1 : 0.55),
                        branding.secondary.withValues(alpha: background == null ? 1 : 0.85),
                      ],
                    ),
                  ),
                ),
                SafeArea(
                  child: Padding(
                    padding: const EdgeInsets.all(48),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        if (logo != null)
                          CachedNetworkImage(
                            imageUrl: logo,
                            height: 140,
                            errorWidget: (_, _, _) => const SizedBox.shrink(),
                          )
                        else
                          Text(
                            branding.restaurantName,
                            textAlign: TextAlign.center,
                            style: Theme.of(context).textTheme.displaySmall?.copyWith(
                                  color: Colors.white,
                                  fontWeight: FontWeight.w800,
                                ),
                          ),
                        const SizedBox(height: 48),
                        Text(
                          branding.headline,
                          textAlign: TextAlign.center,
                          style: Theme.of(context).textTheme.displayMedium?.copyWith(
                                color: Colors.white,
                                fontWeight: FontWeight.w900,
                              ),
                        ),
                        const SizedBox(height: 16),
                        Text(
                          branding.subtext,
                          textAlign: TextAlign.center,
                          style: Theme.of(context)
                              .textTheme
                              .headlineSmall
                              ?.copyWith(color: Colors.white70),
                        ),
                        const SizedBox(height: 64),
                        _PulsingButton(label: branding.startButtonLabel),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
          const Positioned(left: 0, right: 0, bottom: 0, child: _VisitorOptions()),
        ],
      ),
    );
  }
}

/// Language and large-text choices for this visit. Sits outside the "touch to order" area so
/// choosing one does not start an order.
class _VisitorOptions extends ConsumerWidget {
  const _VisitorOptions();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final locale = ref.watch(localeProvider);
    final large = ref.watch(accessibilityModeProvider);
    final l10n = AppLocalizations.of(context);

    Widget chip(String label, bool selected, VoidCallback onTap) => ChoiceChip(
          label: Text(label, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
          selected: selected,
          onSelected: (_) => onTap(),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          showCheckmark: false,
        );

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Wrap(
          alignment: WrapAlignment.center,
          spacing: 12,
          runSpacing: 12,
          children: [
            chip('English', locale.languageCode == 'en',
                () => ref.read(localeProvider.notifier).state = const Locale('en')),
            chip('हिंदी', locale.languageCode == 'hi',
                () => ref.read(localeProvider.notifier).state = const Locale('hi')),
            FilterChip(
              avatar: const Icon(Icons.text_increase_rounded),
              label: Text(large ? l10n.standardText : l10n.largeText,
                  style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
              selected: large,
              onSelected: (v) => ref.read(accessibilityModeProvider.notifier).state = v,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              showCheckmark: false,
            ),
          ],
        ),
      ),
    );
  }
}

class _PulsingButton extends StatefulWidget {
  const _PulsingButton({required this.label});

  final String label;

  @override
  State<_PulsingButton> createState() => _PulsingButtonState();
}

class _PulsingButtonState extends State<_PulsingButton>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1200),
  )..repeat(reverse: true);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ScaleTransition(
      scale: Tween(begin: 1.0, end: 1.06).animate(
        CurvedAnimation(parent: _controller, curve: Curves.easeInOut),
      ),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 56, vertical: 28),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(48),
        ),
        child: Text(
          widget.label,
          style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                color: Theme.of(context).colorScheme.primary,
                fontWeight: FontWeight.w900,
              ),
        ),
      ),
    );
  }
}
