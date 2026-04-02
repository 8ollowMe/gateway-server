package com.followme.gatewayserver.response;

import java.util.UUID;

public record User(
        UUID userId,
        String username,
        String name,
        String email,
        String role,
        String status
) {}