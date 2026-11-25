package com.heuristiq.notification.domain.model;

import java.util.List;

import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.domain.model.NotificationEvent;

/**
 * Predefined content for the notification events every app needs out of the box
 * (auth: OTP/password-reset/account-created; payments: success/failed). Styled after the
 * production email/SMS/WhatsApp templates used in the PayEase gateway-backend, reskinned for
 * RailCab and wired to RailCab's own event placeholders (see EventAttributesConfig).
 *
 * Seeded once by {@link TemplateService}; edit/replace via the normal
 * {@code /api/v1/nms/templates} CRUD afterwards — seeding never overwrites existing rows.
 */
public final class DefaultNotificationTemplates {

    private DefaultNotificationTemplates() {}

    public record Seed(String event, NotificationChannel channel, String name, String subject, String body) {}

    /** Placeholders the seed text uses for the brand; {@link TemplateService} fills them from {@code nms.branding.*}. */
    public static final String BRAND_NAME_TOKEN = "%BRAND_NAME%";
    public static final String SUPPORT_EMAIL_TOKEN = "%SUPPORT_EMAIL%";
    public static final String MATRIMONY_NAME_TOKEN = "%MATRIMONY_BRAND_NAME%";
    public static final String COLOR_ALERT_TOKEN = "%COLOR_ALERT%";
    public static final String COLOR_SUCCESS_TOKEN = "%COLOR_SUCCESS%";
    public static final String COLOR_APPROVED_TOKEN = "%COLOR_APPROVED%";
    public static final String COLOR_DANGER_TOKEN = "%COLOR_DANGER%";
    public static final String COLOR_WARNING_TOKEN = "%COLOR_WARNING%";
    public static final String BRAND_PRIMARY_TINT_TOKEN = "%BRAND_PRIMARY_TINT%";
    public static final String TAGLINE_TOKEN = "%BRAND_TAGLINE%";
    public static final String PRIMARY_COLOR_TOKEN = "%BRAND_PRIMARY%";
    public static final String ACCENT_COLOR_TOKEN = "%BRAND_ACCENT%";

    private static final String RS = "&#8377;";

