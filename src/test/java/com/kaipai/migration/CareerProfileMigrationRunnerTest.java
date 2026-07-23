package com.kaipai.migration;

import static org.junit.jupiter.api.Assertions.*;

import com.kaipai.CareerProfileMigrationRunner;
import org.junit.jupiter.api.Test;

class CareerProfileMigrationRunnerTest {
    @Test void inspectReportsMalformedExtendedFieldRows() {
        CareerProfileMigrationRunner runner = new CareerProfileMigrationRunner();
        runner.seedProfile(10001L, "not-json");
        assertTrue(runner.inspect().malformedExtendedFieldProfileIds().contains(10001L));
    }
    @Test void dryRunDoesNotCreateAssetsOrRelations() {
        CareerProfileMigrationRunner runner = new CareerProfileMigrationRunner();
        runner.seedProfile(10001L, "{}"); runner.seedAssetAndRelation();
        var before = runner.inspect(); var dryRun = runner.dryRun(10001L); var after = runner.inspect();
        assertEquals(before.mediaAssetCount(), dryRun.mediaAssetCount()); assertEquals(before.relationCount(), dryRun.relationCount());
        assertEquals(before.mediaAssetCount(), after.mediaAssetCount()); assertEquals(before.relationCount(), after.relationCount());
    }
    @Test void wangHuohuoFixtureLoadsAndMatchesOfflineBaseline() throws Exception {
        var baseline = CareerProfileMigrationRunner.loadBaseline();
        CareerProfileMigrationRunner runner = new CareerProfileMigrationRunner();
        for (int i = 0; i < baseline.profileCount(); i++) runner.seedProfile(10001L + i, "{}");
        for (int i = 0; i < baseline.mediaAssetCount(); i++) runner.seedAssetAndRelation();
        assertTrue(runner.verify(baseline).passed());
    }
}
