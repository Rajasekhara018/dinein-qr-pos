import 'package:flutter/foundation.dart';

/// One country or territory of the phone field: ISO 3166 alpha-2 code, calling code, English name and the national number
/// length (digits after the calling code, without the trunk prefix).
///
/// Countries that share a calling code (NANP +1, +7, +44, +39, +358, +47, +61, +212, +262, +590, +599, +672) are separate
/// entries with the same [dialCode]; a NANP territory also lists its [areaCodes] (leading digits of the national number).
@immutable
class HqPhoneCountry {
  const HqPhoneCountry({
    required this.iso,
    required this.dialCode,
    required this.nationalMin,
    required this.nationalMax,
    this.name = '',
    this.areaCodes = const <String>[],
    this.pattern,
    this.keepsLeadingZero = false,
  });

  final String iso;

  /// Calling code digits without `+` (`91`, `1`, `971`).
  final String dialCode;
  final int nationalMin;
  final int nationalMax;

  /// English name; the picker shows a localized one when the app passes `countryNames`.
  final String name;

  /// NANP area codes of a territory sharing `+1` (empty for the United States, Canada and countries with their own code).
  final List<String> areaCodes;

  /// A regular expression the national number must match in full (India, NANP); null means the length range decides.
  final String? pattern;

  /// True when a leading 0 is part of the national number (Italy, Vatican) and must not be dropped as a trunk prefix.
  final bool keepsLeadingZero;

  /// What the picker shows: `+91`, or `+1 268` for a territory with one area code.
  String get dialDisplay => areaCodes.length == 1 ? '+$dialCode ${areaCodes.first}' : '+$dialCode';

  /// The emoji flag of this country.
  String get flag => HqCountryData.flagEmoji(iso);
}

/// The country list and helpers behind the phone field, kept apart so other widgets (an address field, a profile screen) can
/// reuse it.
///
/// Source and limits: ISO 3166-1 alpha-2 codes (plus Kosovo, XK, which is user-assigned) and ITU calling codes. The national
/// lengths are the commonly published ranges, not a full numbering-plan check like the web's `libphonenumber-js`; India
/// (`[6-9]` then nine digits) and the NANP (`[2-9]` then nine digits) are strict. Tighten a country by editing [_raw].
abstract final class HqCountryData {
  /// Every country, sorted by English name.
  static final List<HqPhoneCountry> all = _build();

  static final Map<String, HqPhoneCountry> _byIso = {for (final c in all) c.iso: c};

  /// The country for [iso] (case-insensitive), or null.
  static HqPhoneCountry? byIso(String iso) => _byIso[iso.toUpperCase()];

  /// The emoji flag for an ISO code: two regional-indicator symbols. Returns an empty string for anything but two letters.
  static String flagEmoji(String iso) {
    final code = iso.toUpperCase();
    if (code.length != 2 || !RegExp(r'^[A-Z]{2}$').hasMatch(code)) return '';
    return String.fromCharCodes(code.codeUnits.map((u) => 0x1F1E6 + (u - 0x41)));
  }

  /// The countries matching [query] in their order of relevance.
  ///
  /// - Digits (`91`, `+91`, `1 268`): countries whose calling code (or code plus area code) starts with them.
  /// - Letters (`ind`, `IN`): an exact ISO code first, then names starting with the text, then names containing it, then ISO
  ///   codes starting with it.
  ///
  /// [names] maps ISO code to a localized name, which is searched in addition to the English name.
  static List<HqPhoneCountry> search(List<HqPhoneCountry> countries, String query, {Map<String, String>? names}) {
    final raw = query.trim();
    if (raw.isEmpty) return countries;
    final stripped = raw.startsWith('+') ? raw.substring(1) : raw;
    final digits = stripped.replaceAll(RegExp(r'[\s-]'), '');
    if (digits.isNotEmpty && RegExp(r'^\d+$').hasMatch(digits)) {
      return [
        for (final c in countries)
          if (c.dialCode.startsWith(digits) || '${c.dialCode}${c.areaCodes.join()}'.startsWith(digits) || c.areaCodes.any((a) => '${c.dialCode}$a'.startsWith(digits))) c,
      ];
    }
    final needle = stripped.toLowerCase();
    final exact = <HqPhoneCountry>[];
    final starts = <HqPhoneCountry>[];
    final contains = <HqPhoneCountry>[];
    final isoStarts = <HqPhoneCountry>[];
    for (final c in countries) {
      final labels = [c.name.toLowerCase(), if (names?[c.iso] != null) names![c.iso]!.toLowerCase()];
      if (c.iso.toLowerCase() == needle) {
        exact.add(c);
      } else if (labels.any((l) => l.startsWith(needle))) {
        starts.add(c);
      } else if (labels.any((l) => l.contains(needle))) {
        contains.add(c);
      } else if (c.iso.toLowerCase().startsWith(needle)) {
        isoStarts.add(c);
      }
    }
    return [...exact, ...starts, ...contains, ...isoStarts];
  }

