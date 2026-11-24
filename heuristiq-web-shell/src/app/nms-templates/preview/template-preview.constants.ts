import { NmsChannel } from '../event-attributes.config';

export type BodyMode = 'visual' | 'html' | 'preview';
export type PreviewDevice = 'desktop' | 'mobile';

/** Channels whose body is rendered as an HTML page; the others are shown as plain text. */
export const HTML_PREVIEW_CHANNELS: readonly NmsChannel[] = ['EMAIL'];

/** Markup the visual (Quill) editor would rewrite, so such bodies are edited as HTML only. */
export const ADVANCED_HTML_PATTERN = /<\s*(html|head|body|style|table|meta|link)\b/i;

/** Same placeholder syntax the NMS TemplateRenderer substitutes: {{name}}. */
export const PLACEHOLDER_PATTERN = /\{\{(\w+)\}\}/g;

/** Channels a test message can be sent for; push and in-app need a device, not a typed recipient. */
export const TEST_SEND_CHANNELS: readonly NmsChannel[] = ['EMAIL', 'SMS', 'WHATSAPP'];

export const PREVIEW_FRAME_WIDTH: Record<PreviewDevice, string> = {
  desktop: '100%',
  mobile: '375px',
};

/** Sample value rules, first match wins; the name is compared in lower case. Never saved or sent. */
export const SAMPLE_VALUE_RULES: ReadonlyArray<{ test: RegExp; value: string }> = [
  { test: /otp|code|pin/, value: '482913' },
  { test: /email/, value: 'asha.rao@example.com' },
  { test: /phone|mobile/, value: '+91 98765 43210' },
  { test: /link|url/, value: 'https://example.com/open' },
  { test: /amount|fare|price|total|refund|balance/, value: '450.00' },
  { test: /date|time|eta|at$/, value: '18 Oct 2026, 10:30' },
  { test: /expiry|minutes|hours|days/, value: '10' },
  { test: /lat/, value: '12.9716' },
  { test: /lng|lon/, value: '77.5946' },
  { test: /vehicle(number)?$/, value: 'KA 01 AB 1234' },
  { test: /name/, value: 'Asha Rao' },
  { test: /reason/, value: 'Document is not clear' },
  { test: /reference|id$/, value: 'REF-10042' },
];
