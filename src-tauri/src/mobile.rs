use crate::{secure_credentials, tmdb_text_cache};

#[tauri::command]
fn playback_capabilities() -> serde_json::Value {
    serde_json::json!({
        "mpvBundled": false,
        "backend": "none",
        "libmpv": false,
        "exoPlayer": false,
        "platform": "android",
        "formats": []
    })
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![
            playback_capabilities,
            tmdb_text_cache::tmdb_text_cache_get,
            tmdb_text_cache::tmdb_text_cache_set_many,
            secure_credentials::secure_credential_get,
            secure_credentials::secure_credential_set,
            secure_credentials::secure_credential_delete,
        ])
        .run(tauri::generate_context!())
        .expect("error while running Aetherio Mobile");
}