    private static final List<Seed> BASE_DEFAULTS = List.of(

        // ── OTP_REQUEST ──────────────────────────────────────────────────────
        new Seed(
            NotificationEvent.OTP_REQUEST.name(), NotificationChannel.EMAIL,
            "OTP Request - Email",
            "%BRAND_NAME% - OTP Verification",
            html("%BRAND_PRIMARY%", "OTP Verification",
                "<p>Dear <strong>{{passengerName}}</strong>,</p>" +
                "<p>Your one-time password (OTP) for {{purpose}} is:</p>" +
                otpBox("{{otp}}") +
                "<p>This OTP is valid for <strong>{{expiryMinutes}} minute(s)</strong>. " +
                "Do <strong>not</strong> share it with anyone.</p>" +
                "<p style='color:#888;font-size:13px;'>If you did not request this OTP, " +
                "please ignore this email.</p>")
        ),
        new Seed(
            NotificationEvent.OTP_REQUEST.name(), NotificationChannel.SMS,
            "OTP Request - SMS",
            null,
            "%BRAND_NAME% OTP: {{otp}}\n" +
            "Valid for {{expiryMinutes}} min for {{purpose}}. DO NOT share this code with anyone."
        ),
        new Seed(
            NotificationEvent.OTP_REQUEST.name(), NotificationChannel.WHATSAPP,
            "OTP Request - WhatsApp",
            null,
            "*%BRAND_NAME% | OTP Verification*\n\n" +
            "Dear {{passengerName}},\n\n" +
            "Your one-time password (OTP) for {{purpose}} is:\n\n" +
            "*{{otp}}*\n\n" +
            "Valid for *{{expiryMinutes}} minute(s)*. Do not share this code with anyone.\n\n" +
            "If you did not request this OTP, please ignore this message."
        ),

        // ── PASSWORD_RESET ───────────────────────────────────────────────────
        new Seed(
            NotificationEvent.PASSWORD_RESET.name(), NotificationChannel.EMAIL,
            "Password Reset - Email",
            "%BRAND_NAME% - Reset Your Password",
            html("%COLOR_ALERT%", "Password Reset",
                "<p>Dear <strong>{{passengerName}}</strong>,</p>" +
                "<p>We received a request to reset your %BRAND_NAME% password. Click below to choose a new one:</p>" +
                button("{{resetLink}}", "Reset Password") +
                "<p>This link is valid for <strong>{{expiryHours}} hour(s)</strong>. " +
                "If you didn't request this, you can safely ignore this email.</p>")
        ),
        new Seed(
            NotificationEvent.PASSWORD_RESET.name(), NotificationChannel.SMS,
            "Password Reset - SMS",
            null,
            "%BRAND_NAME%: Reset your password using this link: {{resetLink}} (valid {{expiryHours}} hrs). " +
            "Ignore if not requested by you."
        ),

        // ── ACCOUNT_CREATED ──────────────────────────────────────────────────
        new Seed(
            NotificationEvent.ACCOUNT_CREATED.name(), NotificationChannel.EMAIL,
            "Account Created - Welcome Email",
            "Welcome to %BRAND_NAME%, {{passengerName}}!",
            html("%COLOR_SUCCESS%", "Account Created",
                badge("%COLOR_SUCCESS%", "ACCOUNT CREATED") +
                "<p>Dear <strong>{{passengerName}}</strong>,</p>" +
                "<p>Your %BRAND_NAME% account has been created successfully.</p>" +
                table(
                    row("Email", "{{email}}"),
                    row("Phone", "{{phone}}")) +
                button("{{verificationLink}}", "Verify My Account") +
                "<p>Welcome aboard!</p>")
        ),

        // ── PAYMENT_SUCCESSFUL ───────────────────────────────────────────────
        new Seed(
            NotificationEvent.PAYMENT_SUCCESSFUL.name(), NotificationChannel.EMAIL,
            "Payment Successful - Email",
            "%BRAND_NAME% - Payment Successful",
            html("%COLOR_SUCCESS%", "Payment Successful",
                "<p>Dear <strong>{{passengerName}}</strong>,</p>" +
                "<p>Your payment for booking <strong>{{bookingId}}</strong> has been received and confirmed.</p>" +
                badge("%COLOR_SUCCESS%", "PAID") +
                table(
                    row("Amount", RS + "{{amount}}"),
                    row("Payment via", "{{paymentMethod}}"),
                    row("Transaction ID", "{{transactionId}}"),
                    row("Date &amp; Time", "{{paidAt}}")) +
                "<p>Thank you for riding with %BRAND_NAME%.</p>")
        ),
        new Seed(
            NotificationEvent.PAYMENT_SUCCESSFUL.name(), NotificationChannel.SMS,
            "Payment Successful - SMS",
            null,
            "%BRAND_NAME% | Payment Successful\n" +
            "Booking {{bookingId}}: Rs.{{amount}} via {{paymentMethod}}\n" +
            "Txn ID: {{transactionId}}\n" +
            "{{paidAt}}\n" +
            "Thank you for riding with %BRAND_NAME%."
        ),

        // ── PAYMENT_FAILED ───────────────────────────────────────────────────
        new Seed(
            NotificationEvent.PAYMENT_FAILED.name(), NotificationChannel.EMAIL,
            "Payment Failed - Email",
            "%BRAND_NAME% - Payment Failed",
            html("%COLOR_DANGER%", "Payment Failed",
                "<p>Dear <strong>{{passengerName}}</strong>,</p>" +
                "<p>We were unable to process your payment for booking <strong>{{bookingId}}</strong>.</p>" +
                badge("%COLOR_DANGER%", "FAILED") +
                table(
                    row("Amount", RS + "{{amount}}"),
                    row("Reason", "{{failureReason}}"),
                    row("Date &amp; Time", "{{failedAt}}")) +
                "<p>Please retry or contact <a href='mailto:%SUPPORT_EMAIL%'>%SUPPORT_EMAIL%</a>.</p>")
        ),
        new Seed(
            NotificationEvent.PAYMENT_FAILED.name(), NotificationChannel.SMS,
            "Payment Failed - SMS",
            null,
            "%BRAND_NAME% | Payment Failed\n" +
            "Booking {{bookingId}}: Rs.{{amount}} could not be processed.\n" +
            "Reason: {{failureReason}}\n" +
            "{{failedAt}}\n" +
            "Retry or contact %SUPPORT_EMAIL%"
        ),

        // ── WORKFLOW_TASK_ASSIGNED ─────────────────────────────────────────────────────
        new Seed(
            NotificationEvent.WORKFLOW_TASK_ASSIGNED.name(), NotificationChannel.IN_APP,
            "Workflow Task Assigned - In App",
            "New task: {{taskName}}",
            "{{taskName}} ({{subject}}) is waiting for your action. Due: {{dueDate}}."
        ),
        new Seed(
            NotificationEvent.WORKFLOW_TASK_ASSIGNED.name(), NotificationChannel.FCM_PUSH,
            "Workflow Task Assigned - Push",
            "New task: {{taskName}}",
            "{{taskName}} ({{subject}}) is waiting for your action. Due: {{dueDate}}."
        ),
        new Seed(
            NotificationEvent.WORKFLOW_TASK_ASSIGNED.name(), NotificationChannel.EMAIL,
            "Workflow Task Assigned - Email",
            "%BRAND_NAME% - New task: {{taskName}}",
            html("%BRAND_ACCENT%", "New task for you",
                "<p>Dear <strong>{{recipientName}}</strong>,</p>" +
                "<p>A new approval step is waiting for you.</p>" +
                table(
                    row("Step", "{{taskName}}"),
                    row("Process", "{{processName}}"),
                    row("Request", "{{subject}}"),
                    row("Due", "{{dueDate}}")) +
                button("{{taskLink}}", "Open task"))
        ),
        new Seed(
            NotificationEvent.WORKFLOW_TASK_ASSIGNED.name(), NotificationChannel.WHATSAPP,
            "Workflow Task Assigned - WhatsApp",
            null,
            "*%BRAND_NAME% | New task for you*\n\n" +
            "Dear {{recipientName}},\n\n" +
            "A new approval step is waiting for you.\n\n" +
            "Step: *{{taskName}}*\nRequest: {{subject}}\nDue: {{dueDate}}\n\n{{taskLink}}"
        ),
        new Seed(
            NotificationEvent.WORKFLOW_TASK_ASSIGNED.name(), NotificationChannel.SMS,
            "Workflow Task Assigned - SMS",
            null,
            "%BRAND_NAME%: New task {{taskName}} for {{subject}} awaits your action. Due {{dueDate}}. {{taskLink}}"
        ),
        // ── WORKFLOW_TASK_REASSIGNED ─────────────────────────────────────────────────────
        new Seed(
            NotificationEvent.WORKFLOW_TASK_REASSIGNED.name(), NotificationChannel.IN_APP,
            "Workflow Task Reassigned - In App",
            "Task reassigned: {{taskName}}",
            "{{reassignedBy}} reassigned {{taskName}} ({{subject}}) to you. Due: {{dueDate}}."
        ),
        new Seed(
            NotificationEvent.WORKFLOW_TASK_REASSIGNED.name(), NotificationChannel.FCM_PUSH,
            "Workflow Task Reassigned - Push",
            "Task reassigned: {{taskName}}",
            "{{reassignedBy}} reassigned {{taskName}} ({{subject}}) to you. Due: {{dueDate}}."
        ),
        new Seed(
            NotificationEvent.WORKFLOW_TASK_REASSIGNED.name(), NotificationChannel.EMAIL,
            "Workflow Task Reassigned - Email",
            "%BRAND_NAME% - Task reassigned: {{taskName}}",
            html("%BRAND_PRIMARY%", "Task reassigned to you",
                "<p>Dear <strong>{{recipientName}}</strong>,</p>" +
                "<p>{{reassignedBy}} has handed this approval step to you.</p>" +
                table(
                    row("Step", "{{taskName}}"),
                    row("Process", "{{processName}}"),
                    row("Request", "{{subject}}"),
                    row("Due", "{{dueDate}}")) +
                button("{{taskLink}}", "Open task"))
        ),
        new Seed(
            NotificationEvent.WORKFLOW_TASK_REASSIGNED.name(), NotificationChannel.WHATSAPP,
            "Workflow Task Reassigned - WhatsApp",
            null,
            "*%BRAND_NAME% | Task reassigned to you*\n\n" +
            "Dear {{recipientName}},\n\n" +
            "{{reassignedBy}} has handed this approval step to you.\n\n" +
            "Step: *{{taskName}}*\nRequest: {{subject}}\nDue: {{dueDate}}\n\n{{taskLink}}"
        ),
        new Seed(
            NotificationEvent.WORKFLOW_TASK_REASSIGNED.name(), NotificationChannel.SMS,
            "Workflow Task Reassigned - SMS",
            null,
            "%BRAND_NAME%: {{reassignedBy}} reassigned {{taskName}} for {{subject}} to you. Due {{dueDate}}. {{taskLink}}"
        ),
        // ── WORKFLOW_TASK_OVERDUE ─────────────────────────────────────────────────────
        new Seed(
            NotificationEvent.WORKFLOW_TASK_OVERDUE.name(), NotificationChannel.IN_APP,
            "Workflow Task Overdue - In App",
            "Overdue: {{taskName}}",
            "{{taskName}} ({{subject}}) missed its deadline of {{dueDate}}. Please act now."
        ),
        new Seed(
            NotificationEvent.WORKFLOW_TASK_OVERDUE.name(), NotificationChannel.FCM_PUSH,
            "Workflow Task Overdue - Push",
            "Overdue: {{taskName}}",
            "{{taskName}} ({{subject}}) missed its deadline of {{dueDate}}. Please act now."
        ),
        new Seed(
            NotificationEvent.WORKFLOW_TASK_OVERDUE.name(), NotificationChannel.EMAIL,
            "Workflow Task Overdue - Email",
            "%BRAND_NAME% - Overdue: {{taskName}}",
            html("%COLOR_DANGER%", "Task overdue",
                "<p>Dear <strong>{{recipientName}}</strong>,</p>" +
                "<p>This approval step has passed its deadline and still needs action.</p>" +
                table(
                    row("Step", "{{taskName}}"),
                    row("Process", "{{processName}}"),
                    row("Request", "{{subject}}"),
                    row("Due", "{{dueDate}}")) +
                button("{{taskLink}}", "Open task"))
        ),
        new Seed(
            NotificationEvent.WORKFLOW_TASK_OVERDUE.name(), NotificationChannel.WHATSAPP,
            "Workflow Task Overdue - WhatsApp",
            null,
            "*%BRAND_NAME% | Task overdue*\n\n" +
            "Dear {{recipientName}},\n\n" +
            "This approval step has passed its deadline and still needs action.\n\n" +
            "Step: *{{taskName}}*\nRequest: {{subject}}\nDue: {{dueDate}}\n\n{{taskLink}}"
        ),
        new Seed(
            NotificationEvent.WORKFLOW_TASK_OVERDUE.name(), NotificationChannel.SMS,
            "Workflow Task Overdue - SMS",
            null,
            "%BRAND_NAME%: OVERDUE - {{taskName}} for {{subject}} was due {{dueDate}}. {{taskLink}}"
        ),

        // ── SOS (Rider_RideService). English drafts; other languages are admin data. See docs/SOS_TEMPLATES.md ──
        new Seed(
            NotificationEvent.SOS_ALERT_RAISED.name(), NotificationChannel.IN_APP,
            "SOS Alert Raised - In App",
            "SOS raised",
            "SOS alert {{alertId}} raised for ride {{rideId}} by user {{userId}}. Last location: " +
            "https://maps.google.com/?q={{lat}},{{lng}}. Please acknowledge and respond now."
        ),
        new Seed(
            NotificationEvent.SOS_ALERT_RAISED.name(), NotificationChannel.FCM_PUSH,
            "SOS Alert Raised - Push",
            "SOS raised",
            "SOS alert {{alertId}} raised for ride {{rideId}} by user {{userId}}. Last location: " +
            "https://maps.google.com/?q={{lat}},{{lng}}. Please acknowledge and respond now."
        ),
        new Seed(
            NotificationEvent.SOS_ALERT_ESCALATED.name(), NotificationChannel.IN_APP,
            "SOS Alert Escalated - In App",
            "SOS not acknowledged",
            "SOS alert {{alertId}} for ride {{rideId}} (user {{userId}}) has not been acknowledged and is escalated. " +
            "Last location: https://maps.google.com/?q={{lat}},{{lng}}. Immediate action required."
        ),
        new Seed(
            NotificationEvent.SOS_ALERT_ESCALATED.name(), NotificationChannel.FCM_PUSH,
            "SOS Alert Escalated - Push",
            "SOS not acknowledged",
            "SOS alert {{alertId}} for ride {{rideId}} (user {{userId}}) has not been acknowledged and is escalated. " +
            "Last location: https://maps.google.com/?q={{lat}},{{lng}}. Immediate action required."
        ),
        new Seed(
            NotificationEvent.SOS_CONTACT_ALERT.name(), NotificationChannel.SMS,
            "SOS Contact Alert - SMS",
            null,
            "Hello {{contactName}}, a Best Ride customer who listed you as an emergency contact has raised an SOS alert " +
            "during ride {{rideId}}. Last known location: https://maps.google.com/?q={{lat}},{{lng}}. " +
            "If you cannot reach them, please call emergency services."
        ),

        // ── Early drop and help desk. English drafts (templates have no language column); admins can edit them. ──
        new Seed(
            NotificationEvent.EARLY_DROP_REQUESTED.name(), NotificationChannel.IN_APP,
            "Early Drop Requested - In App",
            "Driver asks to end the trip early",
            "Your driver would like to end this trip before the planned drop point. Open the app to accept or decline."
        ),
        new Seed(
            NotificationEvent.EARLY_DROP_REQUESTED.name(), NotificationChannel.FCM_PUSH,
            "Early Drop Requested - Push",
            "Driver asks to end the trip early",
            "Your driver would like to end this trip before the planned drop point. Open the app to accept or decline."
        ),
        new Seed(
            NotificationEvent.EARLY_DROP_ACCEPTED.name(), NotificationChannel.IN_APP,
            "Early Drop Accepted - In App",
            "Early drop accepted",
            "The customer agreed to end the trip at this point. The trip is now complete."
        ),
        new Seed(
            NotificationEvent.EARLY_DROP_ACCEPTED.name(), NotificationChannel.FCM_PUSH,
            "Early Drop Accepted - Push",
            "Early drop accepted",
            "The customer agreed to end the trip at this point. The trip is now complete."
        ),
        new Seed(
            NotificationEvent.EARLY_DROP_DECLINED.name(), NotificationChannel.IN_APP,
            "Early Drop Declined - In App",
            "Early drop declined",
            "The customer did not agree to end the trip early. Please continue to the planned drop point."
        ),
        new Seed(
            NotificationEvent.EARLY_DROP_DECLINED.name(), NotificationChannel.FCM_PUSH,
            "Early Drop Declined - Push",
            "Early drop declined",
            "The customer did not agree to end the trip early. Please continue to the planned drop point."
        ),
        new Seed(
            NotificationEvent.SUPPORT_TICKET_REPLY.name(), NotificationChannel.IN_APP,
            "Support Ticket Reply - In App",
            "Support replied to your ticket",
            "Support replied to your ticket {{ticketReference}}: {{subject}}. Open the app to read the reply."
        ),
        new Seed(
            NotificationEvent.SUPPORT_TICKET_REPLY.name(), NotificationChannel.FCM_PUSH,
            "Support Ticket Reply - Push",
            "Support replied to your ticket",
            "Support replied to your ticket {{ticketReference}}: {{subject}}. Open the app to read the reply."
        ),

        // ── WORKFLOW_TASK_DUE_SOON ─────────────────────────────────────────────────────
        new Seed(
            NotificationEvent.WORKFLOW_TASK_DUE_SOON.name(), NotificationChannel.IN_APP,
            "Workflow Task Due Soon - In App",
            "Due soon: {{taskName}}",
            "{{taskName}} ({{subject}}) is due {{dueDate}}. Please act soon."
        ),
        new Seed(
            NotificationEvent.WORKFLOW_TASK_DUE_SOON.name(), NotificationChannel.FCM_PUSH,
            "Workflow Task Due Soon - Push",
            "Due soon: {{taskName}}",
            "{{taskName}} ({{subject}}) is due {{dueDate}}. Please act soon."
        ),
        new Seed(
            NotificationEvent.WORKFLOW_TASK_DUE_SOON.name(), NotificationChannel.EMAIL,
            "Workflow Task Due Soon - Email",
            "%BRAND_NAME% - Due soon: {{taskName}}",
            html("%COLOR_WARNING%", "Task due soon",
                "<p>Dear <strong>{{recipientName}}</strong>,</p>" +
                "<p>This approval step is close to its deadline.</p>" +
                table(
                    row("Step", "{{taskName}}"),
                    row("Process", "{{processName}}"),
                    row("Request", "{{subject}}"),
                    row("Due", "{{dueDate}}")) +
                button("{{taskLink}}", "Open task"))
        ),
        new Seed(
            NotificationEvent.WORKFLOW_TASK_DUE_SOON.name(), NotificationChannel.WHATSAPP,
            "Workflow Task Due Soon - WhatsApp",
            null,
            "*%BRAND_NAME% | Task due soon*\n\n" +
            "Dear {{recipientName}},\n\n" +
            "This approval step is close to its deadline.\n\n" +
            "Step: *{{taskName}}*\nRequest: {{subject}}\nDue: {{dueDate}}\n\n{{taskLink}}"
        ),
        new Seed(
            NotificationEvent.WORKFLOW_TASK_DUE_SOON.name(), NotificationChannel.SMS,
            "Workflow Task Due Soon - SMS",
            null,
            "%BRAND_NAME%: {{taskName}} for {{subject}} is due {{dueDate}}. {{taskLink}}"
        ),

        // ── ONBOARDING_RECEIVED ─────────────────────────────────────────────────────
        new Seed(
            NotificationEvent.ONBOARDING_RECEIVED.name(), NotificationChannel.IN_APP,
            "Onboarding Received - In App",
            "Application received",
            "We received your application {{reference}}. We will update you after review."
        ),
        new Seed(
            NotificationEvent.ONBOARDING_RECEIVED.name(), NotificationChannel.FCM_PUSH,
            "Onboarding Received - Push",
            "Application received",
            "We received your application {{reference}}. We will update you after review."
        ),
        new Seed(
            NotificationEvent.ONBOARDING_RECEIVED.name(), NotificationChannel.EMAIL,
            "Onboarding Received - Email",
            "%BRAND_NAME% - Application received",
            html("%BRAND_ACCENT%", "Application received",
                "<p>Dear <strong>{{applicantName}}</strong>,</p>" +
                "<p>We have received your {{typeName}} application and it is now being reviewed. We will let you know once there is an update.</p>" +
                table(
                    row("Reference", "{{reference}}")))
        ),
        new Seed(
            NotificationEvent.ONBOARDING_RECEIVED.name(), NotificationChannel.WHATSAPP,
            "Onboarding Received - WhatsApp",
            null,
            "*%BRAND_NAME% | Application received*\n\n" +
            "Dear {{applicantName}},\n\n" +
            "We have received your {{typeName}} application and it is now being reviewed.\n\n" +
            "Reference: *{{reference}}*"
        ),
        new Seed(
            NotificationEvent.ONBOARDING_RECEIVED.name(), NotificationChannel.SMS,
            "Onboarding Received - SMS",
            null,
            "%BRAND_NAME%: We received your application {{reference}}. We will update you after review."
        ),

        // ── ONBOARDING_RETURNED ─────────────────────────────────────────────────────
        new Seed(
            NotificationEvent.ONBOARDING_RETURNED.name(), NotificationChannel.IN_APP,
            "Onboarding Returned - In App",
            "Action needed: {{reference}}",
            "Your application {{reference}} needs a correction: {{reason}}"
        ),
        new Seed(
            NotificationEvent.ONBOARDING_RETURNED.name(), NotificationChannel.FCM_PUSH,
            "Onboarding Returned - Push",
            "Action needed: {{reference}}",
            "Your application {{reference}} needs a correction: {{reason}}"
        ),
        new Seed(
            NotificationEvent.ONBOARDING_RETURNED.name(), NotificationChannel.EMAIL,
            "Onboarding Returned - Email",
            "%BRAND_NAME% - Action needed: {{reference}}",
            html("%COLOR_WARNING%", "Action needed on your application",
                "<p>Dear <strong>{{applicantName}}</strong>,</p>" +
                "<p>A reviewer needs you to correct something in your {{typeName}} application. Please open the app, fix it and submit again.</p>" +
                table(
                    row("Reference", "{{reference}}"),
                    row("Reason", "{{reason}}")))
        ),
        new Seed(
            NotificationEvent.ONBOARDING_RETURNED.name(), NotificationChannel.WHATSAPP,
            "Onboarding Returned - WhatsApp",
            null,
            "*%BRAND_NAME% | Action needed on your application*\n\n" +
            "Dear {{applicantName}},\n\n" +
            "A reviewer needs you to correct something in your application. Please open the app, fix it and submit again.\n\n" +
            "Reference: *{{reference}}*\n\nReason: {{reason}}"
        ),
        new Seed(
            NotificationEvent.ONBOARDING_RETURNED.name(), NotificationChannel.SMS,
            "Onboarding Returned - SMS",
            null,
            "%BRAND_NAME%: Your application {{reference}} needs a correction: {{reason}} Open the app to fix and resubmit."
        ),

        // ── ONBOARDING_APPROVED ─────────────────────────────────────────────────────
        new Seed(
            NotificationEvent.ONBOARDING_APPROVED.name(), NotificationChannel.IN_APP,
            "Onboarding Approved - In App",
            "Application approved",
            "Your application {{reference}} is approved. You can now log in."
        ),
        new Seed(
            NotificationEvent.ONBOARDING_APPROVED.name(), NotificationChannel.FCM_PUSH,
            "Onboarding Approved - Push",
            "Application approved",
            "Your application {{reference}} is approved. You can now log in."
        ),
        new Seed(
            NotificationEvent.ONBOARDING_APPROVED.name(), NotificationChannel.EMAIL,
            "Onboarding Approved - Email",
            "%BRAND_NAME% - Application approved",
            html("%COLOR_APPROVED%", "Application approved",
                "<p>Dear <strong>{{applicantName}}</strong>,</p>" +
                "<p>Good news - your {{typeName}} application has been approved. You can now log in and get started.</p>" +
                table(
                    row("Reference", "{{reference}}")))
        ),
        new Seed(
            NotificationEvent.ONBOARDING_APPROVED.name(), NotificationChannel.WHATSAPP,
            "Onboarding Approved - WhatsApp",
            null,
            "*%BRAND_NAME% | Application approved*\n\n" +
            "Dear {{applicantName}},\n\n" +
            "Good news - your application has been approved. You can now log in and get started.\n\n" +
            "Reference: *{{reference}}*"
        ),
        new Seed(
            NotificationEvent.ONBOARDING_APPROVED.name(), NotificationChannel.SMS,
            "Onboarding Approved - SMS",
            null,
            "%BRAND_NAME%: Your application {{reference}} is approved. You can now log in."
        ),

        // ── ONBOARDING_REJECTED ─────────────────────────────────────────────────────
        new Seed(
            NotificationEvent.ONBOARDING_REJECTED.name(), NotificationChannel.IN_APP,
            "Onboarding Rejected - In App",
            "Application not approved",
            "Your application {{reference}} was not approved. Reason: {{reason}}"
        ),
        new Seed(
            NotificationEvent.ONBOARDING_REJECTED.name(), NotificationChannel.FCM_PUSH,
            "Onboarding Rejected - Push",
            "Application not approved",
            "Your application {{reference}} was not approved. Reason: {{reason}}"
        ),
        new Seed(
            NotificationEvent.ONBOARDING_REJECTED.name(), NotificationChannel.EMAIL,
            "Onboarding Rejected - Email",
            "%BRAND_NAME% - Application not approved",
            html("%COLOR_DANGER%", "Application not approved",
                "<p>Dear <strong>{{applicantName}}</strong>,</p>" +
                "<p>We are sorry - your {{typeName}} application could not be approved.</p>" +
                table(
                    row("Reference", "{{reference}}"),
                    row("Reason", "{{reason}}")))
        ),
        new Seed(
            NotificationEvent.ONBOARDING_REJECTED.name(), NotificationChannel.WHATSAPP,
            "Onboarding Rejected - WhatsApp",
            null,
            "*%BRAND_NAME% | Application not approved*\n\n" +
            "Dear {{applicantName}},\n\n" +
            "We are sorry - your application could not be approved.\n\n" +
            "Reference: *{{reference}}*\n\nReason: {{reason}}"
        ),
        new Seed(
            NotificationEvent.ONBOARDING_REJECTED.name(), NotificationChannel.SMS,
            "Onboarding Rejected - SMS",
            null,
            "%BRAND_NAME%: Your application {{reference}} was not approved. Reason: {{reason}}"
        ),

        // ── FOOD_* (English only: NMS templates have no language dimension yet) ──────────
        new Seed(
            NotificationEvent.FOOD_NEW_ORDER.name(), NotificationChannel.IN_APP,
            "Food New Order - In App",
            "New order",
            "New order {{orderNumber}}. Accept it before it times out."
        ),
        new Seed(
            NotificationEvent.FOOD_NEW_ORDER.name(), NotificationChannel.FCM_PUSH,
            "Food New Order - Push",
            "New order",
            "New order {{orderNumber}}. Accept it before it times out."
        ),
        new Seed(
            NotificationEvent.FOOD_ORDER_ACCEPTED.name(), NotificationChannel.IN_APP,
            "Food Order Accepted - In App",
            "Order accepted",
            "The restaurant is preparing order {{orderNumber}}."
        ),
        new Seed(
            NotificationEvent.FOOD_ORDER_ACCEPTED.name(), NotificationChannel.FCM_PUSH,
            "Food Order Accepted - Push",
            "Order accepted",
            "The restaurant is preparing order {{orderNumber}}."
        ),
        new Seed(
            NotificationEvent.FOOD_ORDER_REJECTED.name(), NotificationChannel.IN_APP,
            "Food Order Rejected - In App",
            "Order not accepted",
            "Order {{orderNumber}} could not be accepted. Any payment is refunded in full."
        ),
        new Seed(
            NotificationEvent.FOOD_ORDER_REJECTED.name(), NotificationChannel.FCM_PUSH,
            "Food Order Rejected - Push",
            "Order not accepted",
            "Order {{orderNumber}} could not be accepted. Any payment is refunded in full."
        ),
        new Seed(
            NotificationEvent.FOOD_ORDER_ON_THE_WAY.name(), NotificationChannel.IN_APP,
            "Food Order On The Way - In App",
            "On the way",
            "Order {{orderNumber}} is on the way. Keep your delivery code ready."
        ),
        new Seed(
            NotificationEvent.FOOD_ORDER_ON_THE_WAY.name(), NotificationChannel.FCM_PUSH,
            "Food Order On The Way - Push",
            "On the way",
            "Order {{orderNumber}} is on the way. Keep your delivery code ready."
        ),
        new Seed(
            NotificationEvent.FOOD_ORDER_DELIVERED.name(), NotificationChannel.IN_APP,
            "Food Order Delivered - In App",
            "Delivered",
            "Order {{orderNumber}} was delivered. Enjoy your meal."
        ),
        new Seed(
            NotificationEvent.FOOD_ORDER_DELIVERED.name(), NotificationChannel.FCM_PUSH,
            "Food Order Delivered - Push",
            "Delivered",
            "Order {{orderNumber}} was delivered. Enjoy your meal."
        ),
        new Seed(
            NotificationEvent.FOOD_COURIER_OFFER.name(), NotificationChannel.IN_APP,
            "Food Courier Offer - In App",
            "New delivery job",
            "A delivery job is waiting. Accept it before the offer ends."
        ),
        new Seed(
            NotificationEvent.FOOD_COURIER_OFFER.name(), NotificationChannel.FCM_PUSH,
            "Food Courier Offer - Push",
            "New delivery job",
            "A delivery job is waiting. Accept it before the offer ends."
        ),
        new Seed(
            NotificationEvent.FOOD_REFUND_APPROVED.name(), NotificationChannel.IN_APP,
            "Food Refund Approved - In App",
            "Refund approved",
            "Your refund request was approved."
        ),
        new Seed(
            NotificationEvent.FOOD_REFUND_APPROVED.name(), NotificationChannel.FCM_PUSH,
            "Food Refund Approved - Push",
            "Refund approved",
            "Your refund request was approved."
        ),
        new Seed(
            NotificationEvent.FOOD_REFUND_DECLINED.name(), NotificationChannel.IN_APP,
            "Food Refund Declined - In App",
            "Refund update",
            "We reviewed your refund request. See the app for details."
        ),
        new Seed(
            NotificationEvent.FOOD_REFUND_DECLINED.name(), NotificationChannel.FCM_PUSH,
            "Food Refund Declined - Push",
            "Refund update",
            "We reviewed your refund request. See the app for details."
        ),

        // ── MATRIMONY_ONBOARDING_* (English only: NMS templates have no language dimension yet) ──────────
        new Seed(
            NotificationEvent.MATRIMONY_ONBOARDING_RECEIVED.name(), NotificationChannel.IN_APP,
            "Matrimony Onboarding Received - In App",
            "Application received",
            "We received your application {{reference}}. We will update you shortly."
        ),
        new Seed(
            NotificationEvent.MATRIMONY_ONBOARDING_RECEIVED.name(), NotificationChannel.FCM_PUSH,
            "Matrimony Onboarding Received - Push",
            "Application received",
            "We received your application {{reference}}. We will update you shortly."
        ),
        new Seed(
            NotificationEvent.MATRIMONY_ONBOARDING_RECEIVED.name(), NotificationChannel.EMAIL,
            "Matrimony Onboarding Received - Email",
            "%MATRIMONY_BRAND_NAME% - Application received",
            html("%BRAND_ACCENT%", "Application received",
                "<p>Dear <strong>{{applicantName}}</strong>,</p>" +
                "<p>Thank you for joining %MATRIMONY_BRAND_NAME%. We have received your application and will update you shortly.</p>" +
                table(
                    row("Reference", "{{reference}}")))
        ),
        new Seed(
            NotificationEvent.MATRIMONY_ONBOARDING_RECEIVED.name(), NotificationChannel.SMS,
            "Matrimony Onboarding Received - SMS",
            null,
            "%MATRIMONY_BRAND_NAME%: We received your application {{reference}}. We will update you shortly."
        ),

        new Seed(
            NotificationEvent.MATRIMONY_ONBOARDING_RETURNED.name(), NotificationChannel.IN_APP,
            "Matrimony Onboarding Returned - In App",
            "Please update your application",
            "Your application {{reference}} needs a correction: {{reason}}. Please update it and submit again."
        ),
        new Seed(
            NotificationEvent.MATRIMONY_ONBOARDING_RETURNED.name(), NotificationChannel.FCM_PUSH,
            "Matrimony Onboarding Returned - Push",
            "Please update your application",
            "Your application {{reference}} needs a correction: {{reason}}. Please update it and submit again."
        ),
        new Seed(
            NotificationEvent.MATRIMONY_ONBOARDING_RETURNED.name(), NotificationChannel.EMAIL,
            "Matrimony Onboarding Returned - Email",
            "%MATRIMONY_BRAND_NAME% - Please update your application",
            html("%COLOR_ALERT%", "Please update your application",
                "<p>Dear <strong>{{applicantName}}</strong>,</p>" +
                "<p>We need a small correction before we can continue with your application. Please update it and submit again.</p>" +
                table(
                    row("Reference", "{{reference}}"),
                    row("What to fix", "{{reason}}")))
        ),
        new Seed(
            NotificationEvent.MATRIMONY_ONBOARDING_RETURNED.name(), NotificationChannel.SMS,
            "Matrimony Onboarding Returned - SMS",
            null,
            "%MATRIMONY_BRAND_NAME%: Your application {{reference}} needs a correction: {{reason}}. Please update and resubmit."
        ),

        new Seed(
            NotificationEvent.MATRIMONY_ONBOARDING_APPROVED.name(), NotificationChannel.IN_APP,
            "Matrimony Onboarding Approved - In App",
            "Welcome to %MATRIMONY_BRAND_NAME%",
            "Your application {{reference}} is approved. You can now sign in."
        ),
        new Seed(
            NotificationEvent.MATRIMONY_ONBOARDING_APPROVED.name(), NotificationChannel.FCM_PUSH,
            "Matrimony Onboarding Approved - Push",
            "Welcome to %MATRIMONY_BRAND_NAME%",
            "Your application {{reference}} is approved. You can now sign in."
        ),
        new Seed(
            NotificationEvent.MATRIMONY_ONBOARDING_APPROVED.name(), NotificationChannel.EMAIL,
            "Matrimony Onboarding Approved - Email",
            "%MATRIMONY_BRAND_NAME% - Welcome",
            html("%BRAND_ACCENT%", "Welcome to %MATRIMONY_BRAND_NAME%",
                "<p>Dear <strong>{{applicantName}}</strong>,</p>" +
                "<p>Your application has been approved and your account is ready. You can now sign in and complete your profile.</p>" +
                "<p>{{signInDetails}}</p>" +
                table(
                    row("Reference", "{{reference}}")))
        ),
        new Seed(
            NotificationEvent.MATRIMONY_ONBOARDING_APPROVED.name(), NotificationChannel.SMS,
            "Matrimony Onboarding Approved - SMS",
            null,
            "%MATRIMONY_BRAND_NAME%: Your application {{reference}} is approved. You can now sign in."
        ),

        new Seed(
            NotificationEvent.MATRIMONY_SELF_REGISTER_EXISTING_ACCOUNT.name(), NotificationChannel.EMAIL,
            "Matrimony Sign-up - Account Already Exists - Email",
            "%MATRIMONY_BRAND_NAME% - You already have an account",
            html("%BRAND_ACCENT%", "You already have an account",
                "<p>Someone, possibly you, tried to create a %MATRIMONY_BRAND_NAME% account with <strong>{{email}}</strong>.</p>" +
                "<p>An account already exists for this address, so no new one was made. To sign in, open the sign-in page and use a " +
                "one-time code sent to this address. If you have forgotten your password, choose <em>Forgot password</em> there.</p>" +
                "<p>If this was not you, you can ignore this message; nothing has changed on your account.</p>")
        ),

        new Seed(
            NotificationEvent.MATRIMONY_ACCOUNT_ACTIVATED.name(), NotificationChannel.IN_APP,
            "Matrimony Account Activated - In App",
            "Your account is active",
            "Your %MATRIMONY_BRAND_NAME% account is active. You can now sign in."
        ),
        new Seed(
            NotificationEvent.MATRIMONY_ACCOUNT_ACTIVATED.name(), NotificationChannel.FCM_PUSH,
            "Matrimony Account Activated - Push",
            "Your account is active",
            "Your %MATRIMONY_BRAND_NAME% account is active. You can now sign in."
        ),
        new Seed(
            NotificationEvent.MATRIMONY_ACCOUNT_ACTIVATED.name(), NotificationChannel.EMAIL,
            "Matrimony Account Activated - Email",
            "%MATRIMONY_BRAND_NAME% - Your account is active",
            html("%BRAND_ACCENT%", "Your account is active",
                "<p>Dear <strong>{{customerName}}</strong>,</p>" +
                "<p>Your %MATRIMONY_BRAND_NAME% account is now active. You can sign in and complete your profile.</p>" +
                "<p>{{signInDetails}}</p>")
        ),
        new Seed(
            NotificationEvent.MATRIMONY_ACCOUNT_ACTIVATED.name(), NotificationChannel.SMS,
            "Matrimony Account Activated - SMS",
            null,
            "%MATRIMONY_BRAND_NAME%: Your account is active. Your sign-in details were sent to your email."
        ),

        new Seed(
            NotificationEvent.ORGANIZATION_ADMIN_WELCOME.name(), NotificationChannel.EMAIL,
            "Organization Administrator Welcome - Email",
            "{{organizationName}} is ready - sign in to get started",
            html("%BRAND_ACCENT%", "{{organizationName}} is ready",
                "<p>Dear <strong>{{adminName}}</strong>,</p>" +
                "<p>The organization <strong>{{organizationName}}</strong> has been created and you are its administrator. " +
                "You can now sign in to set up your departments, people, roles and applications.</p>" +
                "<p>{{signInDetails}}</p>")
        ),
        new Seed(
            NotificationEvent.ORGANIZATION_ADMIN_WELCOME.name(), NotificationChannel.SMS,
            "Organization Administrator Welcome - SMS",
            null,
            "{{organizationName}} is ready and you are its administrator. Your sign-in details were sent to your email."
        ),

        new Seed(
            NotificationEvent.FRANCHISE_WELCOME.name(), NotificationChannel.EMAIL,
            "Franchise Welcome - Email",
            "{{organizationName}}: your franchise {{franchiseCode}} is ready",
            html("%BRAND_ACCENT%", "Your franchise {{franchiseCode}} is ready",
                "<p>Dear <strong>{{personName}}</strong>,</p>" +
                "<p>You have been added as the {{roleLabel}} of franchise <strong>{{franchiseCode}}</strong> at " +
                "<strong>{{organizationName}}</strong>.</p>" +
                "<p>You can use: {{applications}}</p>" +
                button("{{portalUrl}}", "Open the sign-in page") +
                "<p>Or open this link: <a href='{{portalUrl}}'>{{portalUrl}}</a></p>" +
                "<p>{{signInDetails}}</p>")
        ),
        new Seed(
            NotificationEvent.FRANCHISE_WELCOME.name(), NotificationChannel.SMS,
            "Franchise Welcome - SMS",
            null,
            "{{organizationName}}: you were added as the {{roleLabel}} of franchise {{franchiseCode}}. Your sign-in details were sent to your email."
        ),

        new Seed(
            NotificationEvent.FRANCHISE_AGENT_REQUEST.name(), NotificationChannel.EMAIL,
            "Franchise Agent Request - Email",
            "{{organizationName}}: franchise {{franchiseCode}} asks for more agents",
            html("%COLOR_WARNING%", "More agents requested",
                "<p><strong>{{ownerName}}</strong> ({{ownerEmail}}), owner of franchise <strong>{{franchiseCode}}</strong>, has reached the " +
                "limit of {{currentLimit}} agents for <strong>{{applicationName}}</strong> and asks for {{requestedAgents}} more.</p>" +
                "<p>{{note}}</p>" +
                "<p>To give them more places, collect the franchise amount and raise the limit on the Franchises screen.</p>")
        ),

        new Seed(
            NotificationEvent.FRANCHISE_DEMAND_THRESHOLD.name(), NotificationChannel.EMAIL,
            "Franchise Demand Threshold - Email",
            "{{organizationName}}: {{votes}} people ask for {{applicationName}} in {{placeName}}",
            html("%COLOR_APPROVED%", "Demand for a new place",
                "<p><strong>{{votes}}</strong> people asked for <strong>{{applicationName}}</strong> in <strong>{{placeName}}</strong> " +
                "({{placeType}} {{placeCode}}) in the last {{windowDays}} days. No franchise covers this place yet.</p>" +
                "<p>Your alert level is {{threshold}} votes. Open the demand planning screen to see who is asking (customers, " +
                "people who want to work there, people who want to run a franchise) and plan the next franchise.</p>" +
                "<p><a href='{{portalUrl}}'>{{portalUrl}}</a></p>")
        ),

        // ── DRIVER_SUBSCRIPTION_REMINDER (Rider_FranchiseService) ──────────────────────
        new Seed(
            NotificationEvent.DRIVER_SUBSCRIPTION_REMINDER.name(), NotificationChannel.IN_APP,
            "Driver Subscription Reminder - In App",
            "Your {{planName}} subscription ends tomorrow",
            "Your subscription is paid until {{periodEnd}}. The next period ({{currency}} {{price}}) is taken from your earnings; keep enough earnings so your dues stay within the limit and you can keep going online."
        ),
        new Seed(
            NotificationEvent.DRIVER_SUBSCRIPTION_REMINDER.name(), NotificationChannel.FCM_PUSH,
            "Driver Subscription Reminder - Push",
            "Your {{planName}} subscription ends tomorrow",
            "Paid until {{periodEnd}}. The next period ({{currency}} {{price}}) is taken from your earnings."
        ),

        // ── DRIVER_DOCUMENT_EXPIRING / DRIVER_DOCUMENT_LAPSED (Rider_DMS, D12) ──────────
        new Seed(
            NotificationEvent.DRIVER_DOCUMENT_EXPIRING.name(), NotificationChannel.IN_APP,
            "Driver Document Expiring - In App",
            "Your {{documentName}} expires in {{daysLeft}} day(s)",
            "Your {{documentName}} is valid until {{validUntil}}. Upload the renewed document before then; once it lapses you cannot go online until the renewal is approved."
        ),
        new Seed(
            NotificationEvent.DRIVER_DOCUMENT_EXPIRING.name(), NotificationChannel.FCM_PUSH,
            "Driver Document Expiring - Push",
            "Your {{documentName}} expires in {{daysLeft}} day(s)",
            "Valid until {{validUntil}}. Upload the renewed document in the app."
        ),
        new Seed(
            NotificationEvent.DRIVER_DOCUMENT_EXPIRING.name(), NotificationChannel.SMS,
            "Driver Document Expiring - SMS",
            null,
            "%BRAND_NAME%: Your {{documentName}} expires on {{validUntil}}. Upload the renewed document in the app to keep going online."
        ),
        new Seed(
            NotificationEvent.DRIVER_DOCUMENT_LAPSED.name(), NotificationChannel.IN_APP,
            "Driver Document Lapsed - In App",
            "Your {{documentName}} has expired",
            "Your {{documentName}} was valid until {{validUntil}}. You cannot go online until you upload the renewed document and it is approved."
        ),
        new Seed(
            NotificationEvent.DRIVER_DOCUMENT_LAPSED.name(), NotificationChannel.FCM_PUSH,
            "Driver Document Lapsed - Push",
            "Your {{documentName}} has expired",
            "Upload the renewed document in the app to go online again."
        ),
        new Seed(
            NotificationEvent.DRIVER_DOCUMENT_LAPSED.name(), NotificationChannel.SMS,
            "Driver Document Lapsed - SMS",
            null,
            "%BRAND_NAME%: Your {{documentName}} expired on {{validUntil}}. You cannot go online until the renewed document is uploaded and approved."
        ),

        new Seed(
            NotificationEvent.WALLET_ALERT.name(), NotificationChannel.EMAIL,
            "Wallet Alert - Email",
            "{{organizationName}} wallet alert: {{alertType}}",
            html("%COLOR_WARNING%", "Wallet alert",
                "<p>An item in the <strong>{{organizationName}}</strong> wallet needs attention.</p>" +
                "<p><strong>{{alertType}}</strong> {{appCode}}</p>" +
                "<p>{{alertMessage}}</p>" +
                "<p>Open the wallet's Alerts screen to review it and mark it as seen.</p>")
        ),

        new Seed(
            NotificationEvent.MATRIMONY_ONBOARDING_REJECTED.name(), NotificationChannel.IN_APP,
            "Matrimony Onboarding Rejected - In App",
            "Application not approved",
            "Your application {{reference}} was not approved. Reason: {{reason}}"
        ),
        new Seed(
            NotificationEvent.MATRIMONY_ONBOARDING_REJECTED.name(), NotificationChannel.FCM_PUSH,
            "Matrimony Onboarding Rejected - Push",
            "Application not approved",
            "Your application {{reference}} was not approved. Reason: {{reason}}"
        ),
        new Seed(
            NotificationEvent.MATRIMONY_ONBOARDING_REJECTED.name(), NotificationChannel.EMAIL,
            "Matrimony Onboarding Rejected - Email",
            "%MATRIMONY_BRAND_NAME% - Application not approved",
            html("%COLOR_DANGER%", "Application not approved",
                "<p>Dear <strong>{{applicantName}}</strong>,</p>" +
                "<p>We are sorry - your application could not be approved.</p>" +
                table(
                    row("Reference", "{{reference}}"),
                    row("Reason", "{{reason}}")))
        ),
        new Seed(
            NotificationEvent.MATRIMONY_ONBOARDING_REJECTED.name(), NotificationChannel.SMS,
            "Matrimony Onboarding Rejected - SMS",
            null,
            "%MATRIMONY_BRAND_NAME%: Your application {{reference}} was not approved. Reason: {{reason}}"
        ),

        // ── MATRIMONY_NOTIFICATION ───────────────────────────────────────────
        new Seed(
            NotificationEvent.MATRIMONY_NOTIFICATION.name(), NotificationChannel.IN_APP,
            "Matrimony Notification - In App",
            "{{title}}",
            "{{message}}"
        ),
        new Seed(
            NotificationEvent.MATRIMONY_NOTIFICATION.name(), NotificationChannel.FCM_PUSH,
            "Matrimony Notification - Push",
            "{{title}}",
            "{{message}}"
        ),
        new Seed(
            NotificationEvent.MATRIMONY_NOTIFICATION.name(), NotificationChannel.EMAIL,
            "Matrimony Notification - Email",
            "%MATRIMONY_BRAND_NAME% - {{title}}",
            html("%BRAND_ACCENT%", "{{title}}",
                "<p>{{message}}</p>")
        ),
        new Seed(
            NotificationEvent.MATRIMONY_NOTIFICATION.name(), NotificationChannel.SMS,
            "Matrimony Notification - SMS",
            null,
            "%MATRIMONY_BRAND_NAME%: {{message}}"
        ),

        // ── MATRIMONY_PHONE_OTP ──────────────────────────────────────────────
        new Seed(
            NotificationEvent.MATRIMONY_PHONE_OTP.name(), NotificationChannel.SMS,
            "Matrimony Phone Verification - SMS",
            null,
            "%MATRIMONY_BRAND_NAME%: {{code}} is your verification code. It is valid for {{minutes}} minutes. Do not share it with anyone."
        ),

        // ── TEST_NOTIFICATION ────────────────────────────────────────────────
        new Seed(
            NotificationEvent.TEST_NOTIFICATION.name(), NotificationChannel.IN_APP,
            "Test Notification - In App",
            "Test Notification",
            "{{message}}"
        ),
        new Seed(
            NotificationEvent.TEST_NOTIFICATION.name(), NotificationChannel.FCM_PUSH,
            "Test Notification - Push",
            "Test Notification",
            "{{message}}"
        )
    );

