use serde::{Deserialize, Serialize};
use serde_json::Value;
use sha2::{Digest, Sha256};
use std::{
    fs::{self, OpenOptions},
    io::Write,
    path::{Path, PathBuf},
    sync::{
        atomic::{AtomicU64, Ordering},
        Mutex, MutexGuard,
    },
    time::{SystemTime, UNIX_EPOCH},
};
use tauri::{AppHandle, Manager};

const CACHE_DIRECTORY: &str = "tmdb-text-cache";
// Conserva varios días de datos de Home y de navegación. 750 entradas se
// alcanzaban con un catálogo amplio y expulsaban metadatos todavía vigentes.
const MAX_CACHE_ENTRIES: usize = 3_000;
const MAX_CACHE_BYTES: u64 = 256 * 1024 * 1024;
const MAX_ENTRY_BYTES: usize = 2 * 1024 * 1024;

static CACHE_FILESYSTEM_LOCK: Mutex<()> = Mutex::new(());
static TEMP_FILE_COUNTER: AtomicU64 = AtomicU64::new(0);

#[derive(Clone, Debug, Deserialize, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TmdbTextCacheEntry {
    pub expires_at: u64,
    pub data: Value,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TmdbTextCacheWrite {
    pub key: String,
    pub expires_at: u64,
    pub data: Value,
}

#[tauri::command]
pub fn tmdb_text_cache_get(
    app: AppHandle,
    key: String,
) -> Result<Option<TmdbTextCacheEntry>, String> {
    if key.is_empty() || key.len() > 8_192 || is_image_endpoint(&key) {
        return Ok(None);
    }

    let _guard = cache_lock();
    let directory = cache_directory(&app)?;
    read_entry(&directory, &key, now_ms())
}

#[tauri::command]
pub fn tmdb_text_cache_set_many(
    app: AppHandle,
    entries: Vec<TmdbTextCacheWrite>,
) -> Result<(), String> {
    if entries.is_empty() {
        return Ok(());
    }

    let _guard = cache_lock();
    let directory = cache_directory(&app)?;
    fs::create_dir_all(&directory)
        .map_err(|error| format!("No se pudo crear la caché TMDB: {error}"))?;

    let now = now_ms();
    for entry in entries {
        if entry.key.is_empty()
            || entry.key.len() > 8_192
            || is_image_endpoint(&entry.key)
            || entry.expires_at <= now
        {
            continue;
        }

        let cache_entry = TmdbTextCacheEntry {
            expires_at: entry.expires_at,
            data: entry.data,
        };
        let bytes = serde_json::to_vec(&cache_entry)
            .map_err(|error| format!("No se pudo serializar la caché TMDB: {error}"))?;
        // Las respuestas de detalle normales caben holgadamente. Una respuesta
        // excepcionalmente grande no debe consumir el disco de la app.
        if bytes.len() > MAX_ENTRY_BYTES {
            continue;
        }
        write_entry(&directory, &entry.key, &bytes)?;
    }

    prune_cache(&directory);
    Ok(())
}

fn cache_lock() -> MutexGuard<'static, ()> {
    CACHE_FILESYSTEM_LOCK
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
}

fn cache_directory(app: &AppHandle) -> Result<PathBuf, String> {
    app.path()
        .app_data_dir()
        .map(|path| path.join(CACHE_DIRECTORY))
        .map_err(|error| format!("No se pudo resolver AppData para la caché TMDB: {error}"))
}

fn cache_file_path(directory: &Path, key: &str) -> PathBuf {
    let digest = Sha256::digest(key.as_bytes());
    directory.join(format!("{}.json", hex::encode(digest)))
}

fn read_entry(directory: &Path, key: &str, now: u64) -> Result<Option<TmdbTextCacheEntry>, String> {
    let path = cache_file_path(directory, key);
    let bytes = match fs::read(&path) {
        Ok(bytes) => bytes,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(error) => return Err(format!("No se pudo leer la caché TMDB: {error}")),
    };

    if bytes.len() > MAX_ENTRY_BYTES {
        let _ = fs::remove_file(&path);
        return Ok(None);
    }

    let entry = match serde_json::from_slice::<TmdbTextCacheEntry>(&bytes) {
        Ok(entry) => entry,
        Err(_) => {
            let _ = fs::remove_file(&path);
            return Ok(None);
        }
    };
    if entry.expires_at <= now {
        let _ = fs::remove_file(&path);
        return Ok(None);
    }

    Ok(Some(entry))
}

