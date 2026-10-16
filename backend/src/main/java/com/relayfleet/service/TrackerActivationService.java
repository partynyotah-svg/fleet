package com.relayfleet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.relayfleet.api.TrackerActivationResult;
import com.relayfleet.domain.Vehicle;
import com.relayfleet.domain.VehicleRepository;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class TrackerActivationService {
    private static final int ACTIVATION_ACCEPTED = 100;
    private final VehicleRepository vehicles;
    private final AuditService audit;
    private final RestClient smsClient;
    private final String username;
    private final String apiKey;
    private final String fleetDomain;
    private final String serverPort;
    private final String trackerPassword;

    public TrackerActivationService(VehicleRepository vehicles, AuditService audit,
            RestClient.Builder restClientBuilder,
            @Value("${relay.activation.username:}") String username,
            @Value("${relay.activation.api-key:}") String apiKey,
            @Value("${relay.activation.fleet-domain:}") String fleetDomain,
            @Value("${relay.activation.server-port:}") String serverPort,
            @Value("${relay.activation.tracker-password:}") String trackerPassword) {
        this.vehicles = vehicles;
        this.audit = audit;
        this.username = username;
        this.apiKey = apiKey;
        this.fleetDomain = fleetDomain;
        this.serverPort = serverPort;
        this.trackerPassword = trackerPassword;
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.smsClient = restClientBuilder.baseUrl("https://api.africastalking.com")
                .requestFactory(requestFactory).build();
    }

    public boolean isConfigured() {
        return !username.isBlank() && !apiKey.isBlank() && !trackerPassword.isBlank()
                && !fleetDomain.isBlank() && !fleetDomain.equalsIgnoreCase("localhost")
                && serverPort.matches("[0-9]{1,5}")
                && Integer.parseInt(serverPort) > 0 && Integer.parseInt(serverPort) <= 65535;
    }

    public String activationTarget() { return isConfigured() ? fleetDomain + ":" + serverPort : ""; }

    public TrackerActivationResult activate(java.util.UUID vehicleId, boolean confirmed, Authentication actor) {
        if (!confirmed) throw new IllegalArgumentException("Explicit confirmation is required before sending an activation SMS");
        if (!isConfigured()) throw new IllegalStateException("Tracker SMS activation is not configured");
        Vehicle vehicle = vehicles.findById(vehicleId).orElseThrow(() -> new IllegalArgumentException("Vehicle not found"));
        if (!"TK103".equals(vehicle.getTrackerProtocol())) throw new IllegalArgumentException("This tracker is not configured for the TK103 activation command");
        if (vehicle.getTrackerPhone() == null || vehicle.getTrackerPhone().isBlank()) throw new IllegalArgumentException("Add the tracker SIM phone number before activation");

        String message = trackerPassword + " adminip3 " + fleetDomain + " " + serverPort;
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("to", vehicle.getTrackerPhone());
        form.add("message", message);
        JsonNode response;
        try {
            response = smsClient.post().uri("/version1/messaging")
                    .header("apiKey", apiKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (org.springframework.web.client.RestClientException error) {
            throw new IllegalStateException("SMS provider request failed; check provider configuration and connectivity");
        }
        JsonNode recipients = response == null ? null : response.path("SMSMessageData").path("Recipients");
        boolean accepted = false;
        if (recipients != null && recipients.isArray()) {
            for (JsonNode recipient : recipients) {
                if (recipient.path("statusCode").asInt(-1) == ACTIVATION_ACCEPTED) {
                    accepted = true;
                    break;
                }
            }
        }
        if (!accepted) throw new IllegalStateException("SMS provider did not accept the activation message");

        String phone = vehicle.getTrackerPhone();
        String suffix = phone.substring(Math.max(0, phone.length() - 4));
        Instant acceptedAt = Instant.now();
        audit.record(actor, "tracker.activation_sms_accepted", "vehicle", vehicleId.toString(),
                Map.of("vehicleCode", vehicle.getVehicleCode(), "protocol", vehicle.getTrackerProtocol(), "recipientSuffix", suffix, "provider", "africas_talking"));
        return new TrackerActivationResult("ACCEPTED", "Provider accepted the activation SMS; carrier delivery is not confirmed.", suffix, acceptedAt);
    }
}