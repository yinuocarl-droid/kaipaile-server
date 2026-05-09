package com.kaipai;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Idempotent JDBC seeder for admin smoke-detail verification on the dev database.
 */
public class AdminSmokeDataSeeder {

    private static final String DB_URL = "jdbc:mysql://101.43.57.62:3306/kaipai_dev?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";
    private static final String DB_USERNAME = "root";
    private static final String DB_PASSWORD = "root123456";

    private static final long USER_ID_10000 = 10000L;
    private static final long USER_ID_10001 = 10001L;
    private static final long ADMIN_USER_ID = 1L;

    private static final String INVITE_CODE = "SMK100";
    private static final String POLICY_NAME = "SMOKE_POLICY";
    private static final String GRANT_CODE = "SMOKE_GRANT_10000";
    private static final String PRODUCT_CODE = "SMOKE_PLUS_30D";
    private static final String ORDER_NO = "SMOKE_PAY_10000";
    private static final String CHANNEL_TRADE_NO = "SMOKE_TX_10000";
    private static final String REFUND_NO = "SMOKE_REFUND_10000";
    private static final String TEMPLATE_CODE = "SMOKE_TEMPLATE";
    private static final String PUBLISH_VERSION = "SMOKE_V1";
    private static final String ID_CARD_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    public static void main(String[] args) throws Exception {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD)) {
            connection.setAutoCommit(false);
            try {
                ensureUser(connection, USER_ID_10000);
                ensureUser(connection, USER_ID_10001);

                updateUsers(connection);

                long policyId = upsertReferralPolicy(connection);
                long inviteCodeId = upsertInviteCode(connection);
                long referralId = upsertReferralRecord(connection, inviteCodeId);
                long productId = upsertCapabilityProduct(connection);
                long paymentOrderId = upsertPaymentOrder(connection, productId);
                long transactionId = upsertPaymentTransaction(connection, paymentOrderId);
                long refundOrderId = upsertRefundOrder(connection, paymentOrderId);
                upsertRefundOperateLog(connection, refundOrderId);
                long grantId = upsertEntitlementGrant(connection, policyId);
                upsertCapabilityAccount(connection, paymentOrderId);
                upsertCapabilityChangeLog(connection, paymentOrderId);
                long verificationId = upsertIdentityVerification(connection);
                long templateId = upsertCardSceneTemplate(connection);
                upsertTemplatePublishLog(connection, templateId);

                connection.commit();

                Map<String, Long> ids = new LinkedHashMap<>();
                ids.put("verifyId", verificationId);
                ids.put("referralId", referralId);
                ids.put("policyId", policyId);
                ids.put("grantId", grantId);
                ids.put("productId", productId);
                ids.put("capabilityUserId", USER_ID_10000);
                ids.put("paymentOrderId", paymentOrderId);
                ids.put("transactionId", transactionId);
                ids.put("refundOrderId", refundOrderId);
                ids.put("templateId", templateId);
                ids.forEach((key, value) -> System.out.println(key + "=" + value));
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    private static void ensureUser(Connection connection, long userId) throws SQLException {
        Long existingUserId = queryForLong(connection, "SELECT user_id FROM `user` WHERE user_id = ?", userId);
        if (existingUserId == null) {
            throw new IllegalStateException("Required smoke user not found: " + userId);
        }
    }

    private static void updateUsers(Connection connection) throws SQLException {
        executeUpdate(connection, """
                UPDATE `user`
                   SET real_auth_status = 2,
                       valid_invite_count = 1,
                       update_user_name = 'smoke-seeder'
                 WHERE user_id = ?
                """, USER_ID_10000);

        executeUpdate(connection, """
                UPDATE `user`
                   SET invited_by_user_id = ?,
                       register_device_fingerprint = ?,
                       update_user_name = 'smoke-seeder'
                 WHERE user_id = ?
                """, USER_ID_10000, "smoke-device-10001", USER_ID_10001);
    }

    private static long upsertIdentityVerification(Connection connection) throws SQLException {
        Long verificationId = queryForLong(connection,
                "SELECT verification_id FROM identity_verification WHERE user_id = ? ORDER BY verification_id DESC LIMIT 1",
                USER_ID_10000);
        if (verificationId == null) {
            executeInsert(connection, """
                    INSERT INTO identity_verification
                        (user_id, real_name, id_card_no_cipher, id_card_hash, status, reviewer_id, reviewed_at,
                         snapshot_profile_completion, create_user_name, update_user_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    USER_ID_10000, "Smoke User", "ENC_SMOKE_10000", ID_CARD_HASH, 2, ADMIN_USER_ID,
                    timestamp(LocalDateTime.of(2026, 3, 31, 10, 0)), 100, "smoke-seeder", "smoke-seeder");
        } else {
            executeUpdate(connection, """
                    UPDATE identity_verification
                       SET real_name = ?,
                           id_card_no_cipher = ?,
                           id_card_hash = ?,
                           status = ?,
                           reject_reason = NULL,
                           reviewer_id = ?,
                           reviewed_at = ?,
                           snapshot_profile_completion = ?,
                           deleted = 0,
                           update_user_name = ?
                     WHERE verification_id = ?
                    """,
                    "Smoke User", "ENC_SMOKE_10000", ID_CARD_HASH, 2, ADMIN_USER_ID,
                    timestamp(LocalDateTime.of(2026, 3, 31, 10, 0)), 100, "smoke-seeder", verificationId);
        }
        return requireId(connection, "SELECT verification_id FROM identity_verification WHERE id_card_hash = ?", ID_CARD_HASH);
    }

    private static long upsertInviteCode(Connection connection) throws SQLException {
        Long inviteCodeId = queryForLong(connection, "SELECT invite_code_id FROM invite_code WHERE user_id = ?", USER_ID_10000);
        if (inviteCodeId == null) {
            executeInsert(connection, """
                    INSERT INTO invite_code (user_id, code, status, create_user_name, update_user_name)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    USER_ID_10000, INVITE_CODE, 1, "smoke-seeder", "smoke-seeder");
        } else {
            executeUpdate(connection, """
                    UPDATE invite_code
                       SET code = ?,
                           status = ?,
                           deleted = 0,
                           update_user_name = ?
                     WHERE invite_code_id = ?
                    """,
                    INVITE_CODE, 1, "smoke-seeder", inviteCodeId);
        }
        return requireId(connection, "SELECT invite_code_id FROM invite_code WHERE user_id = ?", USER_ID_10000);
    }

    private static long upsertReferralRecord(Connection connection, long inviteCodeId) throws SQLException {
        Long referralId = queryForLong(connection,
                "SELECT referral_id FROM referral_record WHERE invitee_user_id = ? LIMIT 1",
                USER_ID_10001);
        if (referralId == null) {
            executeInsert(connection, """
                    INSERT INTO referral_record
                        (inviter_user_id, invitee_user_id, invite_code_id, invite_code_snapshot, register_device_fingerprint,
                         status, risk_flag, risk_reason, registered_at, validated_at, create_user_name, update_user_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    USER_ID_10000, USER_ID_10001, inviteCodeId, INVITE_CODE, "smoke-device-10001",
                    1, 0, null, timestamp(LocalDateTime.of(2026, 3, 31, 9, 30)),
                    timestamp(LocalDateTime.of(2026, 3, 31, 10, 30)), "smoke-seeder", "smoke-seeder");
        } else {
            executeUpdate(connection, """
                    UPDATE referral_record
                       SET inviter_user_id = ?,
                           invite_code_id = ?,
                           invite_code_snapshot = ?,
                           register_device_fingerprint = ?,
                           status = ?,
                           risk_flag = ?,
                           risk_reason = NULL,
                           registered_at = ?,
                           validated_at = ?,
                           deleted = 0,
                           update_user_name = ?
                     WHERE referral_id = ?
                    """,
                    USER_ID_10000, inviteCodeId, INVITE_CODE, "smoke-device-10001", 1, 0,
                    timestamp(LocalDateTime.of(2026, 3, 31, 9, 30)),
                    timestamp(LocalDateTime.of(2026, 3, 31, 10, 30)), "smoke-seeder", referralId);
        }
        return requireId(connection, "SELECT referral_id FROM referral_record WHERE invitee_user_id = ?", USER_ID_10001);
    }

    private static long upsertReferralPolicy(Connection connection) throws SQLException {
        Long policyId = queryForLong(connection,
                "SELECT policy_id FROM referral_policy WHERE policy_name = ? ORDER BY policy_id DESC LIMIT 1",
                POLICY_NAME);
        String grantRuleJson = "{\"grantType\":\"invite_eligibility\",\"grantCode\":\"SMOKE_GRANT_10000\",\"durationDays\":30}";
        if (policyId == null) {
            executeInsert(connection, """
                    INSERT INTO referral_policy
                        (policy_name, enabled, require_real_auth, require_profile_completion, profile_completion_threshold,
                         same_device_limit, hourly_invite_limit, auto_grant_enabled, grant_rule_json, create_user_name, update_user_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    POLICY_NAME, 1, 1, 1, 50, 1, 5, 1, grantRuleJson, "smoke-seeder", "smoke-seeder");
        } else {
            executeUpdate(connection, """
                    UPDATE referral_policy
                       SET enabled = ?,
                           require_real_auth = ?,
                           require_profile_completion = ?,
                           profile_completion_threshold = ?,
                           same_device_limit = ?,
                           hourly_invite_limit = ?,
                           auto_grant_enabled = ?,
                           grant_rule_json = ?,
                           deleted = 0,
                           update_user_name = ?
                     WHERE policy_id = ?
                    """,
                    1, 1, 1, 50, 1, 5, 1, grantRuleJson, "smoke-seeder", policyId);
        }
        return requireId(connection,
                "SELECT policy_id FROM referral_policy WHERE policy_name = ? ORDER BY policy_id DESC LIMIT 1",
                POLICY_NAME);
    }

    private static long upsertEntitlementGrant(Connection connection, long policyId) throws SQLException {
        Long grantId = queryForLong(connection,
                "SELECT grant_id FROM user_entitlement_grant WHERE user_id = ? AND grant_code = ? LIMIT 1",
                USER_ID_10000, GRANT_CODE);
        if (grantId == null) {
            executeInsert(connection, """
                    INSERT INTO user_entitlement_grant
                        (user_id, grant_type, grant_code, status, effective_time, expire_time, source_type, source_ref_id,
                         remark, create_user_name, update_user_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    USER_ID_10000, "invite_eligibility", GRANT_CODE, 1,
                    timestamp(LocalDateTime.of(2026, 3, 31, 11, 0)),
                    timestamp(LocalDateTime.of(2026, 4, 30, 23, 59)),
                    "policy", policyId, "smoke eligibility grant", "smoke-seeder", "smoke-seeder");
        } else {
            executeUpdate(connection, """
                    UPDATE user_entitlement_grant
                       SET grant_type = ?,
                           status = ?,
                           effective_time = ?,
                           expire_time = ?,
                           source_type = ?,
                           source_ref_id = ?,
                           remark = ?,
                           deleted = 0,
                           update_user_name = ?
                     WHERE grant_id = ?
                    """,
                    "invite_eligibility", 1, timestamp(LocalDateTime.of(2026, 3, 31, 11, 0)),
                    timestamp(LocalDateTime.of(2026, 4, 30, 23, 59)), "policy", policyId,
                    "smoke eligibility grant", "smoke-seeder", grantId);
        }
        return requireId(connection,
                "SELECT grant_id FROM user_entitlement_grant WHERE user_id = ? AND grant_code = ? LIMIT 1",
                USER_ID_10000, GRANT_CODE);
    }

    private static long upsertCapabilityProduct(Connection connection) throws SQLException {
        Long productId = queryForLong(connection,
                "SELECT product_id FROM capability_product WHERE product_code = ? LIMIT 1",
                PRODUCT_CODE);
        String benefitConfigJson = "{\"benefitItems\":[{\"benefitCode\":\"priority_support\",\"benefitName\":\"Priority Support\",\"capabilitySummary\":\"admin smoke benefit\",\"status\":1,\"affectedPages\":[\"miniProgramCard\",\"inviteLanding\"],\"artifactTypes\":[\"poster\",\"miniProgramCard\"]}]}";
        if (productId == null) {
            executeInsert(connection, """
                    INSERT INTO capability_product
                        (product_code, product_name, capability_tier, duration_days, list_price, sale_price, status,
                         benefit_config_json, sort_no, create_user_name, update_user_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    PRODUCT_CODE, "Smoke Plus 30D", 1, 30, BigDecimal.valueOf(39.90), BigDecimal.valueOf(29.90),
                    1, benefitConfigJson, 10, "smoke-seeder", "smoke-seeder");
        } else {
            executeUpdate(connection, """
                    UPDATE capability_product
                       SET product_name = ?,
                           capability_tier = ?,
                           duration_days = ?,
                           list_price = ?,
                           sale_price = ?,
                           status = ?,
                           benefit_config_json = ?,
                           sort_no = ?,
                           deleted = 0,
                           update_user_name = ?
                     WHERE product_id = ?
                    """,
                    "Smoke Plus 30D", 1, 30, BigDecimal.valueOf(39.90), BigDecimal.valueOf(29.90),
                    1, benefitConfigJson, 10, "smoke-seeder", productId);
        }
        return requireId(connection,
                "SELECT product_id FROM capability_product WHERE product_code = ? LIMIT 1",
                PRODUCT_CODE);
    }

    private static long upsertCapabilityAccount(Connection connection, long paymentOrderId) throws SQLException {
        Long capabilityId = queryForLong(connection,
                "SELECT capability_id FROM capability_account WHERE user_id = ? LIMIT 1",
                USER_ID_10000);
        if (capabilityId == null) {
            executeInsert(connection, """
                    INSERT INTO capability_account
                        (user_id, tier, status, effective_time, expire_time, source_type, source_ref_id,
                         create_user_name, update_user_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    USER_ID_10000, 1, 1, timestamp(LocalDateTime.of(2026, 3, 31, 11, 0)),
                    timestamp(LocalDateTime.of(2026, 4, 30, 23, 59)), "payment", paymentOrderId,
                    "smoke-seeder", "smoke-seeder");
        } else {
            executeUpdate(connection, """
                    UPDATE capability_account
                       SET tier = ?,
                           status = ?,
                           effective_time = ?,
                           expire_time = ?,
                           source_type = ?,
                           source_ref_id = ?,
                           deleted = 0,
                           update_user_name = ?
                     WHERE capability_id = ?
                    """,
                    1, 1, timestamp(LocalDateTime.of(2026, 3, 31, 11, 0)),
                    timestamp(LocalDateTime.of(2026, 4, 30, 23, 59)), "payment", paymentOrderId,
                    "smoke-seeder", capabilityId);
        }
        return requireId(connection, "SELECT capability_id FROM capability_account WHERE user_id = ? LIMIT 1", USER_ID_10000);
    }

    private static void upsertCapabilityChangeLog(Connection connection, long paymentOrderId) throws SQLException {
        Long changeLogId = queryForLong(connection, """
                SELECT change_log_id
                  FROM capability_change_log
                 WHERE user_id = ?
                   AND source_type = ?
                   AND source_ref_id = ?
                 ORDER BY change_log_id DESC
                 LIMIT 1
                """, USER_ID_10000, "payment", paymentOrderId);
        if (changeLogId == null) {
            executeInsert(connection, """
                    INSERT INTO capability_change_log
                        (user_id, before_tier, after_tier, change_reason, source_type, source_ref_id,
                         effective_time, expire_time, remark, create_user_name, update_user_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    USER_ID_10000, 0, 1, "购买开通", "payment", paymentOrderId,
                    timestamp(LocalDateTime.of(2026, 3, 31, 11, 0)),
                    timestamp(LocalDateTime.of(2026, 4, 30, 23, 59)),
                    "smoke capability log", "smoke-seeder", "smoke-seeder");
        } else {
            executeUpdate(connection, """
                    UPDATE capability_change_log
                       SET before_tier = ?,
                           after_tier = ?,
                           change_reason = ?,
                           effective_time = ?,
                           expire_time = ?,
                           remark = ?,
                           deleted = 0,
                           update_user_name = ?
                     WHERE change_log_id = ?
                    """,
                    0, 1, "购买开通", timestamp(LocalDateTime.of(2026, 3, 31, 11, 0)),
                    timestamp(LocalDateTime.of(2026, 4, 30, 23, 59)),
                    "smoke capability log", "smoke-seeder", changeLogId);
        }
    }

    private static long upsertPaymentOrder(Connection connection, long productId) throws SQLException {
        Long paymentOrderId = queryForLong(connection,
                "SELECT payment_order_id FROM payment_order WHERE order_no = ? LIMIT 1",
                ORDER_NO);
        if (paymentOrderId == null) {
            executeInsert(connection, """
                    INSERT INTO payment_order
                        (order_no, user_id, biz_type, biz_ref_id, product_id, amount, currency_code, pay_status,
                         pay_channel, paid_at, create_user_name, update_user_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    ORDER_NO, USER_ID_10000, "capability_purchase", productId, productId,
                    BigDecimal.valueOf(29.90), "CNY", 1, "wxpay",
                    timestamp(LocalDateTime.of(2026, 3, 31, 11, 0)), "smoke-seeder", "smoke-seeder");
        } else {
            executeUpdate(connection, """
                    UPDATE payment_order
                       SET user_id = ?,
                           biz_type = ?,
                           biz_ref_id = ?,
                           product_id = ?,
                           amount = ?,
                           currency_code = ?,
                           pay_status = ?,
                           pay_channel = ?,
                           paid_at = ?,
                           closed_at = NULL,
                           deleted = 0,
                           update_user_name = ?
                     WHERE payment_order_id = ?
                    """,
                    USER_ID_10000, "capability_purchase", productId, productId,
                    BigDecimal.valueOf(29.90), "CNY", 1, "wxpay",
                    timestamp(LocalDateTime.of(2026, 3, 31, 11, 0)), "smoke-seeder", paymentOrderId);
        }
        return requireId(connection,
                "SELECT payment_order_id FROM payment_order WHERE order_no = ? LIMIT 1",
                ORDER_NO);
    }

    private static long upsertPaymentTransaction(Connection connection, long paymentOrderId) throws SQLException {
        Long transactionId = queryForLong(connection,
                "SELECT transaction_id FROM payment_transaction WHERE channel_trade_no = ? LIMIT 1",
                CHANNEL_TRADE_NO);
        String callbackPayload = "{\"result\":\"SUCCESS\",\"orderNo\":\"SMOKE_PAY_10000\",\"tradeState\":\"SUCCESS\"}";
        if (transactionId == null) {
            executeInsert(connection, """
                    INSERT INTO payment_transaction
                        (payment_order_id, channel_trade_no, channel, trade_type, amount, status,
                         callback_payload, callback_time, create_user_name, update_user_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    paymentOrderId, CHANNEL_TRADE_NO, "wxpay", "jsapi", BigDecimal.valueOf(29.90), 1,
                    callbackPayload, timestamp(LocalDateTime.of(2026, 3, 31, 11, 1)), "smoke-seeder", "smoke-seeder");
        } else {
            executeUpdate(connection, """
                    UPDATE payment_transaction
                       SET payment_order_id = ?,
                           channel = ?,
                           trade_type = ?,
                           amount = ?,
                           status = ?,
                           callback_payload = ?,
                           callback_time = ?,
                           deleted = 0,
                           update_user_name = ?
                     WHERE transaction_id = ?
                    """,
                    paymentOrderId, "wxpay", "jsapi", BigDecimal.valueOf(29.90), 1, callbackPayload,
                    timestamp(LocalDateTime.of(2026, 3, 31, 11, 1)), "smoke-seeder", transactionId);
        }
        return requireId(connection,
                "SELECT transaction_id FROM payment_transaction WHERE channel_trade_no = ? LIMIT 1",
                CHANNEL_TRADE_NO);
    }

    private static long upsertRefundOrder(Connection connection, long paymentOrderId) throws SQLException {
        Long refundOrderId = queryForLong(connection,
                "SELECT refund_order_id FROM refund_order WHERE refund_no = ? LIMIT 1",
                REFUND_NO);
        if (refundOrderId == null) {
            executeInsert(connection, """
                    INSERT INTO refund_order
                        (refund_no, payment_order_id, user_id, refund_amount, refund_reason, audit_status, refund_status,
                         audit_remark, auditor_id, audited_at, channel_refund_no, refunded_at, create_user_name, update_user_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    REFUND_NO, paymentOrderId, USER_ID_10000, BigDecimal.valueOf(29.90),
                    "smoke refund verification", 1, 2, "approved in smoke", ADMIN_USER_ID,
                    timestamp(LocalDateTime.of(2026, 3, 31, 12, 0)), "CHAN_REFUND_SMOKE_10000",
                    timestamp(LocalDateTime.of(2026, 3, 31, 12, 1)), "smoke-seeder", "smoke-seeder");
        } else {
            executeUpdate(connection, """
                    UPDATE refund_order
                       SET payment_order_id = ?,
                           user_id = ?,
                           refund_amount = ?,
                           refund_reason = ?,
                           audit_status = ?,
                           refund_status = ?,
                           audit_remark = ?,
                           auditor_id = ?,
                           audited_at = ?,
                           channel_refund_no = ?,
                           refunded_at = ?,
                           deleted = 0,
                           update_user_name = ?
                     WHERE refund_order_id = ?
                    """,
                    paymentOrderId, USER_ID_10000, BigDecimal.valueOf(29.90), "smoke refund verification",
                    1, 2, "approved in smoke", ADMIN_USER_ID,
                    timestamp(LocalDateTime.of(2026, 3, 31, 12, 0)), "CHAN_REFUND_SMOKE_10000",
                    timestamp(LocalDateTime.of(2026, 3, 31, 12, 1)), "smoke-seeder", refundOrderId);
        }
        return requireId(connection,
                "SELECT refund_order_id FROM refund_order WHERE refund_no = ? LIMIT 1",
                REFUND_NO);
    }

    private static void upsertRefundOperateLog(Connection connection, long refundOrderId) throws SQLException {
        Long logId = queryForLong(connection, """
                SELECT log_id
                  FROM refund_operate_log
                 WHERE refund_order_id = ?
                   AND action_type = ?
                 ORDER BY log_id DESC
                 LIMIT 1
                """, refundOrderId, "approve");
        if (logId == null) {
            executeInsert(connection, """
                    INSERT INTO refund_operate_log
                        (refund_order_id, operator_id, action_type, remark, create_user_name, update_user_name)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    refundOrderId, ADMIN_USER_ID, "approve", "smoke refund approve", "smoke-seeder", "smoke-seeder");
        } else {
            executeUpdate(connection, """
                    UPDATE refund_operate_log
                       SET operator_id = ?,
                           remark = ?,
                           deleted = 0,
                           update_user_name = ?
                     WHERE log_id = ?
                    """,
                    ADMIN_USER_ID, "smoke refund approve", "smoke-seeder", logId);
        }
    }

    private static long upsertCardSceneTemplate(Connection connection) throws SQLException {
        Long templateId = queryForLong(connection,
                "SELECT template_id FROM card_scene_template WHERE template_code = ? LIMIT 1",
                TEMPLATE_CODE);
        String themeJson = """
                {"themeColors":{"primary":"#D8B16A","accent":"#F4E8D2","background":"#112233","text":"#F8F1E8","heroText":"#FFFFFF"}}
                """;
        String artifactJson = """
                {"requiredInviteCount":1,"contentFocus":["镜头表现","角色适配"],"poster":{"enabled":true,"ratio":"3:4"},"miniProgramCard":{"enabled":true,"ratio":"1:1"},"pageConfig":{"layoutPreset":"magazine","surface":"paper","density":"balanced","heroStyle":"editorial","sections":{"profile":true,"stats":true,"timeline":true,"contactCta":true},"actions":{"primary":"contact","secondary":"share"}}}
                """;
        if (templateId == null) {
            executeInsert(connection, """
                    INSERT INTO card_scene_template
                        (template_code, TEMPLATE_SCENE_CODE, template_name, description, layout_variant, tier, required_level,
                         capability_required, base_theme_json, artifact_preset_json, status, sort_no, create_user_name, update_user_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    TEMPLATE_CODE, "classic", "Smoke Template", "template for admin detail smoke verification",
                    "magazine", "paid", 1, 1, themeJson, artifactJson, 1, 1, "smoke-seeder", "smoke-seeder");
        } else {
            executeUpdate(connection, """
                    UPDATE card_scene_template
                       SET TEMPLATE_SCENE_CODE = ?,
                           template_name = ?,
                           description = ?,
                           layout_variant = ?,
                           tier = ?,
                           required_level = ?,
                           capability_required = ?,
                           base_theme_json = ?,
                           artifact_preset_json = ?,
                           status = ?,
                           sort_no = ?,
                           deleted = 0,
                           update_user_name = ?
                     WHERE template_id = ?
                    """,
                    "classic", "Smoke Template", "template for admin detail smoke verification",
                    "magazine", "paid", 1, 1, themeJson, artifactJson, 1, 1, "smoke-seeder", templateId);
        }
        return requireId(connection,
                "SELECT template_id FROM card_scene_template WHERE template_code = ? LIMIT 1",
                TEMPLATE_CODE);
    }

    private static void upsertTemplatePublishLog(Connection connection, long templateId) throws SQLException {
        Long publishLogId = queryForLong(connection, """
                SELECT publish_log_id
                  FROM template_publish_log
                 WHERE template_id = ?
                   AND publish_version = ?
                   AND action_type = ?
                 ORDER BY publish_log_id DESC
                 LIMIT 1
                """, templateId, PUBLISH_VERSION, "publish");
        String snapshotJson = "{\"templateCode\":\"SMOKE_TEMPLATE\",\"templateSceneCode\":\"classic\",\"status\":1}";
        String diffSummaryJson = "{\"changed\":[\"baseThemeJson\",\"artifactPresetJson\"],\"note\":\"smoke publish\"}";
        if (publishLogId == null) {
            executeInsert(connection, """
                    INSERT INTO template_publish_log
                        (template_id, target_type, target_code, publish_version, draft_version, source_version, target_version,
                         action_type, published_by, publish_note, diff_summary_json, snapshot_json, published_at,
                         create_user_name, update_user_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    templateId, "template", TEMPLATE_CODE, PUBLISH_VERSION, "draft-smoke", "draft-smoke", PUBLISH_VERSION,
                    "publish", ADMIN_USER_ID, "smoke publish log", diffSummaryJson, snapshotJson,
                    timestamp(LocalDateTime.of(2026, 3, 31, 13, 0)), "smoke-seeder", "smoke-seeder");
        } else {
            executeUpdate(connection, """
                    UPDATE template_publish_log
                       SET target_type = ?,
                           target_code = ?,
                           draft_version = ?,
                           source_version = ?,
                           target_version = ?,
                           published_by = ?,
                           publish_note = ?,
                           diff_summary_json = ?,
                           snapshot_json = ?,
                           published_at = ?,
                           deleted = 0,
                           update_user_name = ?
                     WHERE publish_log_id = ?
                    """,
                    "template", TEMPLATE_CODE, "draft-smoke", "draft-smoke", PUBLISH_VERSION, ADMIN_USER_ID,
                    "smoke publish log", diffSummaryJson, snapshotJson, timestamp(LocalDateTime.of(2026, 3, 31, 13, 0)),
                    "smoke-seeder", publishLogId);
        }
    }

    private static long requireId(Connection connection, String sql, Object... parameters) throws SQLException {
        Long value = queryForLong(connection, sql, parameters);
        if (value == null) {
            throw new IllegalStateException("Expected id but query returned null: " + sql);
        }
        return value;
    }

    private static Long queryForLong(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                long value = resultSet.getLong(1);
                return resultSet.wasNull() ? null : value;
            }
        }
    }

    private static void executeInsert(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, parameters);
            statement.executeUpdate();
        }
    }

    private static void executeUpdate(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int i = 0; i < parameters.length; i++) {
            Object parameter = parameters[i];
            int index = i + 1;
            if (parameter instanceof Timestamp timestamp) {
                statement.setTimestamp(index, timestamp);
            } else if (parameter instanceof BigDecimal decimal) {
                statement.setBigDecimal(index, decimal);
            } else if (parameter instanceof Long value) {
                statement.setLong(index, value);
            } else if (parameter instanceof Integer value) {
                statement.setInt(index, value);
            } else if (parameter == null) {
                statement.setObject(index, null);
            } else {
                statement.setObject(index, parameter);
            }
        }
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return Timestamp.valueOf(value);
    }
}

