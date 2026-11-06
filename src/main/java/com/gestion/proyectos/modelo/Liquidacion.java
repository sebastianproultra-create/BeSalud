package com.gestion.proyectos.modelo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;

/**
 * Pago que BeSalud le hace a un médico por lo que ganó: qué citas cubre, lo cobrado, la comisión, lo que recibió y el
 * comprobante de la transferencia. La app no mueve dinero: el administrador paga por fuera y aquí deja la prueba.
 */
@Document(collection = "liquidaciones")
@Getter
@Setter
public class Liquidacion {

    @Id
    private String id;
    @Indexed(unique = true)
    private String codigo;
    @Indexed
    private String doctorId;
    private List<String> citaIds = new ArrayList<>();
    private long brutoPesos;
    private long comisionPesos;
    private long netoPesos;
    private String medio;
    /** Número que da el banco. Único: la misma transferencia no sirve de prueba para dos liquidaciones. */
    @Indexed(unique = true)
    private String numeroOperacion;
    private byte[] comprobante;
    private String comprobanteNombre;
    private String comprobanteTipo;
    /** SHA-256 del comprobante: delata el mismo archivo usado en otra liquidación o cambiado después. */
    @Indexed
    private String comprobanteHash;
    private String registradaPor;
    private LocalDateTime registradaEn;

    public boolean isComprobantePdf() {
        return "application/pdf".equals(comprobanteTipo);
    }
}
