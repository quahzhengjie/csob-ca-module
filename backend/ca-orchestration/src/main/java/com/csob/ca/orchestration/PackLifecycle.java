package com.csob.ca.orchestration;

import com.csob.ca.shared.enums.PackStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single authoritative definition of allowed PackStatus transitions.
 * Consumers MUST NOT perform transitions except via this class.
 *
 *   INITIATED           → DATA_GATHERED
 *   DATA_GATHERED       → CHECKED
 *   CHECKED             → SUMMARISED
 *   SUMMARISED          → VALIDATED
 *   VALIDATED           → REVIEWED
 *   REVIEWED            → APPROVED_FOR_FILE | REJECTED_BY_REVIEWER
 *
 * No backward transitions. No skips.
 */
public final class PackLifecycle {

    private static final Map<PackStatus, Set<PackStatus>> ALLOWED = new EnumMap<>(PackStatus.class);

    static {
        ALLOWED.put(PackStatus.INITIATED, EnumSet.of(PackStatus.DATA_GATHERED));
        ALLOWED.put(PackStatus.DATA_GATHERED, EnumSet.of(PackStatus.CHECKED));
        ALLOWED.put(PackStatus.CHECKED, EnumSet.of(PackStatus.SUMMARISED));
        ALLOWED.put(PackStatus.SUMMARISED, EnumSet.of(PackStatus.VALIDATED));
        ALLOWED.put(PackStatus.VALIDATED, EnumSet.of(PackStatus.REVIEWED));
        ALLOWED.put(PackStatus.REVIEWED,
                EnumSet.of(PackStatus.APPROVED_FOR_FILE, PackStatus.REJECTED_BY_REVIEWER));
        ALLOWED.put(PackStatus.APPROVED_FOR_FILE, EnumSet.noneOf(PackStatus.class));
        ALLOWED.put(PackStatus.REJECTED_BY_REVIEWER, EnumSet.noneOf(PackStatus.class));
    }

    private PackLifecycle() {}

    public static boolean isAllowed(PackStatus from, PackStatus to) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(PackStatus.class)).contains(to);
    }

    public static void requireAllowed(PackStatus from, PackStatus to) {
        if (!isAllowed(from, to)) {
            throw new IllegalStateException("Illegal transition: " + from + " -> " + to);
        }
    }
}
