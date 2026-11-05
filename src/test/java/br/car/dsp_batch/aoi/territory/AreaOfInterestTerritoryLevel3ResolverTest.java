package br.car.dsp_batch.aoi.territory;

import br.car.dsp_batch.layer.introspection.SchemaIntrospectionService;
import br.car.dsp_batch.layer.metadata.QualifiedTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AreaOfInterestTerritoryLevel3ResolverTest {

    private static final QualifiedTable AOI_TABLE = new QualifiedTable("dsp", "area_of_interest");

    private JdbcTemplate geoTargetJdbc;
    private SchemaIntrospectionService schemaIntrospectionService;
    private AreaOfInterestTerritoryLevel3Resolver resolver;

    @BeforeEach
    void setUp() {
        geoTargetJdbc = mock(JdbcTemplate.class);
        schemaIntrospectionService = mock(SchemaIntrospectionService.class);
        resolver = new AreaOfInterestTerritoryLevel3Resolver(geoTargetJdbc, schemaIntrospectionService);
        when(schemaIntrospectionService.tableExists(eq(geoTargetJdbc), any(QualifiedTable.class)))
                .thenReturn(true);
    }

    @Test
    void resolve_ReadsStoredGeometryAndOrdersByIntersectionPercentage() {
        when(geoTargetJdbc.query(anyString(), any(RowMapper.class), any()))
                .thenReturn(List.of("l3-b"));

        Optional<String> result = resolver.resolve("aoi-1", AOI_TABLE);

        assertEquals("l3-b", result.orElseThrow());
        verify(geoTargetJdbc).query(
                org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("FROM dsp.area_of_interest a")
                                && sql.contains("ST_Intersection")
                                && sql.contains("NULLIF(aoi.aoi_area, 0)")
                                && !sql.contains("ST_GeomFromGeoJSON")
                                && !sql.contains("::geography")
                                && sql.contains("l3.id ASC")),
                any(RowMapper.class),
                eq("aoi-1"));
    }

    @Test
    void resolve_ReturnsEmptyWhenIdBlank() {
        assertTrue(resolver.resolve(null, AOI_TABLE).isEmpty());
        assertTrue(resolver.resolve("  ", AOI_TABLE).isEmpty());
    }
}