    /**
     * Every seed: the base templates and the ride lifecycle
     * messages (booking, driver, trip, payment, refund, promotions, operator messages).
     */
    public static final List<Seed> DEFAULTS = java.util.stream.Stream
            .of(BASE_DEFAULTS.stream(), RideLifecycleTemplates.seeds().stream(), RideFlowTemplates.seeds().stream(), SupportSlaTemplates.seeds().stream())
            .flatMap(java.util.function.Function.identity()).toList();

    // ── shared HTML design system (mirrors the PayEase gateway-backend email templates) ────

    static String html(String headerColor, String title, String body) {
        return "<!DOCTYPE html>" +
               "<html lang='en'>" +
               "<head>" +
               "  <meta charset='UTF-8'>" +
               "  <meta name='viewport' content='width=device-width,initial-scale=1'>" +
               "  <title>" + title + "</title>" +
               "</head>" +
               "<body style='margin:0;padding:0;background:#f4f6f9;" +
               "     font-family:Arial,Helvetica,sans-serif;color:#333;'>" +
               "<table width='100%' cellpadding='0' cellspacing='0' style='background:#f4f6f9;padding:32px 0;'>" +
               "<tr><td align='center'>" +
               "<table width='600' cellpadding='0' cellspacing='0' style='background:#fff;" +
               "       border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.08);'>" +
               "<tr><td style='background:" + headerColor + ";padding:24px 32px;'>" +
               "  <table width='100%' cellpadding='0' cellspacing='0'><tr>" +
               "    <td><span style='color:#fff;font-size:22px;font-weight:bold;letter-spacing:1px;'>%BRAND_NAME%</span></td>" +
               "    <td align='right'><span style='color:rgba(255,255,255,.7);font-size:12px;'>%BRAND_TAGLINE%</span></td>" +
               "  </tr></table>" +
               "</td></tr>" +
               "<tr><td style='background:" + headerColor + ";padding:0 32px 24px;'>" +
               "  <h2 style='color:#fff;margin:0;font-size:18px;font-weight:normal;'>" + title + "</h2>" +
               "</td></tr>" +
               "<tr><td style='padding:32px;'>" + body + "</td></tr>" +
               "<tr><td style='background:#f0f2f5;padding:20px 32px;border-top:1px solid #e0e0e0;" +
               "               text-align:center;color:#888;font-size:12px;'>" +
               "  &copy; %BRAND_NAME%. All rights reserved.<br>" +
               "  <a href='mailto:%SUPPORT_EMAIL%' style='color:%BRAND_PRIMARY%;text-decoration:none;'>%SUPPORT_EMAIL%</a>" +
               "</td></tr>" +
               "</table>" +
               "</td></tr></table>" +
               "</body></html>";
    }

