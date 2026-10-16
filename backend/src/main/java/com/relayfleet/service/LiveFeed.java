package com.relayfleet.service;

import com.relayfleet.api.VehicleView;
import com.relayfleet.domain.VehicleRepository;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class LiveFeed {
    private final VehicleRepository vehicles;
    private final CopyOnWriteArrayList<SseEmitter> clients = new CopyOnWriteArrayList<>();

    public LiveFeed(VehicleRepository vehicles) { this.vehicles = vehicles; }

    public SseEmitter subscribe() {
        if (clients.size() >= 500) throw new IllegalStateException("Live feed is at capacity; retry shortly.");
        SseEmitter emitter = new SseEmitter(0L);
        clients.add(emitter);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> clients.remove(emitter));
        emitter.onError(error -> clients.remove(emitter));
        return emitter;
    }

    @Scheduled(fixedDelay = 2000)
    public void publish() {
        if (clients.isEmpty()) return;
        List<VehicleView> snapshot = vehicles.findAllByOrderByVehicleCodeAsc().stream().map(VehicleView::from).toList();
        for (SseEmitter client : clients) {
            try {
                client.send(SseEmitter.event().name("fleet").data(snapshot));
            } catch (IOException | IllegalStateException error) {
                clients.remove(client);
            }
        }
    }
}