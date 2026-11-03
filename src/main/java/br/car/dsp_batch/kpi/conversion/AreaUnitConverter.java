package br.car.dsp_batch.kpi.conversion;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts square metres to the configured display/storage unit.
 */
public final class AreaUnitConverter {

    private static final int SCALE = 3;

    private AreaUnitConverter() {
    }

    public static BigDecimal fromSquareMetres(BigDecimal squareMetres, String unitOfMeasurement) {
        if (squareMetres == null) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal factor = factorFor(unitOfMeasurement);
        return squareMetres.divide(factor, SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal factorFor(String unitOfMeasurement) {
        if (unitOfMeasurement == null || unitOfMeasurement.isBlank()) {
            return BigDecimal.ONE;
        }
        return switch (unitOfMeasurement.trim()) {
            case "m²", "m2" -> BigDecimal.ONE;
            case "km²", "km2" -> new BigDecimal("1000000");
            case "ha" -> new BigDecimal("10000");
            case "ft²", "ft2" -> new BigDecimal("0.09290304");
            default -> throw new IllegalArgumentException(
                    "Unsupported unit-of-measurement '" + unitOfMeasurement
                            + "'. Expected m², km², ha or ft².");
        };
    }
}
