package com.heuristiq.identity.device;

import com.heuristiq.identity.enums.UamEnums;
import eu.bitwalker.useragentutils.Browser;
import eu.bitwalker.useragentutils.OperatingSystem;
import eu.bitwalker.useragentutils.UserAgent;
import eu.bitwalker.useragentutils.Version;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Parses a raw {@code User-Agent} header into the fields {@link com.heuristiq.identity.auth.LoginSessionService}
 *  needs to fingerprint/describe a device -- kept separate from login/session orchestration so that
 *  logic stays testable and reusable on its own. */
@Component
public class UserAgentParser {

    public record ParsedUserAgent(String browser, String browserVersion, String os, String deviceName,
                                   String manufacturer, UamEnums.DeviceType deviceType, UamEnums.Platform platform) {
    }

    public ParsedUserAgent parse(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return new ParsedUserAgent(null, null, null, null, null, UamEnums.DeviceType.UNKNOWN, UamEnums.Platform.WEB);
        }
        UserAgent parsed = UserAgent.parseUserAgentString(userAgent);
        Browser browser = parsed.getBrowser();
        OperatingSystem os = parsed.getOperatingSystem();
        Version browserVersion = parsed.getBrowserVersion();
        String browserName = browser != null && browser != Browser.UNKNOWN ? browser.getName() : null;
        String browserVer = browserVersion != null ? browserVersion.getVersion() : null;
        String osName = os != null && os != OperatingSystem.UNKNOWN ? os.getName() : null;
        String manufacturer = os != null && os.getManufacturer() != null ? os.getManufacturer().getName() : null;
        return new ParsedUserAgent(browserName, browserVer, osName, osName, manufacturer,
                mapDeviceType(os), mapPlatform(osName));
    }

    private UamEnums.DeviceType mapDeviceType(OperatingSystem os) {
        if (os == null) {
            return UamEnums.DeviceType.UNKNOWN;
        }
        eu.bitwalker.useragentutils.DeviceType deviceType = os.getDeviceType();
        if (deviceType == null) {
            return UamEnums.DeviceType.UNKNOWN;
        }
        return switch (deviceType) {
            case MOBILE -> UamEnums.DeviceType.MOBILE;
            case TABLET -> UamEnums.DeviceType.TABLET;
            case COMPUTER -> UamEnums.DeviceType.DESKTOP;
            case GAME_CONSOLE, DMR -> UamEnums.DeviceType.SMART_TV;
            default -> UamEnums.DeviceType.UNKNOWN;
        };
    }

    private UamEnums.Platform mapPlatform(String osName) {
        if (!StringUtils.hasText(osName)) {
            return UamEnums.Platform.WEB;
        }
        String lower = osName.toLowerCase();
        if (lower.contains("android")) return UamEnums.Platform.ANDROID;
        if (lower.contains("ios") || lower.contains("iphone") || lower.contains("ipad")) return UamEnums.Platform.IOS;
        if (lower.contains("windows")) return UamEnums.Platform.WINDOWS;
        if (lower.contains("mac")) return UamEnums.Platform.MACOS;
        if (lower.contains("linux")) return UamEnums.Platform.LINUX;
        return UamEnums.Platform.WEB;
    }
}
