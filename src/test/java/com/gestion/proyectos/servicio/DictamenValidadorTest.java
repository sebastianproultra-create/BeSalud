package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Antecedentes;
import com.gestion.proyectos.modelo.CausaExterna;
import com.gestion.proyectos.modelo.Diagnostico;
import com.gestion.proyectos.modelo.Dictamen;
import com.gestion.proyectos.modelo.Medicamento;
import com.gestion.proyectos.modelo.SignosVitales;
import com.gestion.proyectos.modelo.TipoConsulta;
import com.gestion.proyectos.modelo.TipoDiagnostico;
import com.gestion.proyectos.modelo.ViaAdministracion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DictamenValidadorTest {

    /** Dictamen con solo los campos obligatorios. */
    private Dictamen minimo() {
        Dictamen d = new Dictamen();
        d.setTipoConsulta(TipoConsulta.PRIMERA_VEZ);
        d.setCausaExterna(CausaExterna.ENFERMEDAD_GENERAL);
        d.setMotivoConsulta("  Me duele la garganta ");
        d.setEnfermedadActual("Dolor desde hace 3 días");
        d.setExamenFisico("Amígdalas hipertróficas");
        d.setDiagnosticoPrincipal(dx("j03.9", "Amigdalitis aguda", TipoDiagnostico.CONFIRMADO_NUEVO));
        return d;
    }

    private static Diagnostico dx(String codigo, String descripcion, TipoDiagnostico tipo) {
        Diagnostico dx = new Diagnostico();
        dx.setCodigo(codigo);
        dx.setDescripcion(descripcion);
        dx.setTipo(tipo);
        return dx;
    }

    private static Medicamento medicamento() {
        Medicamento m = new Medicamento();
        m.setNombre("Acetaminofén 500 mg");
        m.setVia(ViaAdministracion.ORAL);
        m.setDosisFrecuencia("1 tableta cada 8 horas");
        m.setDuracion("Por 5 días");
        m.setCantidad(15);
        return m;
    }

    private static String validar(Dictamen d) {
        return DictamenValidador.validar(DictamenValidador.normalizar(d));
    }

    @Test
    void soloObligatorios_esValidoYNormaliza() {
        Dictamen n = DictamenValidador.normalizar(minimo());
        assertThat(DictamenValidador.validar(n)).isNull();
        assertThat(n.getMotivoConsulta()).isEqualTo("Me duele la garganta");
        assertThat(n.getDiagnosticoPrincipal().getCodigo()).isEqualTo("J039");
        assertThat(n.getAntecedentes()).isNull();
        assertThat(n.getSignosVitales()).isNull();
    }

    @Test
    void faltaCampoObligatorio_daError() {
        Dictamen d = minimo();
        d.setTipoConsulta(null);
        assertThat(validar(d)).contains("tipo de consulta");

        d = minimo();
        d.setCausaExterna(null);
        assertThat(validar(d)).contains("causa externa");

        d = minimo();
        d.setEnfermedadActual("   ");
        assertThat(validar(d)).isEqualTo("Falta la enfermedad actual.");

        d = minimo();
        d.setExamenFisico(null);
        assertThat(validar(d)).isEqualTo("Falta el examen físico.");

        d = minimo();
        d.setDiagnosticoPrincipal(new Diagnostico());
        assertThat(validar(d)).contains("diagnóstico principal es obligatorio");
    }

    @Test
    void codigoCie10_formatoYTipo() {
        Dictamen d = minimo();
        d.getDiagnosticoPrincipal().setCodigo("123");
        assertThat(validar(d)).contains("no es válido");

        d = minimo();
        d.getDiagnosticoPrincipal().setCodigo("a09x");
        assertThat(validar(d)).isNull();

        d = minimo();
        d.getDiagnosticoPrincipal().setTipo(null);
        assertThat(validar(d)).contains("tipo de diagnóstico");
    }

    @Test
    void relacionados_filasVaciasSeIgnoranYNoSeRepiten() {
        Dictamen d = minimo();
        d.setDiagnosticosRelacionados(new ArrayList<>(List.of(new Diagnostico(),
                dx("R509", "Fiebre", TipoDiagnostico.IMPRESION_DIAGNOSTICA))));
        assertThat(validar(d)).isNull();
        assertThat(DictamenValidador.normalizar(d).getDiagnosticosRelacionados()).hasSize(1);

        d.getDiagnosticosRelacionados().add(dx("J03.9", "Repetido", TipoDiagnostico.CONFIRMADO_NUEVO));
        assertThat(validar(d)).contains("J039 está repetido");

        d = minimo();
        d.setDiagnosticosRelacionados(new ArrayList<>(List.of(dx("R509", "", TipoDiagnostico.CONFIRMADO_NUEVO))));
        assertThat(validar(d)).contains("diagnóstico relacionado 1");
    }

    @Test
    void signosVitales_rangosTensionEImcCalculado() {
        Dictamen d = minimo();
        SignosVitales sv = new SignosVitales();
        sv.setPeso(70.0);
        sv.setTalla(175.0);
        sv.setImc(99.0); // lo que mande el cliente se ignora
        d.setSignosVitales(sv);
        Dictamen n = DictamenValidador.normalizar(d);
        assertThat(DictamenValidador.validar(n)).isNull();
        assertThat(n.getSignosVitales().getImc()).isEqualTo(22.9);

        sv.setTensionSistolica(120);
        assertThat(validar(d)).contains("tensión arterial completa");
        sv.setTensionDiastolica(130);
        assertThat(validar(d)).contains("mayor que la diastólica");
        sv.setTensionDiastolica(80);
        assertThat(validar(d)).isNull();

        sv.setTemperatura(50.0);
        assertThat(validar(d)).contains("temperatura debe estar entre 30 y 45");
        sv.setTemperatura(36.5);
        sv.setSaturacionOxigeno(101);
        assertThat(validar(d)).contains("saturación");
    }

    @Test
    void medicamentos_filaIncompletaOCantidadInvalida() {
        Dictamen d = minimo();
        d.setMedicamentos(new ArrayList<>(List.of(medicamento())));
        assertThat(validar(d)).isNull();

        d.getMedicamentos().get(0).setVia(null);
        assertThat(validar(d)).isEqualTo("Medicamento 1: seleccione la vía de administración.");

        d.getMedicamentos().get(0).setVia(ViaAdministracion.ORAL);
        d.getMedicamentos().get(0).setCantidad(0);
        assertThat(validar(d)).contains("cantidad debe estar entre 1 y 9999");
    }

    @Test
    void incapacidad_requiereDiasYJustificacionJuntos() {
        Dictamen d = minimo();
        d.setIncapacidadDias(3);
        assertThat(validar(d)).contains("justificación");

        d.setIncapacidadJustificacion("Reposo por fiebre");
        assertThat(validar(d)).isNull();

        d.setIncapacidadDias(31);
        assertThat(validar(d)).contains("entre 1 y 30");

        d.setIncapacidadDias(null);
        assertThat(validar(d)).contains("Indique los días");
    }

    @Test
    void alergias_seResaltanSoloSiHayContenidoReal() {
        Antecedentes a = new Antecedentes();
        a.setAlergicos("Niega");
        assertThat(a.isTieneAlergias()).isFalse();
        a.setAlergicos("ninguna.");
        assertThat(a.isTieneAlergias()).isFalse();
        a.setAlergicos("Penicilina");
        assertThat(a.isTieneAlergias()).isTrue();
    }

    @Test
    void normalizar_descartaFirmaYFechasEnviadasPorElCliente() {
        Dictamen d = minimo();
        d.setFirmadoPor("Otro");
        d.setEspecialidadFirmante("Otra");
        d.setFechaEmision(java.time.LocalDateTime.MIN);
        d.setDiagnostico("texto viejo");
        Dictamen n = DictamenValidador.normalizar(d);
        assertThat(n.getFirmadoPor()).isNull();
        assertThat(n.getEspecialidadFirmante()).isNull();
        assertThat(n.getFechaEmision()).isNull();
        assertThat(n.getDiagnostico()).isNull();
    }
}