fn write_entry(directory: &Path, key: &str, bytes: &[u8]) -> Result<(), String> {
    fs::create_dir_all(directory)
        .map_err(|error| format!("No se pudo crear la caché TMDB: {error}"))?;

    let path = cache_file_path(directory, key);
    let temp_id = TEMP_FILE_COUNTER.fetch_add(1, Ordering::Relaxed);
    let temp_path = directory.join(format!(
        ".{}.{}.{}.tmp",
        path.file_stem()
            .and_then(|stem| stem.to_str())
            .unwrap_or("entry"),
        std::process::id(),
        temp_id,
    ));

    let write_result = (|| -> std::io::Result<()> {
        let mut file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&temp_path)?;
        file.write_all(bytes)?;
        file.sync_all()?;
        match fs::rename(&temp_path, &path) {
            Ok(()) => Ok(()),
            Err(error) if path.exists() => {
                // Windows no sustituye siempre el destino en rename. El archivo
                // temporal ya está completo, así que se reemplaza el anterior.
                fs::remove_file(&path)?;
                fs::rename(&temp_path, &path).map_err(|_| error)
            }
            Err(error) => Err(error),
        }
    })();

    if let Err(error) = write_result {
        let _ = fs::remove_file(&temp_path);
        return Err(format!("No se pudo guardar la caché TMDB: {error}"));
    }
    Ok(())
}

fn prune_cache(directory: &Path) {
    let Ok(read_dir) = fs::read_dir(directory) else {
        return;
    };
    let mut files = read_dir
        .filter_map(Result::ok)
        .filter_map(|entry| {
            let path = entry.path();
            if path.extension().and_then(|ext| ext.to_str()) != Some("json") {
                return None;
            }
            let metadata = entry.metadata().ok()?;
            Some((
                path,
                metadata.len(),
                metadata.modified().unwrap_or(UNIX_EPOCH),
            ))
        })
        .collect::<Vec<_>>();

    let mut total_bytes = files.iter().map(|(_, bytes, _)| *bytes).sum::<u64>();
    if files.len() <= MAX_CACHE_ENTRIES && total_bytes <= MAX_CACHE_BYTES {
        return;
    }

    files.sort_by_key(|(_, _, modified)| *modified);
    while !files.is_empty() && (files.len() > MAX_CACHE_ENTRIES || total_bytes > MAX_CACHE_BYTES) {
        let (oldest, bytes, _) = files.remove(0);
        if fs::remove_file(oldest).is_ok() {
            total_bytes = total_bytes.saturating_sub(bytes);
        }
    }
}

fn is_image_endpoint(key: &str) -> bool {
    let path = key.split('?').next().unwrap_or(key).trim_end_matches('/');
    path.ends_with("/images") || path.starts_with("https://image.tmdb.org/")
}

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_millis().min(u64::MAX as u128) as u64)
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn temp_cache_dir() -> PathBuf {
        let path = std::env::temp_dir().join(format!(
            "aetherio-tmdb-cache-test-{}-{}",
            std::process::id(),
            TEMP_FILE_COUNTER.fetch_add(1, Ordering::Relaxed),
        ));
        fs::create_dir_all(&path).expect("create temporary cache dir");
        path
    }

    #[test]
    fn persists_text_response_and_expires_it() {
        let dir = temp_cache_dir();
        let key = "/tv/1399/season/1?language=es-ES";
        let entry = TmdbTextCacheEntry {
            expires_at: 2_000,
            data: serde_json::json!({ "name": "Breaking Bad", "overview": "Texto" }),
        };
        let bytes = serde_json::to_vec(&entry).expect("serialize cache entry");
        write_entry(&dir, key, &bytes).expect("write cache entry");

        assert_eq!(read_entry(&dir, key, 1_999).unwrap(), Some(entry));
        assert_eq!(read_entry(&dir, key, 2_000).unwrap(), None);
        let _ = fs::remove_dir_all(dir);
    }

    #[test]
    fn cache_filename_does_not_expose_request_or_api_key() {
        let path = cache_file_path(Path::new("cache"), "/movie/550?language=es&token=secret");
        let filename = path.file_name().unwrap().to_string_lossy();
        assert!(!filename.contains("secret"));
        assert_eq!(filename.len(), 69); // 64 hex + .json
    }

    #[test]
    fn image_endpoints_are_excluded() {
        assert!(is_image_endpoint("/movie/550/images?language=es-ES"));
        assert!(is_image_endpoint(
            "https://image.tmdb.org/t/p/w500/poster.jpg"
        ));
        assert!(!is_image_endpoint("/movie/550?append_to_response=credits"));
    }
}
