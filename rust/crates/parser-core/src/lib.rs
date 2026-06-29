pub struct HttpEngine;

impl HttpEngine {
    pub fn new() -> Self {
        Self
    }

    pub fn parse_stream(&mut self, data: &[u8]) -> Vec<u8> {
        // Simple mock parse, returns CBOR array
        let mock_result = vec![format!("Parsed {} bytes", data.len())];
        serde_cbor::to_vec(&mock_result).unwrap_or_default()
    }
}
