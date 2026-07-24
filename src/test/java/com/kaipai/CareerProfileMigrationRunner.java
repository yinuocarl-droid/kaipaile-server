package com.kaipai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.kaipai.service.actor.support.ActorWorkDeduplicationSupport;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/** Real MySQL gate for the 00-199 career-profile fixture migration. */
public final class CareerProfileMigrationRunner {

    private static final String BASELINE_RESOURCE =
            "/profile-migration/wang-huohuo-baseline.json";
    private static final String GOLDEN_RESOURCE =
            "/profile-migration/wang-huohuo-works-golden.json";
    private static final String SOURCE_TYPE_MIGRATION = "migration";
    private static final Map<String, Long> EXPECTED_CATEGORIES =
            Map.of("aired", 14L, "upcoming", 6L, "stage", 3L, "horizontal", 6L);
    private static final ObjectMapper BASELINE_CANONICAL_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public CareerProfileMigrationRunner(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public InspectionReport inspect() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT user_id, extended_field
                        FROM actor_profile
                        WHERE deleted = 0
                        ORDER BY user_id
                        """)) {
            Set<Long> malformedUserIds = new LinkedHashSet<>();
            long profileCount = 0L;
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    profileCount++;
                    String extendedField = resultSet.getString("extended_field");
                    if (isMalformedObjectJson(extendedField)) {
                        malformedUserIds.add(resultSet.getLong("user_id"));
                    }
                }
            }
            return new InspectionReport(
                    Set.copyOf(malformedUserIds), profileCount, currentCounts(connection));
        } catch (SQLException error) {
            throw databaseFailure("inspect", error);
        }
    }

    public DryRunReport dryRun(long userId) {
        GoldenDataset golden = loadGolden();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setReadOnly(true);
            try {
                DatabaseCounts before = currentCounts(connection);
                ProfileState profile = findProfile(connection, userId, false);
                int plannedCreates = 0;
                int plannedSkips = 0;
                if (profile != null) {
                    for (GoldenWork work : golden.works()) {
                        String dedupeKey = ActorWorkDeduplicationSupport.dedupeKey(
                                work.projectName(), work.roleName());
                        if (findActiveExperienceId(connection, userId, dedupeKey) == null) {
                            plannedCreates++;
                        } else {
                            plannedSkips++;
                        }
                    }
                }
                DatabaseCounts after = currentCounts(connection);
                connection.rollback();
                return new DryRunReport(
                        profile != null, plannedCreates, plannedSkips, before, after);
            } catch (RuntimeException | SQLException error) {
                rollback(connection, error);
                throw error;
            }
        } catch (SQLException error) {
            throw databaseFailure("dry-run", error);
        }
    }

    public Baseline loadBaseline() {
        return validateBaseline(readResource(BASELINE_RESOURCE, Baseline.class));
    }

    public BaselineSnapshot snapshotBaseline(long userId) {
        try (Connection connection = dataSource.getConnection()) {
            try {
                connection.setReadOnly(true);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                connection.setAutoCommit(false);
                long profileCount = countProfiles(connection, userId);
                ProfileState profile = findProfile(connection, userId, false);
                long workLibraryVersion = profile == null ? 0L : profile.workLibraryVersion();
                List<WorkIdentity> identities = activeWorkIdentities(connection, userId, false);
                Baseline baseline = sealedBaseline(
                        1,
                        profileCount,
                        identities.size(),
                        workLibraryVersion,
                        hashIdentities(identities));
                BaselineSnapshot snapshot = validateSnapshot(new BaselineSnapshot(
                        baseline,
                        identities.stream().map(WorkIdentity::experienceId).toList()));
                connection.rollback();
                return snapshot;
            } catch (RuntimeException | SQLException error) {
                rollback(connection, error);
                throw error;
            }
        } catch (SQLException error) {
            throw databaseFailure("snapshot-baseline", error);
        }
    }

    public ApplyReport applyGolden(long userId, String batchId) {
        String normalizedBatchId = requireBatchId(batchId);
        String batchPrefix = batchPrefix(normalizedBatchId);
        GoldenDataset golden = loadGolden();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ProfileState profile = requireProfile(connection, userId, true);
                int skipped = 0;
                List<Long> createdIds = new ArrayList<>();
                for (int index = 0; index < golden.works().size(); index++) {
                    GoldenWork work = golden.works().get(index);
                    String normalizedProject =
                            ActorWorkDeduplicationSupport.normalizeName(work.projectName());
                    String normalizedRole =
                            ActorWorkDeduplicationSupport.normalizeName(work.roleName());
                    String dedupeKey = ActorWorkDeduplicationSupport.dedupeKey(
                            work.projectName(), work.roleName());
                    if (findActiveExperienceId(connection, userId, dedupeKey) != null) {
                        skipped++;
                        continue;
                    }
                    long experienceId = insertGoldenWork(
                            connection,
                            userId,
                            profile.profileId(),
                            batchPrefix,
                            work,
                            normalizedProject,
                            normalizedRole,
                            dedupeKey,
                            golden.works().size() - index);
                    createdIds.add(experienceId);
                }
                if (!createdIds.isEmpty()) {
                    incrementWorkLibraryVersion(connection, profile.profileId(), createdIds.size());
                }
                connection.commit();
                return new ApplyReport(
                        normalizedBatchId,
                        createdIds.size(),
                        skipped,
                        List.copyOf(createdIds));
            } catch (RuntimeException | SQLException error) {
                rollback(connection, error);
                throw error;
            }
        } catch (SQLException error) {
            throw databaseFailure("apply-golden", error);
        }
    }

    public VerificationReport verify(long userId) {
        GoldenDataset golden = loadGolden();
        try (Connection connection = dataSource.getConnection()) {
            List<CurrentWork> actualWorks = activeWorks(connection, userId);
            Map<String, CurrentWork> actualByDedupe = new LinkedHashMap<>();
            for (CurrentWork actual : actualWorks) {
                actualByDedupe.putIfAbsent(actual.dedupeKey(), actual);
            }

            Set<String> mismatchedFixtureIds = new LinkedHashSet<>();
            for (GoldenWork expected : golden.works()) {
                String dedupeKey = ActorWorkDeduplicationSupport.dedupeKey(
                        expected.projectName(), expected.roleName());
                CurrentWork actual = actualByDedupe.get(dedupeKey);
                if (actual == null || !matches(expected, actual)) {
                    mismatchedFixtureIds.add(expected.fixtureId());
                }
            }

            long distinctDedupeKeys = actualWorks.stream()
                    .map(CurrentWork::dedupeKey)
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .count();
            long migrationSources = actualWorks.stream()
                    .filter(work -> SOURCE_TYPE_MIGRATION.equals(work.sourceType()))
                    .count();
            Map<String, Long> categories = actualWorks.stream()
                    .collect(Collectors.groupingBy(
                            CurrentWork::publishStatus,
                            LinkedHashMap::new,
                            Collectors.counting()));
            boolean passed = actualWorks.size() == golden.works().size()
                    && distinctDedupeKeys == golden.works().size()
                    && migrationSources == golden.works().size()
                    && EXPECTED_CATEGORIES.equals(categories)
                    && mismatchedFixtureIds.isEmpty();
            return new VerificationReport(
                    passed,
                    actualWorks.size(),
                    distinctDedupeKeys,
                    migrationSources,
                    Map.copyOf(categories),
                    Set.copyOf(mismatchedFixtureIds));
        } catch (SQLException error) {
            throw databaseFailure("verify", error);
        }
    }

    public RestoreReport restoreFixture(
            long userId,
            String batchId,
            BaselineSnapshot snapshot,
            String expectedBaselineHash) {
        validateSnapshot(snapshot);
        validateExpectedBaselineHash(expectedBaselineHash, snapshot.baseline());
        String normalizedBatchId = requireBatchId(batchId);
        String prefix = batchPrefix(normalizedBatchId);
        GoldenDataset golden = loadGolden();
        try (Connection connection = dataSource.getConnection()) {
            try {
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                connection.setAutoCommit(false);
                long profileCount = countProfilesForUpdate(connection, userId);
                if (profileCount != snapshot.baseline().profileCount()) {
                    throw new IllegalStateException(
                            "restore refused because active profile count changed after snapshot");
                }
                ProfileState profile = requireProfile(connection, userId, true);
                List<BatchWorkIdentity> batchRows =
                        batchWorkIdentities(connection, userId, prefix);
                validateBatchRows(prefix, batchRows, golden);
                long expectedWorkLibraryVersion = Math.addExact(
                        snapshot.baseline().workLibraryVersion(), (long) batchRows.size());
                if (profile.workLibraryVersion() != expectedWorkLibraryVersion) {
                    throw new IllegalStateException(
                            "restore refused because work library version changed after fixture apply");
                }
                Set<Long> batchIds = batchRows.stream()
                        .map(BatchWorkIdentity::experienceId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                List<WorkIdentity> remaining = activeWorkIdentities(connection, userId, true).stream()
                        .filter(identity -> !batchIds.contains(identity.experienceId()))
                        .toList();
                List<Long> remainingIds =
                        remaining.stream().map(WorkIdentity::experienceId).toList();
                if (!remainingIds.equals(snapshot.activeWorkIds())
                        || !hashIdentities(remaining).equals(snapshot.baseline().activeWorksHash())) {
                    throw new IllegalStateException(
                            "restore refused because active works changed outside the requested batch");
                }

                int removed = deleteBatchWorksExact(connection, userId, batchRows);
                if (removed != batchRows.size()) {
                    throw new IllegalStateException(
                            "restore batch changed after precheck; delete count does not match active batch rows");
                }
                restoreWorkLibraryVersion(
                        connection, profile.profileId(), snapshot.baseline().workLibraryVersion());
                connection.commit();
                return new RestoreReport(
                        normalizedBatchId,
                        removed,
                        snapshot.baseline().workLibraryVersion());
            } catch (RuntimeException | SQLException error) {
                rollback(connection, error);
                throw error;
            }
        } catch (SQLException error) {
            throw databaseFailure("restore-fixture", error);
        }
    }

    public RestoreVerificationReport verifyRestore(
            long userId, BaselineSnapshot expected, String expectedBaselineHash) {
        validateSnapshot(expected);
        validateExpectedBaselineHash(expectedBaselineHash, expected.baseline());
        BaselineSnapshot actual = snapshotBaseline(userId);
        boolean passed = expected.baseline().equals(actual.baseline())
                && expected.activeWorkIds().equals(actual.activeWorkIds());
        return new RestoreVerificationReport(
                passed,
                expected.baseline(),
                actual.baseline(),
                expected.activeWorkIds(),
                actual.activeWorkIds());
    }

    private GoldenDataset loadGolden() {
        GoldenDataset dataset = readResource(GOLDEN_RESOURCE, GoldenDataset.class);
        if (dataset.schemaVersion() != 1
                || dataset.categoryCounts() == null
                || dataset.works() == null
                || dataset.works().size() != 29) {
            throw new IllegalStateException("golden fixture must contain schemaVersion 1 and 29 works");
        }
        Set<String> fixtureIds = new LinkedHashSet<>();
        Set<String> projectNames = new LinkedHashSet<>();
        Map<String, Long> categories = new LinkedHashMap<>();
        for (GoldenWork work : dataset.works()) {
            if (work.fixtureId() == null
                    || work.fixtureId().isBlank()
                    || work.projectName() == null
                    || work.projectName().isBlank()) {
                throw new IllegalStateException("golden fixture ids and project names are required");
            }
            if (!fixtureIds.add(work.fixtureId()) || !projectNames.add(work.projectName())) {
                throw new IllegalStateException("golden fixture ids and project names must be unique");
            }
            categories.merge(work.publishStatus(), 1L, Long::sum);
        }
        if (!EXPECTED_CATEGORIES.equals(dataset.categoryCounts())
                || !dataset.categoryCounts().equals(categories)) {
            throw new IllegalStateException("golden fixture categories must be 14/6/3/6");
        }
        return new GoldenDataset(
                dataset.schemaVersion(),
                Map.copyOf(dataset.categoryCounts()),
                List.copyOf(dataset.works()));
    }

    private <T> T readResource(String resourcePath, Class<T> type) {
        try (InputStream input = CareerProfileMigrationRunner.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("migration fixture is missing: " + resourcePath);
            }
            return objectMapper.readValue(input, type);
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("migration fixture cannot be parsed: " + resourcePath, error);
        }
    }

    private boolean isMalformedObjectJson(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return node == null || !node.isObject();
        } catch (Exception error) {
            return true;
        }
    }

    private long insertGoldenWork(
            Connection connection,
            long userId,
            long profileId,
            String batchPrefix,
            GoldenWork work,
            String normalizedProject,
            String normalizedRole,
            String dedupeKey,
            int sortNo) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO actor_experience (
                  user_id, actor_profile_id, drama_name, role_name, publish_status,
                  work_type_code, role_level_code, platform, sync_sound_status,
                  collaborators_json, achievement_text, normalized_drama_name,
                  normalized_role_name, dedupe_key, source_type, sort_no,
                  extended_field, rid, version, deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'migration', ?, NULL, ?, 0, 0)
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            int parameter = 1;
            statement.setLong(parameter++, userId);
            statement.setLong(parameter++, profileId);
            statement.setString(parameter++, work.projectName());
            statement.setString(parameter++, work.roleName());
            statement.setString(parameter++, work.publishStatus());
            statement.setString(parameter++, work.workTypeCode());
            statement.setString(parameter++, work.roleLevelCode());
            statement.setString(parameter++, work.platform());
            statement.setString(parameter++, work.syncSoundStatus());
            statement.setString(parameter++, objectMapper.writeValueAsString(work.collaborators()));
            statement.setString(parameter++, work.achievementText());
            statement.setString(parameter++, normalizedProject);
            statement.setString(parameter++, normalizedRole);
            statement.setString(parameter++, dedupeKey);
            statement.setInt(parameter++, sortNo);
            statement.setString(parameter, batchPrefix + work.fixtureId());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("golden work insert did not affect exactly one row");
            }
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new IllegalStateException("golden work insert did not return experience_id");
                }
                return generatedKeys.getLong(1);
            }
        } catch (SQLException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("golden work serialization failed", error);
        }
    }

    private boolean matches(GoldenWork expected, CurrentWork actual) {
        return Objects.equals(expected.projectName(), actual.projectName())
                && Objects.equals(expected.publishStatus(), actual.publishStatus())
                && Objects.equals(expected.workTypeCode(), actual.workTypeCode())
                && Objects.equals(expected.roleLevelCode(), actual.roleLevelCode())
                && Objects.equals(expected.roleName(), actual.roleName())
                && Objects.equals(expected.platform(), actual.platform())
                && Objects.equals(expected.syncSoundStatus(), actual.syncSoundStatus())
                && Objects.equals(expected.collaborators(), actual.collaborators())
                && Objects.equals(expected.achievementText(), actual.achievementText())
                && SOURCE_TYPE_MIGRATION.equals(actual.sourceType());
    }

    private List<CurrentWork> activeWorks(Connection connection, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT experience_id, drama_name, role_name, publish_status, work_type_code,
                       role_level_code, platform, sync_sound_status, collaborators_json,
                       achievement_text, dedupe_key, source_type
                FROM actor_experience
                WHERE user_id = ? AND deleted = 0
                ORDER BY experience_id
                """)) {
            statement.setLong(1, userId);
            List<CurrentWork> works = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    works.add(new CurrentWork(
                            resultSet.getLong("experience_id"),
                            resultSet.getString("drama_name"),
                            resultSet.getString("role_name"),
                            resultSet.getString("publish_status"),
                            resultSet.getString("work_type_code"),
                            resultSet.getString("role_level_code"),
                            resultSet.getString("platform"),
                            resultSet.getString("sync_sound_status"),
                            readStringList(resultSet.getString("collaborators_json")),
                            resultSet.getString("achievement_text"),
                            resultSet.getString("dedupe_key"),
                            resultSet.getString("source_type")));
                }
            }
            return works;
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                throw new IllegalStateException("collaborators_json is not an array");
            }
            List<String> values = new ArrayList<>();
            root.forEach(node -> values.add(node.asText()));
            return List.copyOf(values);
        } catch (Exception error) {
            throw new IllegalStateException("collaborators_json cannot be parsed", error);
        }
    }

    private ProfileState requireProfile(Connection connection, long userId, boolean lock)
            throws SQLException {
        ProfileState profile = findProfile(connection, userId, lock);
        if (profile == null) {
            throw new IllegalStateException("migration target profile does not exist");
        }
        return profile;
    }

    private ProfileState findProfile(Connection connection, long userId, boolean lock)
            throws SQLException {
        String sql = """
                SELECT actor_profile_id, work_library_version
                FROM actor_profile
                WHERE user_id = ? AND deleted = 0
                ORDER BY actor_profile_id
                LIMIT 1
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new ProfileState(
                        resultSet.getLong("actor_profile_id"),
                        resultSet.getLong("work_library_version"));
            }
        }
    }

    private Long findActiveExperienceId(
            Connection connection, long userId, String dedupeKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT experience_id
                FROM actor_experience
                WHERE user_id = ? AND dedupe_key = ? AND deleted = 0
                ORDER BY experience_id
                LIMIT 1
                """)) {
            statement.setLong(1, userId);
            statement.setString(2, dedupeKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("experience_id") : null;
            }
        }
    }

    private void incrementWorkLibraryVersion(Connection connection, long profileId, int delta)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                UPDATE actor_profile
                SET work_library_version = work_library_version + ?
                WHERE actor_profile_id = ? AND deleted = 0
                """)) {
            statement.setInt(1, delta);
            statement.setLong(2, profileId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("profile version increment did not affect one row");
            }
        }
    }

    private void restoreWorkLibraryVersion(
            Connection connection, long profileId, long workLibraryVersion) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                UPDATE actor_profile
                SET work_library_version = ?
                WHERE actor_profile_id = ? AND deleted = 0
                """)) {
            statement.setLong(1, workLibraryVersion);
            statement.setLong(2, profileId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("profile version restore did not affect one row");
            }
        }
    }

    private int deleteBatchWorksExact(
            Connection connection, long userId, List<BatchWorkIdentity> batchRows)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                DELETE FROM actor_experience
                WHERE experience_id = ? AND user_id = ? AND rid = ? AND dedupe_key = ?
                  AND source_type = 'migration' AND deleted = 0
                """)) {
            int removed = 0;
            for (BatchWorkIdentity row : batchRows) {
                statement.setLong(1, row.experienceId());
                statement.setLong(2, userId);
                statement.setString(3, row.rid());
                statement.setString(4, row.dedupeKey());
                int affected = statement.executeUpdate();
                if (affected != 1) {
                    throw new IllegalStateException(
                            "restore batch row changed after locking precheck");
                }
                removed += affected;
            }
            return removed;
        }
    }

    private List<BatchWorkIdentity> batchWorkIdentities(
            Connection connection, long userId, String prefix) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT experience_id, dedupe_key, rid
                FROM actor_experience
                WHERE user_id = ? AND deleted = 0 AND source_type = 'migration'
                  AND LEFT(rid, ?) = ?
                ORDER BY experience_id
                FOR UPDATE
                """)) {
            statement.setLong(1, userId);
            statement.setInt(2, prefix.length());
            statement.setString(3, prefix);
            List<BatchWorkIdentity> identities = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    identities.add(new BatchWorkIdentity(
                            resultSet.getLong("experience_id"),
                            safe(resultSet.getString("dedupe_key")),
                            resultSet.getString("rid")));
                }
            }
            return List.copyOf(identities);
        }
    }

    private void validateBatchRows(
            String prefix, List<BatchWorkIdentity> batchRows, GoldenDataset golden) {
        Map<String, String> expectedDedupeByRid = golden.works().stream()
                .collect(Collectors.toMap(
                        work -> prefix + work.fixtureId(),
                        work -> ActorWorkDeduplicationSupport.dedupeKey(
                                work.projectName(), work.roleName())));
        Set<String> seenMarkers = new LinkedHashSet<>();
        for (BatchWorkIdentity row : batchRows) {
            String expectedDedupe = expectedDedupeByRid.get(row.rid());
            if (expectedDedupe == null) {
                throw new IllegalStateException(
                        "restore refused because batch contains an unknown fixture marker");
            }
            if (!seenMarkers.add(row.rid())) {
                throw new IllegalStateException(
                        "restore refused because batch contains duplicate fixture markers");
            }
            if (!expectedDedupe.equals(row.dedupeKey())) {
                throw new IllegalStateException(
                        "restore refused because fixture marker and dedupe key disagree");
            }
        }
    }

    private List<WorkIdentity> activeWorkIdentities(
            Connection connection, long userId, boolean lock)
            throws SQLException {
        String sql = """
                SELECT experience_id, dedupe_key
                FROM actor_experience
                WHERE user_id = ? AND deleted = 0
                ORDER BY experience_id
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            return readIdentities(statement);
        }
    }

    private List<WorkIdentity> readIdentities(PreparedStatement statement) throws SQLException {
        List<WorkIdentity> identities = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                identities.add(new WorkIdentity(
                        resultSet.getLong("experience_id"),
                        safe(resultSet.getString("dedupe_key"))));
            }
        }
        identities.sort(Comparator.comparingLong(WorkIdentity::experienceId));
        return List.copyOf(identities);
    }

    private long countProfiles(Connection connection, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM actor_profile WHERE user_id = ? AND deleted = 0")) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private long countProfilesForUpdate(Connection connection, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT actor_profile_id
                FROM actor_profile
                WHERE user_id = ? AND deleted = 0
                ORDER BY actor_profile_id
                FOR UPDATE
                """)) {
            statement.setLong(1, userId);
            long count = 0L;
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    count++;
                }
            }
            return count;
        }
    }

    private DatabaseCounts currentCounts(Connection connection) throws SQLException {
        long assets = queryLong(
                connection, "SELECT COUNT(*) FROM actor_media_asset WHERE deleted = 0");
        long relations = queryLong(
                connection,
                """
                SELECT
                  (SELECT COUNT(*) FROM actor_profile_asset WHERE deleted = 0)
                  + (SELECT COUNT(*) FROM actor_work_asset WHERE deleted = 0)
                  + (SELECT COUNT(*) FROM share_card_asset WHERE deleted = 0)
                """);
        long works = queryLong(
                connection, "SELECT COUNT(*) FROM actor_experience WHERE deleted = 0");
        return new DatabaseCounts(assets, relations, works);
    }

    private long queryLong(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private String hashIdentities(List<WorkIdentity> identities) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            identities.stream()
                    .sorted(Comparator.comparingLong(WorkIdentity::experienceId))
                    .forEach(identity -> digest.update(
                            (identity.experienceId() + ":" + identity.dedupeKey() + "\n")
                                    .getBytes(StandardCharsets.UTF_8)));
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private Baseline sealedBaseline(
            int schemaVersion,
            long profileCount,
            long activeWorkCount,
            long workLibraryVersion,
            String activeWorksHash) {
        Baseline unsigned = new Baseline(
                schemaVersion,
                profileCount,
                activeWorkCount,
                workLibraryVersion,
                activeWorksHash,
                null);
        return new Baseline(
                schemaVersion,
                profileCount,
                activeWorkCount,
                workLibraryVersion,
                activeWorksHash,
                computeBaselineHash(unsigned));
    }

    private Baseline validateBaseline(Baseline baseline) {
        Objects.requireNonNull(baseline, "baseline");
        if (baseline.schemaVersion() != 1) {
            throw new IllegalArgumentException("baseline schemaVersion must be 1");
        }
        if (baseline.profileCount() < 0
                || baseline.activeWorkCount() < 0
                || baseline.workLibraryVersion() < 0) {
            throw new IllegalArgumentException(
                    "baseline counts and workLibraryVersion must be non-negative");
        }
        if (baseline.activeWorksHash() == null
                || !baseline.activeWorksHash().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "activeWorksHash must contain 64 lowercase hexadecimal characters");
        }
        requireBaselineHashFormat(baseline.baselineHash());
        String recomputed = computeBaselineHash(baseline);
        if (!recomputed.equals(baseline.baselineHash())) {
            throw new IllegalStateException("baselineHash does not match canonical baseline payload");
        }
        return baseline;
    }

    private BaselineSnapshot validateSnapshot(BaselineSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Baseline baseline = validateBaseline(snapshot.baseline());
        List<Long> activeWorkIds = Objects.requireNonNull(
                snapshot.activeWorkIds(), "snapshot.activeWorkIds");
        if (baseline.activeWorkCount() != activeWorkIds.size()) {
            throw new IllegalArgumentException(
                    "snapshot activeWorkCount must equal activeWorkIds size");
        }
        long previous = 0L;
        for (Long activeWorkId : activeWorkIds) {
            if (activeWorkId == null || activeWorkId <= 0L) {
                throw new IllegalArgumentException("snapshot active work IDs must be positive");
            }
            if (activeWorkId <= previous) {
                throw new IllegalArgumentException(
                        "snapshot active work IDs must be strictly ascending and unique");
            }
            previous = activeWorkId;
        }
        return snapshot;
    }

    private void validateExpectedBaselineHash(
            String expectedBaselineHash, Baseline embeddedBaseline) {
        requireBaselineHashFormat(expectedBaselineHash);
        Baseline validated = validateBaseline(embeddedBaseline);
        if (!expectedBaselineHash.equals(validated.baselineHash())) {
            throw new IllegalStateException("expectedBaselineHash does not match the snapshot baseline");
        }
    }

    private void requireBaselineHashFormat(String baselineHash) {
        if (baselineHash == null || !baselineHash.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "baselineHash must use sha256:<64 lowercase hexadecimal characters>");
        }
    }

    private String computeBaselineHash(Baseline baseline) {
        TreeMap<String, Object> payload = new TreeMap<>();
        payload.put("schemaVersion", baseline.schemaVersion());
        payload.put("profileCount", baseline.profileCount());
        payload.put("activeWorkCount", baseline.activeWorkCount());
        payload.put("workLibraryVersion", baseline.workLibraryVersion());
        payload.put("activeWorksHash", baseline.activeWorksHash());
        try {
            byte[] canonicalJson = BASELINE_CANONICAL_MAPPER.writeValueAsBytes(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + java.util.HexFormat.of().formatHex(digest.digest(canonicalJson));
        } catch (Exception error) {
            throw new IllegalStateException("baseline canonical hash cannot be calculated", error);
        }
    }

    private String requireBatchId(String batchId) {
        if (batchId == null || !batchId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,31}")) {
            throw new IllegalArgumentException(
                    "batchId must contain 1-32 ASCII letters, digits, dot, underscore or hyphen");
        }
        return batchId;
    }

    private String batchPrefix(String batchId) {
        return "cp:" + batchId + ":";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private IllegalStateException databaseFailure(String mode, SQLException error) {
        return new IllegalStateException("career profile migration " + mode + " database failure", error);
    }

    private void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }

    public record InspectionReport(
            Set<Long> malformedExtendedFieldUserIds,
            long profileCount,
            DatabaseCounts counts) {
    }

    public record DatabaseCounts(long mediaAssetCount, long relationCount, long activeWorkCount) {
    }

    public record DryRunReport(
            boolean profileFound,
            int plannedCreates,
            int plannedSkips,
            DatabaseCounts beforeCounts,
            DatabaseCounts afterCounts) {
    }

    public record Baseline(
            int schemaVersion,
            long profileCount,
            long activeWorkCount,
            long workLibraryVersion,
            String activeWorksHash,
            String baselineHash) {
    }

    public record BaselineSnapshot(Baseline baseline, List<Long> activeWorkIds) {
        public BaselineSnapshot {
            activeWorkIds = List.copyOf(activeWorkIds);
        }
    }

    public record ApplyReport(
            String batchId,
            int createdCount,
            int skippedCount,
            List<Long> createdExperienceIds) {
        public ApplyReport {
            createdExperienceIds = List.copyOf(createdExperienceIds);
        }
    }

    public record VerificationReport(
            boolean passed,
            long activeWorkCount,
            long distinctDedupeKeyCount,
            long migrationSourceCount,
            Map<String, Long> categoryCounts,
            Set<String> mismatchedFixtureIds) {
    }

    public record RestoreReport(
            String batchId, int removedCount, long restoredWorkLibraryVersion) {
    }

    public record RestoreVerificationReport(
            boolean passed,
            Baseline expected,
            Baseline actual,
            List<Long> expectedActiveWorkIds,
            List<Long> actualActiveWorkIds) {
    }

    private record GoldenDataset(
            int schemaVersion, Map<String, Long> categoryCounts, List<GoldenWork> works) {
    }

    private record GoldenWork(
            String fixtureId,
            String projectName,
            String publishStatus,
            String workTypeCode,
            String roleLevelCode,
            String roleName,
            String platform,
            String syncSoundStatus,
            List<String> collaborators,
            String achievementText) {
        private GoldenWork {
            collaborators = collaborators == null ? List.of() : List.copyOf(collaborators);
        }
    }

    private record ProfileState(long profileId, long workLibraryVersion) {
    }

    private record WorkIdentity(long experienceId, String dedupeKey) {
    }

    private record BatchWorkIdentity(long experienceId, String dedupeKey, String rid) {
    }

    private record CurrentWork(
            long experienceId,
            String projectName,
            String roleName,
            String publishStatus,
            String workTypeCode,
            String roleLevelCode,
            String platform,
            String syncSoundStatus,
            List<String> collaborators,
            String achievementText,
            String dedupeKey,
            String sourceType) {
    }
}
