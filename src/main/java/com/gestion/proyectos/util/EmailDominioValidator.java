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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprueba que el dominio del correo exista en DNS (registro MX, A o AAAA).
 * Solo rechaza cuando el DNS responde que el dominio NO existe; si el DNS falla
 * o tarda (sin red, timeout) deja pasar el correo para no bloquear registros.
 * Se puede apagar con app.validacion.email-dns=false.
 */
@Component
public class EmailDominioValidator {

    private static final Logger log = LoggerFactory.getLogger(EmailDominioValidator.class);

    private static final String DOMINIO_CONTROL = "google.com";

    private final boolean habilitado;
    private final Map<String, Boolean> cache = new ConcurrentHashMap<>();

    public EmailDominioValidator(@Value("${app.validacion.email-dns:true}") boolean habilitado) {
        this.habilitado = habilitado;
    }

    /**
     * @return null si el dominio es válido (o no se pudo comprobar), o el mensaje
     *         de error.
     */
    public String verificar(String email) {
        if (!habilitado || email == null)
            return null;
        int arroba = email.lastIndexOf('@');
        if (arroba < 0 || arroba == email.length() - 1)
            return null;
        String dominio = email.substring(arroba + 1).toLowerCase();

        Boolean existe = cache.get(dominio);
        if (existe == null) {
            existe = consultar(dominio);
            if (existe != null)
                cache.put(dominio, existe); // solo se cachean respuestas definitivas
        }
        if (existe == null || existe)
            return null;
        return "El dominio del correo (" + dominio + ") no existe o no puede recibir correos";
    }

    /**
     * true = existe, false = no existe, null = no se pudo determinar.
     * Si el DNS dice "no existe", se confirma con un dominio que seguro existe: si
     * ese tampoco
     * resuelve, el DNS de este servidor no es confiable (red filtrada) y no se
     * rechaza el correo.
     */
    protected Boolean consultar(String dominio) {
        Boolean existe = resolver(dominio);
        if (Boolean.FALSE.equals(existe) && !Boolean.TRUE.equals(resolver(DOMINIO_CONTROL))) {
            log.warn("El DNS no resuelve ni {}; se omite la verificación de {}", DOMINIO_CONTROL, dominio);
            return null;
        }
        return existe;
    }

    /**
     * Consulta cruda: true = tiene registros, false = NXDOMAIN o sin registros,
     * null = error de red.
     */
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
