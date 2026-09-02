# Compare entity tables vs migration tables
$OutFile = 'D:\MOYUYOWPC\moyuyo-server\diff-result.txt'
Remove-Item $OutFile -ErrorAction SilentlyContinue

$entityTables = @(
'mo_ab_test','mo_achievement','mo_address','mo_admin_permission','mo_admin_role',
'mo_admin_user','mo_affiliate_account','mo_affiliate_commission','mo_after_sales',
'mo_app_version','mo_audit_log','mo_bargain','mo_bargain_help','mo_blacklist',
'mo_block','mo_browsing_history','mo_bundle_deal','mo_bundle_deal_item',
'mo_carrier','mo_cart','mo_category','mo_checkin_makeup','mo_clearance',
'mo_cms_content','mo_community_collect','mo_community_comment','mo_community_like',
'mo_community_post','mo_community_topic_v2','mo_complaint_process','mo_content_review',
'mo_coupon','mo_coupon_transfer_log','mo_crowdfunding','mo_crowdfunding_pledge',
'mo_cs_message','mo_cs_performance','mo_cs_session','mo_data_export_request',
'mo_exchange','mo_favorites','mo_favorites_group','mo_feedback','mo_festival_event',
'mo_finance_record','mo_flash_sale','mo_flash_sale_order','mo_follow',
'mo_gdpr_consent','mo_gdpr_policy','mo_gdpr_request','mo_gift_card',
'mo_gift_card_transaction','mo_group_buy','mo_group_buy_member','mo_growth_record',
'mo_help_article','mo_help_category','mo_inventory_batch','mo_inventory_check',
'mo_inventory_transfer','mo_invite','mo_invoice','mo_knowledge_base','mo_live_room',
'mo_live_room_product','mo_logistics','mo_lottery','mo_lottery_record',
'mo_marketing_campaign','mo_member','mo_member_prime','mo_member_task','mo_merge_package',
'mo_mission','mo_newuser_gift','mo_newuser_gift_claim','mo_notification',
'mo_operation_log','mo_order','mo_order_intercept','mo_order_item',
'mo_order_price_modify','mo_order_print_log','mo_order_tag','mo_order_tag_rel',
'mo_payment','mo_pet','mo_pet_achievement','mo_pet_album','mo_pet_diary',
'mo_pet_outfit','mo_pet_reminder','mo_pet_scene','mo_pet_weight','mo_points_exchange',
'mo_points_goods','mo_points_log','mo_price_strategy','mo_prime_plan','mo_product',
'mo_product_approval','mo_product_image','mo_product_qa','mo_product_review',
'mo_product_sku','mo_push_record','mo_refund','mo_risk_alert_config','mo_risk_event',
'mo_risk_rule','mo_satisfaction_survey','mo_search_log','mo_sensitive_word',
'mo_service_booking','mo_settlement','mo_shipping_strategy','mo_sms_code','mo_sms_record',
'mo_split_package','mo_subscribe_plan','mo_subscription_history','mo_system_config',
'mo_tariff_config','mo_ticket','mo_ticket_message','mo_user','mo_user_achievement',
'mo_user_behavior','mo_user_coupon','mo_user_device','mo_user_mission','mo_user_subscription',
'mo_visit_log','mo_wallet','mo_warehouse'
)

$migTables = @(
'mo_oauth_binding','mo_two_factor','mo_consent_log','mo_data_export_request',
'mo_account_deletion','mo_login_log','mo_device','mo_cms_content','mo_app_version',
'mo_audit_log','mo_knowledge_base','mo_satisfaction_survey','mo_risk_rule',
'mo_risk_event','mo_gdpr_consent','mo_gdpr_request','mo_price_strategy','mo_sms_record',
'mo_marketing_campaign','mo_ab_test','mo_user_behavior','mo_warehouse','mo_carrier',
'mo_clearance','mo_shipping_strategy','mo_merge_package','mo_split_package',
'mo_cs_performance','mo_system_config','mo_push_record','mo_complaint_process',
'mo_inventory_check','mo_gdpr_policy','mo_admin_role','mo_admin_permission',
'mo_admin_role_permission','mo_product_approval','mo_content_review','mo_risk_alert_config',
'mo_order_tag','mo_order_tag_rel','mo_cs_session','mo_inventory_transfer','mo_product_qa',
'mo_crowdfunding','mo_crowdfunding_pledge','mo_order_monitor_rule','mo_search_log',
'mo_visit_log','mo_notification','mo_feedback','mo_mission','mo_user_mission','mo_invite',
'mo_achievement','mo_user_achievement','mo_affiliate_account','mo_affiliate_commission',
'mo_block','mo_coupon_transfer_log','mo_festival_event','mo_follow','mo_help_article',
'mo_help_category','mo_newuser_gift','mo_newuser_gift_claim','mo_service_booking','mo_user_device',
'mo_browsing_history','mo_after_sales','mo_invoice','mo_pet_album','mo_pet_diary',
'mo_pet_weight','mo_pet_outfit','mo_gift_card','mo_gift_card_transaction','mo_bargain',
'mo_bargain_help','mo_group_buy','mo_group_buy_member','mo_bundle_deal',
'mo_bundle_deal_item','mo_live_room','mo_live_room_product','mo_subscribe_plan',
'mo_user_subscription','mo_subscription_history','mo_lottery','mo_lottery_record',
'mo_admin_user','mo_user','mo_ticket','mo_ticket_message','mo_inventory_batch',
'mo_sensitive_word','mo_flash_sale','mo_blacklist','mo_tariff_config','mo_settlement',
'mo_finance_record','mo_exchange','mo_order_price_modify','mo_order_intercept',
'mo_order_print_log','mo_minor_verification','mo_minor_consent_proof','mo_gdpr_quick_action',
'mo_admin_notification','mo_live_monitor_rule','mo_live_violation_alert',
'mo_split_package_rule_version','mo_split_package_rule','mo_user_behavior_event',
'mo_warehouse_allocation_suggest','mo_flash_sale_order','mo_category','mo_brand',
'mo_product','mo_product_sku','mo_product_image','mo_address','mo_cart','mo_order',
'mo_order_item','mo_payment','mo_refund','mo_logistics','mo_sync_log','mo_sync_checkpoint',
'mo_coupon','mo_user_coupon','mo_member','mo_member_wallet','mo_member_prime',
'mo_member_task','mo_marketing_activity','mo_invite_record','mo_return_request',
'mo_pet_health_calendar','mo_community_topic','mo_community_follow',
'mo_collection','mo_brand_ip','mo_product_ip','mo_growth_record','mo_pet_reminder',
'mo_pet_achievement','mo_pet_scene','mo_favorites_group','mo_favorites','mo_product_review',
'mo_operation_log','mo_wallet','mo_prime_plan','mo_points_goods','mo_points_exchange',
'mo_checkin_makeup','mo_sms_code','mo_community_topic_v2'
)

$missing = $entityTables | Where-Object { $migTables -notcontains $_ } | Sort-Object
Add-Content -Path $OutFile -Value ('Entity count = ' + $entityTables.Count)
Add-Content -Path $OutFile -Value ('Migration count = ' + $migTables.Count)
Add-Content -Path $OutFile -Value ''
Add-Content -Path $OutFile -Value ('=== MISSING (Entity declared but no migration CREATE) - ' + @($missing).Count + ' tables ===')
if (@($missing).Count -eq 0) {
    Add-Content -Path $OutFile -Value '  (none)'
} else {
    foreach ($t in $missing) { Add-Content -Path $OutFile -Value ('  MISS ' + $t) }
}

Write-Host 'done -> see diff-result.txt'
