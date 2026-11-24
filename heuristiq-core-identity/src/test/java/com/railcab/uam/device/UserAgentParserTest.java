package com.heuristiq.uam.device;

import com.heuristiq.uam.device.UserAgentParser.ParsedUserAgent;
import com.heuristiq.uam.enums.UamEnums.DeviceType;
import com.heuristiq.uam.enums.UamEnums.Platform;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgentParserTest {
    private final UserAgentParser parser = new UserAgentParser();

    @Test
    void blankHeaderYieldsUnknownDeviceOnWebPlatform() {
        for (String blank : new String[] {null, "", "   "}) {
            ParsedUserAgent parsed = parser.parse(blank);
            assertThat(parsed.browser()).isNull();
            assertThat(parsed.os()).isNull();
            assertThat(parsed.deviceType()).isEqualTo(DeviceType.UNKNOWN);
            assertThat(parsed.platform()).isEqualTo(Platform.WEB);
        }
    }

    @Test
    void desktopChromeOnWindows() {
        ParsedUserAgent parsed = parser.parse("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/120.0.0.0 Safari/537.36");

        assertThat(parsed.browser()).containsIgnoringCase("chrome");
        assertThat(parsed.browserVersion()).startsWith("120");
        assertThat(parsed.os()).containsIgnoringCase("windows");
        assertThat(parsed.deviceName()).isEqualTo(parsed.os());
        assertThat(parsed.manufacturer()).isNotBlank();
        assertThat(parsed.deviceType()).isEqualTo(DeviceType.DESKTOP);
        assertThat(parsed.platform()).isEqualTo(Platform.WINDOWS);
    }

    @Test
    void androidPhone() {
        ParsedUserAgent parsed = parser.parse("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/119.0.0.0 Mobile Safari/537.36");

        assertThat(parsed.os()).containsIgnoringCase("android");
        assertThat(parsed.deviceType()).isEqualTo(DeviceType.MOBILE);
        assertThat(parsed.platform()).isEqualTo(Platform.ANDROID);
    }

    @Test
    void iphoneSafari() {
        ParsedUserAgent parsed = parser.parse("Mozilla/5.0 (iPhone; CPU iPhone OS 16_5 like Mac OS X) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) Version/16.5 Mobile/15E148 Safari/604.1");

        assertThat(parsed.deviceType()).isEqualTo(DeviceType.MOBILE);
        assertThat(parsed.platform()).isEqualTo(Platform.IOS);
    }

    @Test
    void ipadIsATablet() {
        ParsedUserAgent parsed = parser.parse("Mozilla/5.0 (iPad; CPU OS 16_5 like Mac OS X) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) Version/16.5 Mobile/15E148 Safari/604.1");

        assertThat(parsed.deviceType()).isEqualTo(DeviceType.TABLET);
        assertThat(parsed.platform()).isEqualTo(Platform.IOS);
    }

    @Test
    void macDesktop() {
        ParsedUserAgent parsed = parser.parse("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) Version/17.0 Safari/605.1.15");

        assertThat(parsed.deviceType()).isEqualTo(DeviceType.DESKTOP);
        assertThat(parsed.platform()).isEqualTo(Platform.MACOS);
    }

    @Test
    void linuxDesktopFirefox() {
        ParsedUserAgent parsed = parser.parse("Mozilla/5.0 (X11; Linux x86_64; rv:121.0) Gecko/20100101 Firefox/121.0");

        assertThat(parsed.browser()).containsIgnoringCase("firefox");
        assertThat(parsed.platform()).isEqualTo(Platform.LINUX);
    }

    @Test
    void unrecognisedAgentHasNoBrowserOrOsAndFallsBackToWeb() {
        ParsedUserAgent parsed = parser.parse("totally-custom-client/1.0");

        assertThat(parsed.browser()).isNull();
        assertThat(parsed.os()).isNull();
        assertThat(parsed.platform()).isEqualTo(Platform.WEB);
    }

    @Test
    void gameConsoleIsMappedToSmartTv() {
        ParsedUserAgent parsed = parser.parse("Mozilla/5.0 (PlayStation 4 9.00) AppleWebKit/605.1.15 (KHTML, like Gecko)");
        assertThat(parsed.deviceType()).isIn(DeviceType.SMART_TV, DeviceType.UNKNOWN);
    }
}
