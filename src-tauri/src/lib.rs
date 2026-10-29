mod secure_credentials;
mod tmdb_text_cache;

// Keep desktop items at the crate root: EngineFS and its tests use crate::p2p.
#[cfg(not(target_os = "android"))]
include!("desktop.rs");

#[cfg(target_os = "android")]
mod mobile;

#[cfg(target_os = "android")]
pub use mobile::run;
