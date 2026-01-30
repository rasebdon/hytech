package com.rasebdon.hytech.energy.systems;

import com.hypixel.hytale.event.IEventRegistry;
import com.rasebdon.hytech.core.systems.PipeRenderSystem;
import com.rasebdon.hytech.energy.IEnergyContainer;
import com.rasebdon.hytech.energy.events.EnergyContainerChangedEvent;

public class EnergyPipeRenderSystem extends PipeRenderSystem<IEnergyContainer> {
    public EnergyPipeRenderSystem(IEventRegistry eventRegistry) {
        super(eventRegistry, EnergyContainerChangedEvent.class);
    }
}
