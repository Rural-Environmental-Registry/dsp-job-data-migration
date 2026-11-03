package br.car.dsp_batch.kpi.conversion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AreaUnitConverterTest {

    @Test
    void fromSquareMetres_ConvertsToHectares() {
        BigDecimal result = AreaUnitConverter.fromSquareMetres(new BigDecimal("10000"), "ha");

        assertEquals(new BigDecimal("1.000"), result);
    }

    @Test
    void fromSquareMetres_ConvertsToSquareKilometres() {
        BigDecimal result = AreaUnitConverter.fromSquareMetres(new BigDecimal("1000000"), "km²");

        assertEquals(new BigDecimal("1.000"), result);
    }

    @Test
    void fromSquareMetres_RejectsUnknownUnit() {
        assertThrows(IllegalArgumentException.class,
                () -> AreaUnitConverter.fromSquareMetres(BigDecimal.ONE, "acre"));
    }
}
