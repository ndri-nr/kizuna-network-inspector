use rusqlite::{params, Connection, Result};

pub struct SearchEngine {
    pub conn: Connection,
}

impl SearchEngine {
    pub fn new(db_path: &str) -> Self {
        let conn = Connection::open(db_path).expect("Failed to open database for searching");
        let engine = Self { conn };
        engine.initialize_fts().expect("Failed to initialize FTS index virtual tables");
        engine
    }

    fn initialize_fts(&self) -> Result<()> {
        // Create FTS5 virtual table
        self.conn.execute(
            "CREATE VIRTUAL TABLE IF NOT EXISTS http_exchanges_fts USING fts5(
                id UNINDEXED,
                method,
                url,
                request_headers,
                response_headers,
                tokenize = 'unicode61'
            );",
            [],
        )?;

        // Create triggers to sync FTS5 index automatically with storage-core tables
        self.conn.execute(
            "CREATE TRIGGER IF NOT EXISTS trg_exchanges_ai AFTER INSERT ON http_exchanges BEGIN
                INSERT INTO http_exchanges_fts(id, method, url, request_headers, response_headers)
                VALUES (new.id, new.method, new.url, new.request_headers, new.response_headers);
            END;",
            [],
        )?;

        self.conn.execute(
            "CREATE TRIGGER IF NOT EXISTS trg_exchanges_ad AFTER DELETE ON http_exchanges BEGIN
                DELETE FROM http_exchanges_fts WHERE id = old.id;
            END;",
            [],
        )?;

        Ok(())
    }

    pub fn query(&self, search_query: &str) -> Vec<u8> {
        let mut stmt = match self.conn.prepare(
            "SELECT id FROM http_exchanges_fts WHERE http_exchanges_fts MATCH ? ORDER BY rank;"
        ) {
            Ok(s) => s,
            Err(_) => return Vec::new(),
        };

        let mut rows = match stmt.query(params![search_query]) {
            Ok(r) => r,
            Err(_) => return Vec::new(),
        };

        let mut results = Vec::new();
        while let Ok(Some(row)) = rows.next() {
            if let Ok(id) = row.get::<_, String>(0) {
                results.push(id);
            }
        }

        // Return CBOR-encoded array of match IDs
        serde_cbor::to_vec(&results).unwrap_or_default()
    }
}
