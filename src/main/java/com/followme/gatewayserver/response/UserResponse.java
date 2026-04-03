package com.followme.gatewayserver.response;

public record UserResponse(boolean success, User data, String error) {}
