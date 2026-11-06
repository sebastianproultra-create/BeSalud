package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.DocumentoClinico;
import com.gestion.proyectos.repositorio.DocumentoClinicoRepositorio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentoClinicoServiceTest {

    @Mock DocumentoClinicoRepositorio repo;
    @InjectMocks DocumentoClinicoService service;

    private static final byte[] PDF = "%PDF-1.7 contenido".getBytes();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};

    @Test
    void reconoceElTipoPorElContenidoYNoPorLaExtension() {
        assertThat(DocumentoClinicoService.tipoReal(PDF)).isEqualTo("application/pdf");
        assertThat(DocumentoClinicoService.tipoReal(PNG)).isEqualTo("image/png");
        assertThat(DocumentoClinicoService.tipoReal(JPG)).isEqualTo("image/jpeg");
        assertThat(DocumentoClinicoService.tipoReal("<html><script>".getBytes())).isNull();
        assertThat(DocumentoClinicoService.tipoReal(new byte[]{'M', 'Z'})).isNull();
    }

    @Test
    void elNombreSeLimpiaYLlevaLaExtensionDeSuTipoReal() {
        assertThat(DocumentoClinicoService.nombreSeguro("C:\\fakepath\\Exámen sangre (oct).PDF", "application/pdf"))
                .isEqualTo("Exámen sangre (oct).pdf");
        assertThat(DocumentoClinicoService.nombreSeguro("../../etc/passwd", "image/png")).isEqualTo("passwd.png");
        assertThat(DocumentoClinicoService.nombreSeguro("<script>.jpg", "image/jpeg")).isEqualTo("script.jpg");
        assertThat(DocumentoClinicoService.nombreSeguro(null, "application/pdf")).isEqualTo("documento.pdf");
    }

    @Test
    void subirUnPdf_loGuardaConSuTipoReal() {
        var archivo = new MockMultipartFile("archivo", "dictamen.pdf", "application/octet-stream", PDF);

        assertThat(service.subir("p1", archivo)).isNull();

        ArgumentCaptor<DocumentoClinico> guardado = ArgumentCaptor.forClass(DocumentoClinico.class);
        verify(repo).save(guardado.capture());
        assertThat(guardado.getValue().getPacienteId()).isEqualTo("p1");
        assertThat(guardado.getValue().getTipo()).isEqualTo("application/pdf");
        assertThat(guardado.getValue().getNombre()).isEqualTo("dictamen.pdf");
        assertThat(guardado.getValue().getTamano()).isEqualTo(PDF.length);
    }

    @Test
    void rechazaArchivosQueNoSonPdfNiImagen() {
        var disfrazado = new MockMultipartFile("archivo", "examen.pdf", "application/pdf", "<html>".getBytes());

        assertThat(service.subir("p1", disfrazado)).isEqualTo("tipo");
        verify(repo, never()).save(any());
    }

    @Test
    void rechazaArchivosVaciosOGrandesYMasDeDiez() {
        assertThat(service.subir("p1", new MockMultipartFile("archivo", new byte[0]))).isEqualTo("vacio");
        assertThat(service.subir("p1", new MockMultipartFile("archivo", "a.pdf", "application/pdf",
                new byte[(int) DocumentoClinicoService.MAX_BYTES + 1]))).isEqualTo("tamano");
        when(repo.countByPacienteId("p1")).thenReturn((long) DocumentoClinicoService.MAX_DOCUMENTOS);
        assertThat(service.subir("p1", new MockMultipartFile("archivo", "a.pdf", "application/pdf", PDF))).isEqualTo("limite");
        verify(repo, never()).save(any());
    }

    @Test
    void soloElDuenoPuedeVerOEliminarSuDocumento() {
        DocumentoClinico d = new DocumentoClinico();
        d.setId("doc1");
        d.setPacienteId("p1");
        when(repo.findById("doc1")).thenReturn(Optional.of(d));

        assertThat(service.buscar("p2", "doc1")).isEmpty();
        assertThat(service.eliminar("p2", "doc1")).isFalse();
        verify(repo, never()).delete(any());

        assertThat(service.buscar("p1", "doc1")).contains(d);
        assertThat(service.eliminar("p1", "doc1")).isTrue();
        verify(repo).delete(d);
    }

    @Test
    void tamanoLegible() {
        DocumentoClinico d = new DocumentoClinico();
        d.setTamano(870_000);
        assertThat(d.getTamanoLegible()).isEqualTo("850 KB");
        d.setTamano(2_516_582);
        assertThat(d.getTamanoLegible()).isEqualTo("2,4 MB");
    }
}
