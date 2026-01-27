package com.scanium.app.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

internal object SettingsKeys {
    object General {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val APP_LANGUAGE_KEY = stringPreferencesKey("app_language")
        val ALLOW_CLOUD_CLASSIFICATION_KEY = booleanPreferencesKey("allow_cloud_classification")
        val SHARE_DIAGNOSTICS_KEY = booleanPreferencesKey("share_diagnostics")
        val USER_EDITION_KEY = stringPreferencesKey("user_edition")
        val AUTO_SAVE_ENABLED_KEY = booleanPreferencesKey("auto_save_enabled")
        val SAVE_DIRECTORY_URI_KEY = stringPreferencesKey("save_directory_uri")
        val EXPORT_FORMAT_KEY = stringPreferencesKey("export_format")
        val SOUNDS_ENABLED_KEY = booleanPreferencesKey("sounds_enabled")
        val SHOW_ITEM_INFO_CHIPS_KEY = booleanPreferencesKey("show_item_info_chips")
    }

    object Assistant {
        val ALLOW_ASSISTANT_KEY = booleanPreferencesKey("allow_assistant")
        val ALLOW_ASSISTANT_IMAGES_KEY = booleanPreferencesKey("allow_assistant_images")
        val ASSISTANT_LANGUAGE_KEY = stringPreferencesKey("assistant_language")
        val ASSISTANT_TONE_KEY = stringPreferencesKey("assistant_tone")
        val ASSISTANT_REGION_KEY = stringPreferencesKey("assistant_region")
        val ASSISTANT_UNITS_KEY = stringPreferencesKey("assistant_units")
        val ASSISTANT_VERBOSITY_KEY = stringPreferencesKey("assistant_verbosity")
    }

    object Voice {
        val VOICE_MODE_ENABLED_KEY = booleanPreferencesKey("voice_mode_enabled")
        val SPEAK_ANSWERS_KEY = booleanPreferencesKey("speak_answers_enabled")
        val AUTO_SEND_TRANSCRIPT_KEY = booleanPreferencesKey("auto_send_transcript")
        val VOICE_LANGUAGE_KEY = stringPreferencesKey("voice_language")
        val ASSISTANT_HAPTICS_KEY = booleanPreferencesKey("assistant_haptics_enabled")
    }

    object Developer {
        val DEVELOPER_MODE_KEY = booleanPreferencesKey("developer_mode")
        val DEV_ALLOW_SCREENSHOTS_KEY = booleanPreferencesKey("dev_allow_screenshots")
        val DEV_SHOW_FTUE_BOUNDS_KEY = booleanPreferencesKey("dev_show_ftue_bounds")
        val DEV_SHOW_CAMERA_UI_FTUE_BOUNDS_KEY = booleanPreferencesKey("dev_show_camera_ui_ftue_bounds")
        val DEV_BARCODE_DETECTION_ENABLED_KEY = booleanPreferencesKey("dev_barcode_detection_enabled")
        val DEV_DOCUMENT_DETECTION_ENABLED_KEY = booleanPreferencesKey("dev_document_detection_enabled")
        val DEV_ADAPTIVE_THROTTLING_ENABLED_KEY = booleanPreferencesKey("dev_adaptive_throttling_enabled")
        val DEV_SCANNING_DIAGNOSTICS_ENABLED_KEY = booleanPreferencesKey("dev_scanning_diagnostics_enabled")
        val DEV_ROI_DIAGNOSTICS_ENABLED_KEY = booleanPreferencesKey("dev_roi_diagnostics_enabled")
        val DEV_BBOX_MAPPING_DEBUG_KEY = booleanPreferencesKey("dev_bbox_mapping_debug_enabled")
        val DEV_CORRELATION_DEBUG_KEY = booleanPreferencesKey("dev_correlation_debug_enabled")
        val DEV_CAMERA_PIPELINE_DEBUG_KEY = booleanPreferencesKey("dev_camera_pipeline_debug_enabled")
        val DEV_MOTION_OVERLAYS_ENABLED_KEY = booleanPreferencesKey("dev_motion_overlays_enabled")
        val DEV_OVERLAY_ACCURACY_STEP_KEY = intPreferencesKey("dev_overlay_accuracy_step")
        val DEV_SHOW_BUILD_WATERMARK_KEY = booleanPreferencesKey("dev_show_build_watermark")
        val DEV_FORCE_HYPOTHESIS_SHEET_KEY = booleanPreferencesKey("dev_force_hypothesis_sheet")
    }

    object Scanning {
        val SCANNING_GUIDANCE_ENABLED_KEY = booleanPreferencesKey("scanning_guidance_enabled")
        val SHOW_DETECTION_BOXES_KEY = booleanPreferencesKey("show_detection_boxes")
        val OPEN_ITEM_LIST_AFTER_SCAN_KEY = booleanPreferencesKey("open_item_list_after_scan")
        val SMART_MERGE_SUGGESTIONS_ENABLED_KEY = booleanPreferencesKey("smart_merge_suggestions_enabled")
    }

    object Unified {
        val SETTINGS_SCHEMA_VERSION_KEY = intPreferencesKey("settings_schema_version")
        const val CURRENT_SCHEMA_VERSION = 1
        val PRIMARY_REGION_COUNTRY_KEY = stringPreferencesKey("primary_region_country")
        val PRIMARY_LANGUAGE_KEY = stringPreferencesKey("primary_language")
        val APP_LANGUAGE_OVERRIDE_KEY = stringPreferencesKey("app_language_override")
        val AI_LANGUAGE_OVERRIDE_KEY = stringPreferencesKey("ai_language_override")
        val MARKETPLACE_COUNTRY_OVERRIDE_KEY = stringPreferencesKey("marketplace_country_override")
        val TTS_LANGUAGE_SETTING_KEY = stringPreferencesKey("tts_language_setting")
        val LAST_DETECTED_SPOKEN_LANGUAGE_KEY = stringPreferencesKey("last_detected_spoken_language")
    }
}
