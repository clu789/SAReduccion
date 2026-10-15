package com.itt.sareduccion.auth;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.itt.sareduccion.auth.OracleCredentialValidator.AuthenticationRejectedException;
import com.itt.sareduccion.auth.OracleCredentialValidator.AuthenticationUnavailableException;
import com.itt.sareduccion.auth.OracleCredentialValidator.OracleIdentity;
import com.itt.sareduccion.model.ApplicantIdentity;
import com.itt.sareduccion.model.EstudianteDatos;
import com.itt.sareduccion.repository.AdministradorRepository;
import com.itt.sareduccion.repository.ApplicantRepository;
import com.itt.sareduccion.repository.EstudianteRepository;

@Service
public class AuthenticationService {

    private static final String STUDENT_ROLE = "ROL_SAR_ESTUDIANTE_SIM";
    private static final String ADMIN_ROLE = "ROL_SAR_ADMIN_SIM";
    private static final Pattern STUDENT_USERNAME = Pattern.compile("ALU_([A-Z0-9]+)");

    private final OracleCredentialValidator credentialValidator;
    private final EstudianteRepository estudianteRepository;
    private final AdministradorRepository administradorRepository;
    private final ApplicantRepository applicantRepository;

    public AuthenticationService(OracleCredentialValidator credentialValidator,
            EstudianteRepository estudianteRepository, AdministradorRepository administradorRepository,
            ApplicantRepository applicantRepository) {
        this.credentialValidator = credentialValidator;
        this.estudianteRepository = estudianteRepository;
        this.administradorRepository = administradorRepository;
        this.applicantRepository = applicantRepository;
    }

    public AuthenticatedPrincipal authenticate(String username, String password) {
        OracleIdentity oracleIdentity = credentialValidator.authenticate(username, password);
        boolean studentRole = oracleIdentity.hasRole(STUDENT_ROLE);
        boolean adminRole = oracleIdentity.hasRole(ADMIN_ROLE);
        if (studentRole == adminRole) {
            throw new AuthenticationRejectedException();
        }

        if (studentRole) {
            return authenticateStudent(oracleIdentity.getUsername());
        }
        return authenticateAdministrator(oracleIdentity.getUsername());
    }

    private AuthenticatedPrincipal authenticateStudent(String oracleUsername) {
        Matcher matcher = STUDENT_USERNAME.matcher(oracleUsername);
        if (!matcher.matches()) {
            throw new AuthenticationRejectedException();
        }

        String numeroControl = matcher.group(1);
        try {
            Optional<EstudianteDatos> profile = estudianteRepository.misDatos(numeroControl);
            if (!profile.isPresent() || !numeroControl.equals(profile.get().getNumeroControl())) {
                throw new AuthenticationRejectedException();
            }
        } catch (DataAccessException exception) {
            if (hasOracleError(exception, 20810) || hasOracleError(exception, 20811)) {
                throw new AuthenticationRejectedException();
            }
            throw new AuthenticationUnavailableException();
        }

        return new AuthenticatedPrincipal(oracleUsername, numeroControl, AuthRole.STUDENT);
    }

    private AuthenticatedPrincipal authenticateAdministrator(String oracleUsername) {
        String idAdministrador = oracleUsername.toUpperCase(Locale.ROOT);
        try {
            administradorRepository.validarPerfil(idAdministrador);
        } catch (DataAccessException exception) {
            if (hasOracleError(exception, 20820)) {
                throw new AuthenticationRejectedException();
            }
            throw new AuthenticationUnavailableException();
        }
        return new AuthenticatedPrincipal(oracleUsername, idAdministrador, AuthRole.ADMIN);
    }

    /**
     * Autenticacion provisional de aspirante: ficha + CURP. Oracle valida el
     * anio vigente y la unicidad del registro; aqui solo se traduce el
     * resultado a un principal de sesion sin exponer datos personales.
     */
    public ApplicantPrincipal authenticateApplicant(String ficha, String curp) {
        try {
            ApplicantIdentity identity = applicantRepository.validar(ficha, curp)
                    .orElseThrow(AuthenticationRejectedException::new);
            return new ApplicantPrincipal(identity.getFicha(), identity.getPeriodo(), identity.getAnio());
        } catch (DataAccessException exception) {
            if (hasOracleError(exception, 20840) || hasOracleError(exception, 20841)
                    || hasOracleError(exception, 20842)) {
                throw new AuthenticationRejectedException();
            }
            throw new AuthenticationUnavailableException();
        }
    }

    private boolean hasOracleError(Throwable throwable, int expectedCode) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof SQLException && ((SQLException) current).getErrorCode() == expectedCode) {
                return true;
            }
        }
        return false;
    }
}