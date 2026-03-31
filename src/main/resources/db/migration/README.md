# Database Migration Notes

- This project does not yet wire `Flyway` or `Liquibase` in `pom.xml`.
- The SQL files in this directory use Flyway-compatible naming so they can be executed manually now and adopted by Flyway later without renaming.
- Current execution strategy:
  - run the migration once against the target database in filename order
  - keep each later schema change as a new incremental file
  - do not edit an already executed migration in-place for shared environments
- Baseline migration for the new platform admin domains starts from:
  - [V20260331_001__platform_admin_baseline.sql](./V20260331_001__platform_admin_baseline.sql)
- Governance alignment added after baseline:
  - [V20260331_002__platform_admin_governance_alignment.sql](./V20260331_002__platform_admin_governance_alignment.sql)
