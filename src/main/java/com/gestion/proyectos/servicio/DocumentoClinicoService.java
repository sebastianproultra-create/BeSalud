package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.DocumentoClinico;
import com.gestion.proyectos.repositorio.DocumentoClinicoRepositorio;
import com.gestion.proyectos.util.Reloj;
import java.io.IOException;
import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Documentos que el paciente adjunta a su historial para que los vean los médicos que lo atienden. Solo se
 * aceptan PDF, JPG y PNG, reconocidos por su contenido (no por la extensión que trae el archivo).
 */
@Service
public class DocumentoClinicoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoClinicoService.class);

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final int MAX_DOCUMENTOS = 10;
    private static final int MAX_NOMBRE = 80;

    private final DocumentoClinicoRepositorio repo;

    public DocumentoClinicoService(DocumentoClinicoRepositorio repo) {
        this.repo = repo;
    }

    /** Documentos del paciente, del más reciente al más antiguo, sin su contenido. */
    public List<DocumentoClinico> listar(String pacienteId) {
        return repo.listarSinContenido(pacienteId);
    }

    /**
     * Guarda el archivo. Devuelve null si se guardó o el motivo por el que no: "vacio", "tamano", "tipo",
     * "limite" o "procesando".
     */
    public String subir(String pacienteId, MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty())
            return "vacio";
        if (archivo.getSize() > MAX_BYTES)
            return "tamano";
        if (repo.countByPacienteId(pacienteId) >= MAX_DOCUMENTOS)
            return "limite";
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            log.warn("No se pudo leer el documento del paciente {}: {}", pacienteId, e.getMessage());
            return "procesando";
        }
        String tipo = tipoReal(bytes);
        if (tipo == null)
            return "tipo";
        DocumentoClinico d = new DocumentoClinico();
        d.setPacienteId(pacienteId);
        d.setNombre(nombreSeguro(archivo.getOriginalFilename(), tipo));
        d.setTipo(tipo);
        d.setTamano(bytes.length);
        d.setContenido(bytes);
        d.setSubidoEn(Reloj.ahora());
        repo.save(d);
        log.info("Documento clínico adjuntado: paciente={} tipo={} bytes={}", pacienteId, tipo, bytes.length);
        return null;
    }

    /** El documento con su contenido, solo si es de ese paciente. */
    public Optional<DocumentoClinico> buscar(String pacienteId, String documentoId) {
        if (pacienteId == null || documentoId == null)
            return Optional.empty();
        return repo.findById(documentoId).filter(d -> pacienteId.equals(d.getPacienteId()));
    }

    public boolean eliminar(String pacienteId, String documentoId) {
        Optional<DocumentoClinico> d = buscar(pacienteId, documentoId);
        d.ifPresent(repo::delete);
        return d.isPresent();
    }

    /** Tipo según las primeras bytes del archivo (firma de PDF, PNG o JPEG); null si no es ninguno. */
    static String tipoReal(byte[] b) {
        if (b == null)
            return null;
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-')
            return "application/pdf";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A)
            return "image/png";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF)
            return "image/jpeg";
        return null;
    }

    /** Nombre sin rutas ni caracteres raros, con la extensión que corresponde a su tipo real. */
    static String nombreSeguro(String original, String tipo) {
        String base = original == null ? "" : original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        int punto = base.lastIndexOf('.');
        if (punto > 0)
            base = base.substring(0, punto);
        base = Normalizer.normalize(base, Normalizer.Form.NFC)
                .replaceAll("[^\\p{L}\\p{N} ._()-]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (base.isEmpty())
            base = "documento";
        if (base.length() > MAX_NOMBRE)
            base = base.substring(0, MAX_NOMBRE).trim();
        String extension = switch (tipo) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            default -> ".jpg";
        };
        return base + extension;
    }
}