  /// [countries] with the ISO codes of [preferred] first (in that order) and the rest after, in their own order.
  static List<HqPhoneCountry> withPreferredFirst(List<HqPhoneCountry> countries, List<String> preferred) {
    final pinned = <HqPhoneCountry>[
      for (final iso in preferred)
        for (final c in countries)
          if (c.iso == iso.toUpperCase()) c,
    ];
    return [...pinned, for (final c in countries) if (!pinned.contains(c)) c];
  }

  static List<HqPhoneCountry> _build() {
    final list = <HqPhoneCountry>[];
    for (final line in _raw.split('\n')) {
      final text = line.trim();
      if (text.isEmpty) continue;
      final f = text.split('|');
      final dial = f[1];
      final areas = f.length > 5 && f[5].isNotEmpty ? f[5].split(',') : const <String>[];
      String? pattern = f.length > 6 && f[6].isNotEmpty ? f[6] : null;
      if (dial == '1' && pattern == null) pattern = r'^[2-9]\d{9}$';
      list.add(HqPhoneCountry(
        iso: f[0],
        dialCode: dial,
        name: f[2],
        nationalMin: int.parse(f[3]),
        nationalMax: int.parse(f[4]),
        areaCodes: areas,
        pattern: pattern,
        keepsLeadingZero: f[0] == 'IT' || f[0] == 'VA',
      ));
    }
    list.sort((a, b) => _sortKey(a.name).compareTo(_sortKey(b.name)));
    return List<HqPhoneCountry>.unmodifiable(list);
  }

  /// Name without accents so "Åland" sorts under A and "Türkiye" under T.
  static String _sortKey(String name) {
    const from = 'ÅôçéãíÜü';
    const to = 'AoceaiUu';
    final buffer = StringBuffer();
    for (final rune in name.runes) {
      final ch = String.fromCharCode(rune);
      final i = from.indexOf(ch);
      buffer.write(i < 0 ? ch : to[i]);
    }
    return buffer.toString();
  }

