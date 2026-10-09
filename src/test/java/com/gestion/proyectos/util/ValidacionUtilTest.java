package com.gestion.proyectos.util;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ValidacionUtilTest {

    @Test
    void literalRegex_escapaCaracteresEspeciales() {
        assertThat(ValidacionUtil.literalRegex("(")).isEqualTo("\\(");
        assertThat(ValidacionUtil.literalRegex(".*")).isEqualTo("\\.\\*");
        assertThat(ValidacionUtil.literalRegex("Sebas")).isEqualTo("Sebas");
    }

    @Test
    void literalRegex_produceUnaRegexValidaQueCoincideLiteralmente() {
        String raro = "a(b[c.*+?^$|{}\\";
        Pattern p = Pattern.compile(ValidacionUtil.literalRegex(raro));
        assertThat(p.matcher(raro).matches()).isTrue();
        assertThat(Pattern.compile(ValidacionUtil.literalRegex(".*")).matcher("cualquier cosa").find()).isFalse();
    }

    @Test
    void errorNombre_aceptaAbreviaturasYTildesDescompuestas() {
        assertThat(ValidacionUtil.errorNombre("Ma. Fernanda", "nombre")).isNull();
        assertThat(ValidacionUtil.errorNombre("O'Brien-Pérez", "apellido")).isNull();
        assertThat(ValidacionUtil.errorNombre(ValidacionUtil.limpiarEspacios("José"), "nombre")).isNull();
        assertThat(ValidacionUtil.errorNombre("Juan2", "nombre")).isNotNull();
        assertThat(ValidacionUtil.errorNombre("Juan..", "nombre")).isNotNull();
    }
}
