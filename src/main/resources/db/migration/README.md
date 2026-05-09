# Database Migration Notes

- This project does not yet wire `Flyway` or `Liquibase` in `pom.xml`.
- The SQL files in this directory follow Flyway naming and are executed against the target database in filename order.
- Current execution strategy:
  - run the migration once against the target database in filename order
  - keep each later schema change as a new incremental file
  - do not edit an already executed migration in-place for shared environments
- Baseline migration for the new platform admin domains starts from:
  - [V20260331_001__platform_admin_baseline.sql](./V20260331_001__platform_admin_baseline.sql)
- Governance alignment added after baseline:
  - [V20260331_002__platform_admin_governance_alignment.sql](./V20260331_002__platform_admin_governance_alignment.sql)
- Recruit direct-permission alignment for current dev runtime:
  - [V20260422_008__admin_recruit_direct_permission_alignment.sql](./V20260422_008__admin_recruit_direct_permission_alignment.sql)
- Share-card runtime physical cleanup for current strict review:
  - [V20260425_010__share_card_template_scene_code_physical_replacement.sql](./V20260425_010__share_card_template_scene_code_physical_replacement.sql)
  - [V20260425_011__share_card_runtime_physical_cleanup.sql](./V20260425_011__share_card_runtime_physical_cleanup.sql)
- Required verification after applying the cleanup files:
  - `mvn -q -Dexec.classpathScope=test -Dexec.mainClass=com.kaipai.DbMigrationRunner -Dexec.args="inspect" org.codehaus.mojo:exec-maven-plugin:3.6.1:java`
  - The inspect command must report every share-card required column as `EXISTS` and every retired column as `ABSENT`.
