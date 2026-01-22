package com.rasebdon.hytech.energy.network;

import com.rasebdon.hytech.energy.container.IEnergyContainer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EnergyCableNetwork {
    private final Set<EnergyCableComponent> cables = new HashSet<>();
    private final List<IEnergyContainer> pushTargets = new ArrayList<>();
    private final List<IEnergyContainer> pullTargets = new ArrayList<>();
}
