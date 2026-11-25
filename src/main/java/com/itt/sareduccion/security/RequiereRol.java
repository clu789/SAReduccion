package com.itt.sareduccion.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.itt.sareduccion.auth.AuthRole;

/**
 * Restringe un controller completo o un metodo a uno o varios roles. La
 * anotacion del metodo tiene prioridad sobre la de la clase, y ambas sobre las
 * reglas por prefijo de {@link RoleRules}.
 *
 * <pre>
 * &#64;RestController
 * &#64;RequestMapping("/api/convocatorias-publicas")
 * &#64;RequiereRol({AuthRole.STUDENT, AuthRole.APPLICANT})
 * public class MiController { ... }
 * </pre>
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiereRol {

    AuthRole[] value();
}
