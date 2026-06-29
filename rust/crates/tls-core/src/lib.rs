pub struct TlsEngine {
    pub ca_bytes: Vec<u8>,
}

impl TlsEngine {
    pub fn new(ca_bytes: &[u8]) -> Self {
        Self {
            ca_bytes: ca_bytes.to_vec(),
        }
    }

    pub fn intercept_handshake(&mut self, _conn_id: u64, _sni: &str) -> Result<(), &'static str> {
        Ok(())
    }
}
