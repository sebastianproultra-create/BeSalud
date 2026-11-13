package com.gestion.proyectos.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Rechaza solo si el DNS confirma que el dominio no existe; ante fallos de red deja pasar. Apagable con app.validacion.email-dns=false.
@Component
public class EmailDominioValidator {

    private static final Logger log = LoggerFactory.getLogger(EmailDominioValidator.class);

    private static final String DOMINIO_CONTROL = "google.com";
    private static final int MAX_CACHE = 1000;

    private final boolean habilitado;
    // Solo dominios existentes: un negativo cacheado bloquearía para siempre un dominio recién creado o un NXDOMAIN pasajero.
    private final Set<String> existentes = ConcurrentHashMap.newKeySet();

    public EmailDominioValidator(@Value("${app.validacion.email-dns:true}") boolean habilitado) {
        this.habilitado = habilitado;
    }

    /** null si el dominio es válido (o no se pudo comprobar), o el mensaje de error. */
    public String verificar(String email) {
        if (!habilitado || email == null)
            return null;
        int arroba = email.lastIndexOf('@');
        if (arroba < 0 || arroba == email.length() - 1)
            return null;
        String dominio = email.substring(arroba + 1).toLowerCase();
        if (existentes.contains(dominio))
            return null;

        Boolean existe = consultar(dominio);
        if (Boolean.TRUE.equals(existe)) {
            if (existentes.size() >= MAX_CACHE)
                existentes.clear();
            existentes.add(dominio);
        }
        if (!Boolean.FALSE.equals(existe))
            return null;
        return "El dominio del correo (" + dominio + ") no existe o no puede recibir correos";
    }

    // true = existe, false = no existe, null = indeterminado. Un "no existe" se contrasta con google.com:
    // si ese tampoco resuelve, el DNS del servidor no es confiable y no se rechaza el correo.
    protected Boolean consultar(String dominio) {
        Boolean existe = resolver(dominio);
        if (Boolean.FALSE.equals(existe) && !Boolean.TRUE.equals(resolver(DOMINIO_CONTROL))) {
            log.warn("El DNS no resuelve ni {}; se omite la verificación de {}", DOMINIO_CONTROL, dominio);
            return null;
        }
        return existe;
    }

    /** true = tiene registros, false = NXDOMAIN o sin registros, null = error de red. */
    protected Boolean resolver(String dominio) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(dominio, new String[] { "MX", "A", "AAAA" });
            return attrs != null && attrs.size() > 0;
        } catch (NameNotFoundException e) {
            return false;
        } catch (NamingException e) {
            log.warn("No se pudo verificar el dominio {}: {}", dominio, e.getMessage());
            return null;
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ignored) {
                    /* nada */ }
            }
        }
    }
}
