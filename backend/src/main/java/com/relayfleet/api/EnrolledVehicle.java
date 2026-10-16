package com.relayfleet.api;

public record EnrolledVehicle(VehicleView vehicle, String deviceId, String oneTimeDeviceToken) {}