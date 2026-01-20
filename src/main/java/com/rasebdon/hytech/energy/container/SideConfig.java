package com.rasebdon.hytech.energy.container;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum SideConfig {
    NONE(0),   // No connection
    INPUT(1),  // Only receive
    OUTPUT(2), // Only extract
    BOTH(3);   // Both receive and extract

    private final int type;

    SideConfig(int type)
    {
        this.type = type;
    }

    public int getType() { return type; }

    public boolean canReceive() { return this == INPUT || this == BOTH; }
    public boolean canExtract() { return this == OUTPUT || this == BOTH; }

    public SideConfig next() {
        SideConfig[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    private static final Map<Integer, SideConfig> BY_TYPE =
            Arrays.stream(values()).collect(Collectors.toMap(v -> v.type, v -> v));

    public static SideConfig fromType(int type) {
        return BY_TYPE.get(type);
    }

    public static SideConfig[] getDefault() {
        return new SideConfig[] {
                SideConfig.BOTH,
                SideConfig.BOTH,
                SideConfig.BOTH,
                SideConfig.BOTH,
                SideConfig.BOTH,
                SideConfig.BOTH,
                SideConfig.BOTH
        };
    }
}