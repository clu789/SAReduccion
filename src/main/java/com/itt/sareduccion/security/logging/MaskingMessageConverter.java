package com.itt.sareduccion.security.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Conversor de Logback ({@code %mmsg}) que sustituye a {@code %msg} y aplica
 * {@link SensitiveDataMasker} a cada mensaje. Se registra en
 * {@code logback-spring.xml}.
 */
public class MaskingMessageConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return SensitiveDataMasker.mask(event.getFormattedMessage());
    }
}
