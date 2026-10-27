package com.gestion.proyectos.modelo;

import java.time.LocalDate;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;

@Document(collection = "personas")
@Getter
@Setter
public class Doctor extends Persona {

    private String especialidad;
    private LocalDate fechaNacimiento;
    private String foto;
    private String biografia;
    private String estado = "INACTIVO"; // ACTIVO o INACTIVO

    // ── Página propia de reservas (/p/{slug}) ──
    @Indexed(unique = true, sparse = true)
    private String slug;
    /** Porcentajes de abono que puede elegir el profesional. */
    public static final java.util.List<Integer> PORCENTAJES_ABONO = java.util.List.of(10, 20, 30, 40, 50);
    public static final int PORCENTAJE_ABONO_DEFECTO = 20;
    /** Monto mínimo que se cobra en línea (por debajo, la comisión de la pasarela no compensa). */
    public static final int MIN_ABONO = 2_000;
    /** Precio base para calcular el abono mientras el profesional no publica el suyo. */
    public static final int PRECIO_CONSULTA_DEFECTO = 50_000;

    /** Valor de la consulta particular en pesos; sin él se usa el precio base. */
    private Integer precioConsulta;
    /** Porcentaje del precio que el paciente paga como abono al reservar. */
    private Integer porcentajeAbono;
    private String direccionConsultorio;
    /** Horas de anticipación para cancelar con devolución del abono. */
    private Integer horasCancelacion;

    public boolean tienePrecioPropio() {
        return precioConsulta != null && precioConsulta > 0;
    }

    /** Precio sobre el que se calcula el abono: el del profesional o, si no lo ha puesto, el precio base. */
    public int getPrecioEfectivo() {
        return tienePrecioPropio() ? precioConsulta : PRECIO_CONSULTA_DEFECTO;
    }

    public int porcentajeAbonoEfectivo() {
        return porcentajeAbono != null && PORCENTAJES_ABONO.contains(porcentajeAbono)
                ? porcentajeAbono : PORCENTAJE_ABONO_DEFECTO;
    }

    /** Abono en pesos: el porcentaje del precio redondeado a la centena, nunca menos del mínimo ni más del precio. */
    public int getAbono() {
        int precio = getPrecioEfectivo();
        long valor = Math.round(precio * porcentajeAbonoEfectivo() / 100.0 / 100.0) * 100;
        return (int) Math.min(precio, Math.max(MIN_ABONO, valor));
    }

    public int horasCancelacionEfectivas() {
        return horasCancelacion != null && horasCancelacion >= 0 ? horasCancelacion : 24;
    }

    public Doctor() {
        setRole("DOCTOR");
    }

    public Doctor(String nombre, String apellido, String telefono, String identificacion, String email, String password,
            String especialidad, LocalDate fechaNacimiento, String foto, String biografia) {
        setNombre(nombre);
        setApellido(apellido);
        setTelefono(telefono);
        setIdentificacion(identificacion);
        setEmail(email);
        setPassword(password);
        setRole("DOCTOR");
        this.especialidad = especialidad;
        this.fechaNacimiento = fechaNacimiento;
        this.foto = foto;
        this.biografia = biografia;
    }
}
