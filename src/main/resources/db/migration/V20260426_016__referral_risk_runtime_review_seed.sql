SET @risk_referral_id := (
    SELECT referral_id
      FROM referral_record
     WHERE deleted = 0
       AND invitee_user_id = 10022
     ORDER BY referral_id DESC
     LIMIT 1
);

CREATE TABLE IF NOT EXISTS zz_bak_20260426_016_referral_risk_runtime_review_seed LIKE referral_record;

INSERT INTO zz_bak_20260426_016_referral_risk_runtime_review_seed
SELECT rr.*
  FROM referral_record rr
 WHERE rr.referral_id = @risk_referral_id
   AND NOT EXISTS (
       SELECT 1
         FROM zz_bak_20260426_016_referral_risk_runtime_review_seed bak
        WHERE bak.referral_id = rr.referral_id
   );

UPDATE referral_record
   SET status = 3,
       risk_flag = 1,
       risk_reason = 'runtime_review_device_risk',
       update_user_name = 'codex-strict-review',
       last_update = NOW()
 WHERE referral_id = @risk_referral_id;
