# Changelog

## 0.1.0

First version: the Flutter counterpart of `hq-shared-lib`.

- Theme: `HqTokens` (light and dark, taken from the web `styles.scss`), `HqTheme.light/dark`, `context.hq`, spacing,
  radius and size constants, font scale steps.
- Components: `HqPageHeader`, `HqDataList` + `HqListFooter`, `HqField`, `HqFormField`, `HqSearchableSelect`,
  `HqCheckbox`, `HqRadioGroup`, `HqAmountInput`, `HqPhoneNumberInput`, `HqForm`, `HqFormGrid`, `HqFormActions`,
  `HqButton`, `HqStatusBadge`, `HqStatCards`, `HqFilterBar`, `HqTabs`, `HqModal`, `HqDrawer`, `HqFileUpload`.
- Services: `HqToastService`, `HqDialog` (confirm, alert, prompt), `HqApiClient` (CommonResponseObject envelope).
- Privileges: `HqPrivilegeAccess`, `HqPrivilegeScope`, `HqHasPrivilege`, `HqPermissionGuard`, `HqPermissionGate`.
- Audit: `HqScreenAudit` (batched `POST /v1/audit/screen-views`).
- Upload: `HqFileUpload`, `HqFilePicker` / `HqDeviceFilePicker`, `HqFileUploader` / `HqDmsUploader`,
  `HqImageProcessor` (downscale, EXIF strip).
- Money helpers: paise-precision parsing (HALF_EVEN), Indian digit grouping, `hqFormatMoney`.
