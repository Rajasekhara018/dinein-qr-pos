/// Pads [value] to two digits.
String hqTwoDigits(int value) => value.toString().padLeft(2, '0');

/// `yyyy-MM-dd` of [date] in its own zone (call `.toLocal()` first for the device zone).
String hqFormatDate(DateTime date) => '${date.year.toString().padLeft(4, '0')}-${hqTwoDigits(date.month)}-${hqTwoDigits(date.day)}';

/// `yyyy-MM-dd HH:mm` of [date] in its own zone.
String hqFormatDateTime(DateTime date) => '${hqFormatDate(date)} ${hqTwoDigits(date.hour)}:${hqTwoDigits(date.minute)}';

/// Case-insensitive "contains" used by the searchable select and the data list filter.
bool hqContainsIgnoreCase(String haystack, String needle) => haystack.toLowerCase().contains(needle.toLowerCase());
