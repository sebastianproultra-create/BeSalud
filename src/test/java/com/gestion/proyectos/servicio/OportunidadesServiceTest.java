package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Doctor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OportunidadesServiceTest {

    private static Doctor completo() {
        Doctor d = new Doctor();
        d.setFoto("foto.jpg");
        d.setBiografia("Bio");
        d.setPrecioConsulta(80_000);
        d.setDireccionConsultorio("Calle 1");
        return d;
    }

    private static List<String> textos(List<OportunidadesService.Senal> senales) {
        return senales.stream().map(OportunidadesService.Senal::texto).toList();
    }

    @Test
    void medico_cercaDeSuGrupoNoGeneraSenales() {
        assertThat(OportunidadesService.senales(completo(), 50, 100, 20, 10, 30)).isEmpty();
    }

    @Test
    void medico_muyPorDebajoDeSuGrupoYConCancelaciones() {
        List<OportunidadesService.Senal> senales = OportunidadesService.senales(completo(), 50, 100, 10, 25, 30);

        assertThat(textos(senales)).containsExactly("Baja ocupación", "Muchas cancelaciones");
        assertThat(senales.get(0).accion()).contains("10 %", "30 %");
    }

    @Test
    void medico_conPocasCitasNoSeMide() {
        assertThat(textos(OportunidadesService.senales(completo(), 4, 100, 1, 100, 50))).containsExactly("Pocos datos");
    }

    @Test
    void medico_sinHorariosYPerfilIncompleto() {
        Doctor d = completo();
        d.setFoto(null);
        d.setPrecioConsulta(null);

        List<OportunidadesService.Senal> senales = OportunidadesService.senales(d, 0, 0, 0, 0, 30);

        assertThat(textos(senales)).containsExactly("Sin horarios", "Perfil incompleto");
        assertThat(senales.get(1).accion()).contains("foto", "precio de la consulta");
    }

    private static OportunidadesService.Medico medico(long citas, long capacidad) {
        return new OportunidadesService.Medico("A B", "Pediatría", null, null, citas, capacidad, 0, 0, List.of());
    }

    private static String lectura(long citas, long capacidad, int promedio) {
        return OportunidadesService.especialidad("P", List.of(medico(citas, capacidad)), promedio).senal().texto();
    }

    @Test
    void especialidad_seLeeFrenteAlPromedioDeLaPlataforma() {
        assertThat(lectura(80, 100, 90)).isEqualTo("Faltan médicos");
        assertThat(lectura(25, 100, 20)).isEqualTo("Más usada");
        assertThat(lectura(24, 100, 20)).isEqualTo("En el promedio");
        assertThat(lectura(15, 100, 20)).isEqualTo("En el promedio");
        assertThat(lectura(14, 100, 20)).isEqualTo("Menos usada");
        assertThat(lectura(0, 100, 20)).isEqualTo("Sin citas");
        assertThat(lectura(0, 0, 20)).isEqualTo("Sin horarios");
        assertThat(OportunidadesService.especialidad("P", List.of(medico(30, 50), medico(30, 50)), 20).ocupacion()).isEqualTo(60);
    }
}
