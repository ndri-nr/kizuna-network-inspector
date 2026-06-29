use rusqlite::{params, Connection, Result};
use std::path::Path;

pub struct StorageEngine {
    pub conn: Connection,
}

impl StorageEngine {
    pub fn new(db_path: &str) -> Self {
        let path = Path::new(db_path);
        if let Some(parent) = path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }

        let conn = Connection::open(db_path).expect("Failed to open SQLite database");
        let engine = Self { conn };
        engine.initialize_db().expect("Failed to initialize SQLite schema");
        engine
    }

    fn initialize_db(&self) -> Result<()> {
        self.conn.execute("PRAGMA journal_mode = WAL;", [])?;
        self.conn.execute("PRAGMA synchronous = NORMAL;", [])?;
        self.conn.execute("PRAGMA foreign_keys = ON;", [])?;

        self.conn.execute(
            "CREATE TABLE IF NOT EXISTS capture_sessions (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                start_time INTEGER NOT NULL,
                end_time INTEGER
            );",
            [],
        )?;

        self.conn.execute(
            "CREATE TABLE IF NOT EXISTS http_exchanges (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                method TEXT NOT NULL,
                url TEXT NOT NULL,
                status_code INTEGER,
                timestamp INTEGER NOT NULL,
                duration INTEGER,
                request_headers TEXT,
                response_headers TEXT,
                request_body_path TEXT,
                response_body_path TEXT,
                FOREIGN KEY(session_id) REFERENCES capture_sessions(id) ON DELETE CASCADE
            );",
            [],
        )?;

        self.conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_exchanges_session_time ON http_exchanges(session_id, timestamp DESC);",
            [],
        )?;
        self.conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_exchanges_url ON http_exchanges(url);",
            [],
        )?;
        self.conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_exchanges_status ON http_exchanges(status_code);",
            [],
        )?;

        Ok(())
    }

    pub fn write_exchange(
        &self,
        id: &str,
        session_id: &str,
        method: &str,
        url: &str,
        status_code: Option<i32>,
        timestamp: i64,
        duration: Option<i64>,
        req_headers: &str,
        resp_headers: &str,
        req_body_path: &str,
        resp_body_path: &str,
    ) -> Result<()> {
        // Ensure session exists
        self.conn.execute(
            "INSERT OR IGNORE INTO capture_sessions (id, name, start_time) VALUES (?, ?, ?);",
            params![session_id, "Default Session", timestamp],
        )?;

        self.conn.execute(
            "INSERT OR REPLACE INTO http_exchanges (
                id, session_id, method, url, status_code, timestamp, duration,
                request_headers, response_headers, request_body_path, response_body_path
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);",
            params![
                id,
                session_id,
                method,
                url,
                status_code,
                timestamp,
                duration,
                req_headers,
                resp_headers,
                req_body_path,
                resp_body_path
            ],
        )?;

        Ok(())
    }
}
