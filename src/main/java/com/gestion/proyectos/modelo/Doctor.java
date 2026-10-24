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
    /** Valor de la consulta particular en pesos; null si el profesional no lo publica. */
    private Integer precioConsulta;
    /** Abono que se cobra al reservar, en pesos; 0 o null = se reserva sin pagar (como antes). */
    private Integer abono;
    private String direccionConsultorio;
    /** Horas de anticipación para cancelar con devolución del abono. */
    private Integer horasCancelacion;

    public boolean cobraAbono() {
        return abono != null && abono > 0;
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