    private static String badge(String color, String label) {
        return "<p style='margin:16px 0;'>" +
               "<span style='background:" + color + ";color:#fff;font-size:12px;font-weight:bold;" +
               "             padding:4px 12px;border-radius:12px;letter-spacing:.5px;'>" +
               label + "</span></p>";
    }

    private static String otpBox(String otp) {
        return "<div style='text-align:center;margin:32px 0;'>" +
               "  <span style='font-size:36px;font-weight:bold;letter-spacing:12px;" +
               "        background:%BRAND_PRIMARY_TINT%;padding:16px 32px;border-radius:8px;" +
               "        color:%BRAND_PRIMARY%;display:inline-block;'>" + otp + "</span>" +
               "</div>";
    }

    private static String button(String href, String label) {
        return "<div style='text-align:center;margin:28px 0;'>" +
               "<a href='" + href + "' style='background:%BRAND_PRIMARY%;color:#fff;text-decoration:none;" +
               "   padding:12px 28px;border-radius:6px;font-weight:bold;display:inline-block;'>" +
               label + "</a></div>";
    }

    private static String table(String... rows) {
        StringBuilder sb = new StringBuilder(
                "<table style='width:100%;border-collapse:collapse;margin:20px 0;font-size:14px;'>");
        for (int i = 0; i < rows.length; i++) {
            sb.append(rows[i].replace("__BG__", i % 2 == 0 ? "#f8f9fa" : "#fff"));
        }
        sb.append("</table>");
        return sb.toString();
    }

    private static String row(String label, String value) {
        return "<tr style='background:__BG__;'>" +
               "<td style='padding:10px 16px;font-weight:bold;width:40%;" +
               "           border-bottom:1px solid #eee;color:#555;'>" + label + "</td>" +
               "<td style='padding:10px 16px;border-bottom:1px solid #eee;'>" + value + "</td>" +
               "</tr>";
    }
}