  // ISO|calling code|English name|national min|national max|NANP area codes|pattern
  static const String _raw = r'''
AF|93|Afghanistan|9|9
AX|358|Åland Islands|5|12
AL|355|Albania|8|9
DZ|213|Algeria|8|9
AS|1|American Samoa|10|10|684
AD|376|Andorra|6|9
AO|244|Angola|9|9
AI|1|Anguilla|10|10|264
AQ|672|Antarctica|6|6
AG|1|Antigua and Barbuda|10|10|268
AR|54|Argentina|10|11
AM|374|Armenia|8|8
AW|297|Aruba|7|7
AU|61|Australia|9|9
AT|43|Austria|4|13
AZ|994|Azerbaijan|9|9
BS|1|Bahamas|10|10|242
BH|973|Bahrain|8|8
BD|880|Bangladesh|10|10
BB|1|Barbados|10|10|246
BY|375|Belarus|9|10
BE|32|Belgium|8|9
BZ|501|Belize|7|7
BJ|229|Benin|8|10
BM|1|Bermuda|10|10|441
BT|975|Bhutan|7|8
BO|591|Bolivia|8|8
BQ|599|Bonaire, Sint Eustatius and Saba|7|7
BA|387|Bosnia and Herzegovina|8|9
BW|267|Botswana|7|8
BV|47|Bouvet Island|8|8
BR|55|Brazil|10|11
IO|246|British Indian Ocean Territory|7|7
VG|1|British Virgin Islands|10|10|284
BN|673|Brunei|7|7
BG|359|Bulgaria|8|9
BF|226|Burkina Faso|8|8
BI|257|Burundi|8|8
CV|238|Cabo Verde|7|7
KH|855|Cambodia|8|9
CM|237|Cameroon|9|9
CA|1|Canada|10|10
KY|1|Cayman Islands|10|10|345
CF|236|Central African Republic|8|8
TD|235|Chad|8|8
CL|56|Chile|9|9
CN|86|China|10|11
CX|61|Christmas Island|9|9
CC|61|Cocos (Keeling) Islands|9|9
CO|57|Colombia|10|10
KM|269|Comoros|7|7
CG|242|Congo|9|9
CD|243|Congo (Democratic Republic)|9|9
CK|682|Cook Islands|5|5
CR|506|Costa Rica|8|8
CI|225|Côte d'Ivoire|8|10
HR|385|Croatia|8|9
CU|53|Cuba|8|8
CW|599|Curaçao|7|8
CY|357|Cyprus|8|8
CZ|420|Czechia|9|9
DK|45|Denmark|8|8
DJ|253|Djibouti|8|8
DM|1|Dominica|10|10|767
DO|1|Dominican Republic|10|10|809,829,849
EC|593|Ecuador|8|9
EG|20|Egypt|9|10
SV|503|El Salvador|8|8
GQ|240|Equatorial Guinea|9|9
ER|291|Eritrea|7|7
EE|372|Estonia|7|8
SZ|268|Eswatini|8|8
ET|251|Ethiopia|9|9
FK|500|Falkland Islands|5|5
FO|298|Faroe Islands|6|6
FJ|679|Fiji|7|7
FI|358|Finland|5|12
FR|33|France|9|9
GF|594|French Guiana|9|9
PF|689|French Polynesia|8|8
TF|262|French Southern Territories|9|9
GA|241|Gabon|7|8
GM|220|Gambia|7|7
GE|995|Georgia|9|9
DE|49|Germany|5|13
GH|233|Ghana|9|9
GI|350|Gibraltar|8|8
GR|30|Greece|10|10
GL|299|Greenland|6|6
GD|1|Grenada|10|10|473
GP|590|Guadeloupe|9|9
GU|1|Guam|10|10|671
GT|502|Guatemala|8|8
GG|44|Guernsey|10|10
GN|224|Guinea|8|9
GW|245|Guinea-Bissau|7|9
GY|592|Guyana|7|7
HT|509|Haiti|8|8
HM|672|Heard Island and McDonald Islands|6|6
VA|39|Holy See|6|11
HN|504|Honduras|8|8
HK|852|Hong Kong|8|8
HU|36|Hungary|8|9
IS|354|Iceland|7|9
IN|91|India|10|10||^[6-9]\d{9}$
ID|62|Indonesia|8|12
IR|98|Iran|10|10
IQ|964|Iraq|10|10
IE|353|Ireland|7|9
IM|44|Isle of Man|10|10
IL|972|Israel|8|9
IT|39|Italy|6|11
JM|1|Jamaica|10|10|876
JP|81|Japan|9|10
JE|44|Jersey|10|10
JO|962|Jordan|8|9
KZ|7|Kazakhstan|10|10
KE|254|Kenya|9|9
KI|686|Kiribati|5|8
XK|383|Kosovo|8|9
KW|965|Kuwait|8|8
KG|996|Kyrgyzstan|9|9
LA|856|Laos|8|10
LV|371|Latvia|8|8
LB|961|Lebanon|7|8
LS|266|Lesotho|8|8
LR|231|Liberia|7|9
LY|218|Libya|9|10
LI|423|Liechtenstein|7|9
LT|370|Lithuania|8|8
LU|352|Luxembourg|4|11
MO|853|Macao|8|8
MG|261|Madagascar|9|10
MW|265|Malawi|7|9
MY|60|Malaysia|9|10
MV|960|Maldives|7|7
ML|223|Mali|8|8
MT|356|Malta|8|8
MH|692|Marshall Islands|7|7
MQ|596|Martinique|9|9
MR|222|Mauritania|8|8
MU|230|Mauritius|7|8
YT|262|Mayotte|9|9
MX|52|Mexico|10|10
FM|691|Micronesia|7|7
MD|373|Moldova|8|8
MC|377|Monaco|8|9
MN|976|Mongolia|8|8
ME|382|Montenegro|8|9
MS|1|Montserrat|10|10|664
MA|212|Morocco|9|9
MZ|258|Mozambique|8|9
MM|95|Myanmar|7|10
NA|264|Namibia|7|10
NR|674|Nauru|7|7
NP|977|Nepal|8|10
NL|31|Netherlands|9|9
NC|687|New Caledonia|6|6
NZ|64|New Zealand|8|10
NI|505|Nicaragua|8|8
NE|227|Niger|8|8
NG|234|Nigeria|8|10
NU|683|Niue|4|4
NF|672|Norfolk Island|6|6
KP|850|North Korea|8|10
MK|389|North Macedonia|8|8
MP|1|Northern Mariana Islands|10|10|670
NO|47|Norway|8|8
OM|968|Oman|8|8
PK|92|Pakistan|10|10
PW|680|Palau|7|7
PS|970|Palestine|8|9
PA|507|Panama|7|8
PG|675|Papua New Guinea|7|8
PY|595|Paraguay|9|9
PE|51|Peru|8|9
PH|63|Philippines|10|10
PN|64|Pitcairn Islands|9|9
PL|48|Poland|9|9
PT|351|Portugal|9|9
PR|1|Puerto Rico|10|10|787,939
QA|974|Qatar|8|8
RE|262|Réunion|9|9
RO|40|Romania|9|9
RU|7|Russia|10|10
RW|250|Rwanda|9|9
BL|590|Saint Barthélemy|9|9
SH|290|Saint Helena, Ascension and Tristan da Cunha|4|4
KN|1|Saint Kitts and Nevis|10|10|869
LC|1|Saint Lucia|10|10|758
MF|590|Saint Martin|9|9
PM|508|Saint Pierre and Miquelon|6|6
VC|1|Saint Vincent and the Grenadines|10|10|784
WS|685|Samoa|5|7
SM|378|San Marino|6|10
ST|239|São Tomé and Príncipe|7|7
SA|966|Saudi Arabia|9|9
SN|221|Senegal|9|9
RS|381|Serbia|8|9
SC|248|Seychelles|7|7
SL|232|Sierra Leone|8|8
SG|65|Singapore|8|8
SX|1|Sint Maarten|10|10|721
SK|421|Slovakia|9|9
SI|386|Slovenia|8|8
SB|677|Solomon Islands|5|7
SO|252|Somalia|7|9
ZA|27|South Africa|9|9
GS|500|South Georgia and the South Sandwich Islands|5|5
KR|82|South Korea|9|10
SS|211|South Sudan|9|9
ES|34|Spain|9|9
LK|94|Sri Lanka|9|9
SD|249|Sudan|9|9
SR|597|Suriname|6|7
SJ|47|Svalbard and Jan Mayen|8|8
SE|46|Sweden|7|13
CH|41|Switzerland|9|9
SY|963|Syria|8|9
TW|886|Taiwan|8|9
TJ|992|Tajikistan|9|9
TZ|255|Tanzania|9|9
TH|66|Thailand|8|9
TL|670|Timor-Leste|7|8
TG|228|Togo|8|8
TK|690|Tokelau|4|4
TO|676|Tonga|5|7
TT|1|Trinidad and Tobago|10|10|868
TN|216|Tunisia|8|8
TR|90|Türkiye|10|10
TM|993|Turkmenistan|8|8
TC|1|Turks and Caicos Islands|10|10|649
TV|688|Tuvalu|5|7
UG|256|Uganda|9|9
UA|380|Ukraine|9|9
AE|971|United Arab Emirates|8|9
GB|44|United Kingdom|10|10
US|1|United States|10|10
UM|1|United States Minor Outlying Islands|10|10
VI|1|United States Virgin Islands|10|10|340
UY|598|Uruguay|8|9
UZ|998|Uzbekistan|9|9
VU|678|Vanuatu|5|7
VE|58|Venezuela|10|10
VN|84|Vietnam|9|10
WF|681|Wallis and Futuna|6|6
EH|212|Western Sahara|9|9
YE|967|Yemen|7|9
ZM|260|Zambia|9|9
ZW|263|Zimbabwe|9|9
''';
}
