package com.rasebdon.hytech.core.util;

import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.server.core.HytaleServer;

public class EventBusUtil {
    public static <E extends IEvent<Void>> void dispatchIfListening(E event) {
        @SuppressWarnings("unchecked")
        Class<E> eventClass = (Class<E>) event.getClass();

        var dispatcher = HytaleServer.get()
                .getEventBus()
                .dispatchFor(eventClass);

        if (dispatcher.hasListener()) {
            dispatcher.dispatch(event);
        }
    }
}
