package com.railcab.dms.scan;

/** Fixed values of the malware scan feature: provider names and the basic provider's sample size. */
public final class ScanConstants {

    private ScanConstants() {
    }

    /** Name of the built-in blocklist + EICAR provider (also the default {@code railcab.dms.scan.provider}). */
    public static final String BASIC_PROVIDER = "basic";
    /** Leading bytes of a file the basic provider inspects for the EICAR signature. */
    public static final int BASIC_SAMPLE_BYTES = 512;
}
