package br.car.dsp_batch.aoi.service;

import br.car.dsp_batch.aoi.config.AreaOfInterestConfig;
import br.car.dsp_batch.aoi.metadata.AreaOfInterestTableMetadata;
import br.car.dsp_batch.aoi.territory.AreaOfInterestTerritoryLevel3Resolver;
import br.car.dsp_batch.layer.dto.LayerFeatureRecord;
import br.car.dsp_batch.layer.metadata.ColumnMetadata;
import br.car.dsp_batch.layer.metadata.QualifiedTable;
import br.car.dsp_batch.temporal.TemporalTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AreaOfInterestPersistenceServiceTest {

    private AreaOfInterestTerritoryLevel3Resolver resolver;
    private AreaOfInterestPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        resolver = mock(AreaOfInterestTerritoryLevel3Resolver.class);
        JdbcTemplate target = mock(JdbcTemplate.class);
        JdbcTemplate geoTarget = mock(JdbcTemplate.class);
        persistenceService = new AreaOfInterestPersistenceService(target, geoTarget, resolver);
        when(target.batchUpdate(anyString(), any(), anyInt(), any())).thenReturn(new int[][] {{1}});
        when(geoTarget.batchUpdate(anyString(), any(), anyInt(), any())).thenReturn(new int[][] {{1}});
    }

    @Test
    void upsertAll_ResolvesTerritoryWhenSourceColumnMissing() {
        when(resolver.resolve(anyString(), any(QualifiedTable.class))).thenReturn(Optional.of("3509502"));

        LayerFeatureRecord item = new LayerFeatureRecord();
        item.setId("aoi-1");
        item.setGeometryGeoJson("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}");
        item.putAttribute("id", "aoi-1");
        item.putAttribute("creation_date", java.time.OffsetDateTime.now());

        persistenceService.upsertAll(List.of(item), metadataWithoutSourceTerritoryColumn());

        verify(resolver).resolve("aoi-1", new QualifiedTable("dsp", "area_of_interest"));
        assertEquals("3509502", item.getAttribute(AreaOfInterestConfig.TERRITORY_LEVEL_3_ID_COLUMN));
    }

    @Test
    void upsertAll_SkipsResolverWhenSourceValuePresent() {
        LayerFeatureRecord item = new LayerFeatureRecord();
        item.setId("aoi-1");
        item.setGeometryGeoJson("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}");
        item.putAttribute("id", "aoi-1");
        item.putAttribute("creation_date", java.time.OffsetDateTime.now());
        item.putAttribute("territory_level_3_fk", "from-source");

        persistenceService.upsertAll(List.of(item), metadataWithSourceTerritoryColumn());

        verify(resolver, never()).resolve(anyString(), any(QualifiedTable.class));
    }

    @Test
    void upsertAll_FallsBackToResolverWhenSourceValueBlank() {
        when(resolver.resolve(anyString(), any(QualifiedTable.class))).thenReturn(Optional.of("computed"));

        LayerFeatureRecord item = new LayerFeatureRecord();
        item.setId("aoi-1");
        item.setGeometryGeoJson("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}");
        item.putAttribute("id", "aoi-1");
        item.putAttribute("creation_date", java.time.OffsetDateTime.now());
        item.putAttribute("territory_level_3_fk", "  ");

        persistenceService.upsertAll(List.of(item), metadataWithSourceTerritoryColumn());

        verify(resolver).resolve("aoi-1", new QualifiedTable("dsp", "area_of_interest"));
        assertEquals("computed", item.getAttribute(AreaOfInterestConfig.TERRITORY_LEVEL_3_ID_COLUMN));
    }

    private static AreaOfInterestTableMetadata metadataWithoutSourceTerritoryColumn() {
        return new AreaOfInterestTableMetadata(
                "area_of_interest",
                "area-of-interest",
                new QualifiedTable("conservation", "units"),
                new QualifiedTable("dsp", "area_of_interest"),
                "id",
                TemporalTestFixtures.timestamptz("creation_date"),
                null,
                null,
                null,
                "geom",
                4674,
                List.of(
                        new ColumnMetadata("id", "int8", null, null, null, false, false),
                        new ColumnMetadata("creation_date", "timestamptz", null, null, null, false, false),
                        new ColumnMetadata("geom", "geometry", null, null, null, true, true)
                ),
                List.of(),
                "1=1"
        );
    }

    private static AreaOfInterestTableMetadata metadataWithSourceTerritoryColumn() {
        return new AreaOfInterestTableMetadata(
                "area_of_interest",
                "area-of-interest",
                new QualifiedTable("conservation", "units"),
                new QualifiedTable("dsp", "area_of_interest"),
                "id",
                TemporalTestFixtures.timestamptz("creation_date"),
                null,
                "territory_level_3_fk",
                null,
                "geom",
                4674,
                List.of(
                        new ColumnMetadata("id", "int8", null, null, null, false, false),
                        new ColumnMetadata("creation_date", "timestamptz", null, null, null, false, false),
                        new ColumnMetadata("territory_level_3_fk", "int8", null, null, null, false, false),
                        new ColumnMetadata("geom", "geometry", null, null, null, true, true)
                ),
                List.of(),
                "1=1"
        );
    }
}
