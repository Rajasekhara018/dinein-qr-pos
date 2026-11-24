export type NmsChannel = 'EMAIL' | 'SMS' | 'WHATSAPP' | 'FCM_PUSH';

export interface NmsEventDef {
  id?: string;
  value: string;
  label: string;
  attributes: string[];
}

export const NMS_EVENTS: NmsEventDef[] = [
  {
    value: 'BOOKING_CONFIRMED',
    label: 'Booking Confirmed',
    attributes: ['{{bookingId}}', '{{passengerName}}', '{{pickupLocation}}', '{{dropLocation}}', '{{amount}}', '{{driverName}}', '{{vehicleNumber}}', '{{bookingDate}}'],
  },
  {
    value: 'BOOKING_CANCELLED',
    label: 'Booking Cancelled',
    attributes: ['{{bookingId}}', '{{passengerName}}', '{{cancellationReason}}', '{{refundAmount}}', '{{cancellationTime}}'],
  },
  {
    value: 'BOOKING_MODIFIED',
    label: 'Booking Modified',
    attributes: ['{{bookingId}}', '{{passengerName}}', '{{newPickupLocation}}', '{{newDropLocation}}', '{{modifiedAt}}'],
  },
  {
    value: 'DRIVER_ASSIGNED',
    label: 'Driver Assigned',
    attributes: ['{{bookingId}}', '{{driverName}}', '{{driverPhone}}', '{{vehicleNumber}}', '{{vehicleModel}}', '{{eta}}'],
  },
  {
    value: 'DRIVER_ARRIVED',
    label: 'Driver Arrived',
    attributes: ['{{bookingId}}', '{{driverName}}', '{{vehicleNumber}}', '{{arrivalTime}}', '{{pickupLocation}}'],
  },
  {
    value: 'RIDE_STARTED',
    label: 'Ride Started',
    attributes: ['{{bookingId}}', '{{driverName}}', '{{vehicleNumber}}', '{{startTime}}', '{{pickupLocation}}'],
  },
  {
    value: 'RIDE_COMPLETED',
    label: 'Ride Completed',
    attributes: ['{{bookingId}}', '{{passengerName}}', '{{distance}}', '{{duration}}', '{{amount}}', '{{dropLocation}}', '{{completionTime}}'],
  },
  {
    value: 'PAYMENT_SUCCESSFUL',
    label: 'Payment Successful',
    attributes: ['{{bookingId}}', '{{passengerName}}', '{{amount}}', '{{transactionId}}', '{{paymentMethod}}', '{{paidAt}}'],
  },
  {
    value: 'PAYMENT_FAILED',
    label: 'Payment Failed',
    attributes: ['{{bookingId}}', '{{passengerName}}', '{{amount}}', '{{failureReason}}', '{{failedAt}}'],
  },
  {
    value: 'PAYMENT_LINK_SENT',
    label: 'Payment Link Sent',
    attributes: ['{{bookingId}}', '{{passengerName}}', '{{paymentLink}}', '{{amount}}', '{{expiryTime}}'],
  },
  {
    value: 'REFUND_PROCESSED',
    label: 'Refund Processed',
    attributes: ['{{bookingId}}', '{{passengerName}}', '{{refundAmount}}', '{{refundId}}', '{{refundedAt}}', '{{paymentMethod}}'],
  },
  {
    value: 'OTP_REQUEST',
    label: 'OTP Request',
    attributes: ['{{otp}}', '{{passengerName}}', '{{expiryMinutes}}', '{{purpose}}'],
  },
  {
    value: 'PASSWORD_RESET',
    label: 'Password Reset',
    attributes: ['{{passengerName}}', '{{resetLink}}', '{{expiryHours}}'],
  },
  {
    value: 'ACCOUNT_CREATED',
    label: 'Account Created',
    attributes: ['{{passengerName}}', '{{email}}', '{{phone}}', '{{verificationLink}}'],
  },
  {
    value: 'PROMO_ALERT',
    label: 'Promo Alert',
    attributes: ['{{passengerName}}', '{{promoCode}}', '{{discountPercent}}', '{{validTill}}', '{{promoTitle}}'],
  },
  {
    value: 'TRIP_REMINDER',
    label: 'Trip Reminder',
    attributes: ['{{passengerName}}', '{{pickupTime}}', '{{pickupLocation}}', '{{bookingId}}', '{{reminderType}}'],
  },
  {
    value: 'SYSTEM_ALERT',
    label: 'System Alert',
    attributes: ['{{alertTitle}}', '{{alertBody}}', '{{alertTime}}', '{{severity}}'],
  },
];

// NOTE: label/description are translation keys, not display text -- template-landing.ts resolves
// them via TranslateService before handing the array to its template (this is a static object
// built once at module load, before TranslateService has an active language).
export const NMS_CHANNELS: { value: NmsChannel; label: string; icon: string; description: string }[] = [
  { value: 'EMAIL',    label: 'nmsTemplates.landing.channels.email.label',    icon: 'bi-envelope-fill',  description: 'nmsTemplates.landing.channels.email.description'    },
  { value: 'SMS',      label: 'nmsTemplates.landing.channels.sms.label',      icon: 'bi-chat-text-fill', description: 'nmsTemplates.landing.channels.sms.description'      },
  { value: 'WHATSAPP', label: 'nmsTemplates.landing.channels.whatsapp.label', icon: 'bi-whatsapp',       description: 'nmsTemplates.landing.channels.whatsapp.description' },
  { value: 'FCM_PUSH', label: 'nmsTemplates.landing.channels.push.label',     icon: 'bi-bell-fill',      description: 'nmsTemplates.landing.channels.push.description'     },
];

export function getEventAttributes(eventValue: string): string[] {
  return NMS_EVENTS.find(e => e.value === eventValue)?.attributes ?? [];
}

export function getEventLabel(eventValue: string): string {
  return NMS_EVENTS.find(e => e.value === eventValue)?.label ?? eventValue;
}
