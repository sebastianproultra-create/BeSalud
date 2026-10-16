package com.gestion.proyectos.modelo;

import lombok.Getter;
import lombok.Setter;

/**
 * Signos vitales y antropometría (sección O). Numéricos para poder graficarlos.
 * El IMC lo calcula el sistema a partir de peso y talla, nunca lo digita el médico.
 */
@Getter
@Setter
public class SignosVitales {

    private Integer tensionSistolica;      // mmHg
    private Integer tensionDiastolica;     // mmHg
    private Integer frecuenciaCardiaca;    // lpm
    private Integer frecuenciaRespiratoria; // rpm
    private Double temperatura;            // °C
    private Integer saturacionOxigeno;     // %
    private Double peso;                   // kg
    private Double talla;                  // cm
    private Double imc;                    // kg/m², calculado

    /** IMC = peso / talla² (talla en metros), redondeado a 1 decimal; null si falta un dato. */
    public static Double calcularImc(Double pesoKg, Double tallaCm) {
        if (pesoKg == null || tallaCm == null || pesoKg <= 0 || tallaCm <= 0)
            return null;
        double metros = tallaCm / 100.0;
        return Math.round(pesoKg / (metros * metros) * 10.0) / 10.0;
    }

    public boolean isVacio() {
        return tensionSistolica == null && tensionDiastolica == null && frecuenciaCardiaca == null
                && frecuenciaRespiratoria == null && temperatura == null && saturacionOxigeno == null
                && peso == null && talla == null;
    }
}
