use rusqlite::{params, Connection, Result};
use serde::{Deserialize, Serialize};
use std::path::Path;

/// The canonical HTTP exchange record. This is the CBOR contract that crosses the
/// JNI boundary in both directions (capture writes it, the UI reads it) and the
/// row shape persisted in SQLite.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct HttpExchange {
    pub id: String,
    pub session_id: String,
    /// "http" or "https".
    pub scheme: String,
    pub host: String,
    pub method: String,
    pub url: String,
    pub status_code: Option<i32>,
    pub timestamp: i64,
    pub duration_ms: Option<i64>,
    pub req_size: i64,
    pub resp_size: i64,
    /// JSON-encoded header maps.
    pub request_headers: String,
    pub response_headers: String,
    pub request_body: String,
    pub response_body: String,
}

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
        engine
            .initialize_db()
            .expect("Failed to initialize SQLite schema");
        engine
    }

    fn initialize_db(&self) -> Result<()> {
        self.conn.execute_batch(
            "PRAGMA journal_mode = WAL;
             PRAGMA synchronous = NORMAL;
             PRAGMA foreign_keys = ON;",
        )?;

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
                scheme TEXT NOT NULL DEFAULT 'http',
                host TEXT NOT NULL DEFAULT '',
                method TEXT NOT NULL,
                url TEXT NOT NULL,
                status_code INTEGER,
                timestamp INTEGER NOT NULL,
                duration_ms INTEGER,
                req_size INTEGER NOT NULL DEFAULT 0,
                resp_size INTEGER NOT NULL DEFAULT 0,
                request_headers TEXT,
                response_headers TEXT,
                request_body TEXT,
                response_body TEXT,
                FOREIGN KEY(session_id) REFERENCES capture_sessions(id) ON DELETE CASCADE
            );",
            [],
        )?;

        self.conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_exchanges_time ON http_exchanges(timestamp DESC);",
            [],
        )?;
        self.conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_exchanges_host ON http_exchanges(host);",
            [],
        )?;

        Ok(())
    }

    /// Persist an exchange, backfilling its session row if needed.
    pub fn write_exchange(&self, ex: &HttpExchange) -> Result<()> {
        self.conn.execute(
            "INSERT OR IGNORE INTO capture_sessions (id, name, start_time) VALUES (?, ?, ?);",
            params![ex.session_id, "Default Session", ex.timestamp],
        )?;

        self.conn.execute(
            "INSERT OR REPLACE INTO http_exchanges (
                id, session_id, scheme, host, method, url, status_code, timestamp,
                duration_ms, req_size, resp_size, request_headers, response_headers,
                request_body, response_body
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);",
            params![
                ex.id,
                ex.session_id,
                ex.scheme,
                ex.host,
                ex.method,
                ex.url,
                ex.status_code,
                ex.timestamp,
                ex.duration_ms,
                ex.req_size,
                ex.resp_size,
                ex.request_headers,
                ex.response_headers,
                ex.request_body,
                ex.response_body,
            ],
        )?;

        Ok(())
    }

    fn row_to_exchange(row: &rusqlite::Row) -> Result<HttpExchange> {
        Ok(HttpExchange {
            id: row.get(0)?,
            session_id: row.get(1)?,
            scheme: row.get(2)?,
            host: row.get(3)?,
            method: row.get(4)?,
            url: row.get(5)?,
            status_code: row.get(6)?,
            timestamp: row.get(7)?,
            duration_ms: row.get(8)?,
            req_size: row.get(9)?,
            resp_size: row.get(10)?,
            request_headers: row.get::<_, Option<String>>(11)?.unwrap_or_default(),
            response_headers: row.get::<_, Option<String>>(12)?.unwrap_or_default(),
            request_body: row.get::<_, Option<String>>(13)?.unwrap_or_default(),
            response_body: row.get::<_, Option<String>>(14)?.unwrap_or_default(),
        })
    }

    const SELECT_COLS: &'static str = "id, session_id, scheme, host, method, url, status_code, \
        timestamp, duration_ms, req_size, resp_size, request_headers, response_headers, \
        request_body, response_body";

    /// All exchanges with `timestamp > since`, newest first. Used by the UI to
    /// drain freshly captured traffic on a poll tick.
    pub fn read_since(&self, since: i64) -> Result<Vec<HttpExchange>> {
        let sql = format!(
            "SELECT {} FROM http_exchanges WHERE timestamp > ? ORDER BY timestamp DESC LIMIT 1000;",
            Self::SELECT_COLS
        );
        let mut stmt = self.conn.prepare(&sql)?;
        let rows = stmt.query_map(params![since], Self::row_to_exchange)?;
        rows.collect()
    }

    pub fn read_by_id(&self, id: &str) -> Result<Option<HttpExchange>> {
        let sql = format!(
            "SELECT {} FROM http_exchanges WHERE id = ? LIMIT 1;",
            Self::SELECT_COLS
        );
        let mut stmt = self.conn.prepare(&sql)?;
        let mut rows = stmt.query_map(params![id], Self::row_to_exchange)?;
        match rows.next() {
            Some(r) => Ok(Some(r?)),
            None => Ok(None),
        }
    }

    pub fn count(&self) -> Result<i64> {
        self.conn
            .query_row("SELECT COUNT(*) FROM http_exchanges;", [], |r| r.get(0))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample(id: &str, ts: i64) -> HttpExchange {
        HttpExchange {
            id: id.to_string(),
            session_id: "s1".to_string(),
            scheme: "http".to_string(),
            host: "example.com".to_string(),
            method: "GET".to_string(),
            url: "http://example.com/".to_string(),
            status_code: Some(200),
            timestamp: ts,
            duration_ms: Some(12),
            req_size: 100,
            resp_size: 200,
            request_headers: "{}".to_string(),
            response_headers: "{}".to_string(),
            request_body: String::new(),
            response_body: "ok".to_string(),
        }
    }

    #[test]
    fn write_read_roundtrip_and_since_filter() {
        let dir = std::env::temp_dir().join("kni_storage_test");
        let _ = std::fs::remove_dir_all(&dir);
        let db = dir.join("test.db");
        let engine = StorageEngine::new(db.to_str().unwrap());

        engine.write_exchange(&sample("a", 100)).unwrap();
        engine.write_exchange(&sample("b", 200)).unwrap();

        let all = engine.read_since(0).unwrap();
        assert_eq!(all.len(), 2);
        assert_eq!(all[0].id, "b"); // newest first

        let newer = engine.read_since(100).unwrap();
        assert_eq!(newer.len(), 1);
        assert_eq!(newer[0].id, "b");

        let one = engine.read_by_id("a").unwrap().unwrap();
        assert_eq!(one.url, "http://example.com/");
        assert_eq!(one.resp_size, 200);
        assert_eq!(engine.count().unwrap(), 2);

        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn cbor_roundtrip() {
        let ex = sample("x", 1);
        let bytes = serde_cbor::to_vec(&ex).unwrap();
        let back: HttpExchange = serde_cbor::from_slice(&bytes).unwrap();
        assert_eq!(ex, back);
    }
}
