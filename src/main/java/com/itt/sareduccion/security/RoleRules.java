package com.itt.sareduccion.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.itt.sareduccion.auth.AuthRole;

/**
 * Reglas de autorizacion por prefijo de ruta. Se evaluan en orden y gana la
 * primera coincidencia. Un patron es una ruta exacta ({@code /api/auth/me}) o
 * un prefijo terminado en {@code /**} ({@code /api/admin/**}).
 */
public final class RoleRules {

    private final List<Rule> rules = new ArrayList<>();
    private final List<String> publicPatterns = new ArrayList<>();

    public RoleRules permitAll(String... patterns) {
        Collections.addAll(publicPatterns, patterns);
        return this;
    }

    public RoleRules require(String pattern, AuthRole first, AuthRole... rest) {
        rules.add(new Rule(pattern, EnumSet.of(first, rest)));
        return this;
    }

    /**
     * Agrega reglas escritas como {@code /api/x/**=ADMIN,STUDENT;/api/y/**=APPLICANT}.
     * Se anteponen a las existentes para que la configuracion tenga prioridad.
     */
    public RoleRules prepend(String definition) {
        if (definition == null || definition.trim().isEmpty()) {
            return this;
        }
        List<Rule> parsed = new ArrayList<>();
        for (String entry : definition.split(";")) {
            if (entry.trim().isEmpty()) {
                continue;
            }
            String[] parts = entry.split("=", 2);
            if (parts.length != 2 || !parts[0].trim().startsWith("/")) {
                throw new IllegalArgumentException("Regla de rol no valida: " + entry);
            }
            Set<AuthRole> roles = EnumSet.noneOf(AuthRole.class);
            for (String role : parts[1].split(",")) {
                roles.add(AuthRole.valueOf(role.trim().toUpperCase(java.util.Locale.ROOT)));
            }
            parsed.add(new Rule(parts[0].trim(), roles));
        }
        rules.addAll(0, parsed);
        return this;
    }

    public boolean isPublic(String path) {
        for (String pattern : publicPatterns) {
            if (matches(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /** Roles exigidos para la ruta, o {@code null} si basta con estar autenticado. */
    public Set<AuthRole> rolesFor(String path) {
        for (Rule rule : rules) {
            if (matches(rule.pattern, path)) {
                return rule.roles;
            }
        }
        return null;
    }

    static boolean matches(String pattern, String path) {
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
        return path.equals(pattern) || path.equals(pattern + "/");
    }

    private static final class Rule {
        private final String pattern;
        private final Set<AuthRole> roles;

        private Rule(String pattern, Set<AuthRole> roles) {
            this.pattern = pattern;
            this.roles = Collections.unmodifiableSet(roles);
        }
    }
}
