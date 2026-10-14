package com.itt.sareduccion.auth;

public final class AuthenticatedPrincipal {

    private final String oracleUsername;
    private final String profileId;
    private final AuthRole role;

    public AuthenticatedPrincipal(String oracleUsername, String profileId, AuthRole role) {
        this.oracleUsername = oracleUsername;
        this.profileId = profileId;
        this.role = role;
    }

    public String getOracleUsername() {
        return oracleUsername;
    }

    public String getProfileId() {
        return profileId;
    }

    public AuthRole getRole() {
        return role;
    }
}