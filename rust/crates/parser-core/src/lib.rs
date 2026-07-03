//! Real HTTP/1.x parsing over reassembled TCP byte streams.
//!
//! Phase 1 parses plaintext HTTP (port 80) request and response head + body
//! (Content-Length bodies; other framings are surfaced head-only). The parser is
//! tolerant: a partial buffer yields `None` so the caller can wait for more bytes.

use serde::{Deserialize, Serialize};

const MAX_HEADERS: usize = 64;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct HttpRequestParts {
    pub method: String,
    pub path: String,
    pub host: Option<String>,
    pub headers: Vec<(String, String)>,
    pub body: Vec<u8>,
    /// Total bytes consumed from the input buffer (head + body).
    pub consumed: usize,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct HttpResponseParts {
    pub status: u16,
    pub reason: String,
    pub headers: Vec<(String, String)>,
    pub body: Vec<u8>,
    pub consumed: usize,
}

fn header_value<'a>(headers: &'a [(String, String)], name: &str) -> Option<&'a str> {
    headers
        .iter()
        .find(|(k, _)| k.eq_ignore_ascii_case(name))
        .map(|(_, v)| v.as_str())
}

fn content_length(headers: &[(String, String)]) -> usize {
    header_value(headers, "content-length")
        .and_then(|v| v.trim().parse::<usize>().ok())
        .unwrap_or(0)
}

fn is_chunked(headers: &[(String, String)]) -> bool {
    header_value(headers, "transfer-encoding")
        .map(|v| v.to_ascii_lowercase().contains("chunked"))
        .unwrap_or(false)
}

fn collect_headers(src: &[httparse::Header]) -> Vec<(String, String)> {
    src.iter()
        .filter(|h| !h.name.is_empty())
        .map(|h| {
            (
                h.name.to_string(),
                String::from_utf8_lossy(h.value).to_string(),
            )
        })
        .collect()
}

/// Parse a single HTTP request from the front of `buf`. Returns `None` if the
/// head is incomplete or malformed.
pub fn parse_request(buf: &[u8]) -> Option<HttpRequestParts> {
    let mut headers = [httparse::EMPTY_HEADER; MAX_HEADERS];
    let mut req = httparse::Request::new(&mut headers);
    let head_len = match req.parse(buf) {
        Ok(httparse::Status::Complete(n)) => n,
        _ => return None,
    };

    let hdrs = collect_headers(req.headers);
    let host = header_value(&hdrs, "host").map(|h| h.to_string());
    let body_len = content_length(&hdrs).min(buf.len().saturating_sub(head_len));
    let body = buf[head_len..head_len + body_len].to_vec();

    Some(HttpRequestParts {
        method: req.method.unwrap_or("").to_string(),
        path: req.path.unwrap_or("").to_string(),
        host,
        headers: hdrs,
        body,
        consumed: head_len + body_len,
    })
}

/// Parse a single HTTP response from the front of `buf`.
pub fn parse_response(buf: &[u8]) -> Option<HttpResponseParts> {
    let mut headers = [httparse::EMPTY_HEADER; MAX_HEADERS];
    let mut resp = httparse::Response::new(&mut headers);
    let head_len = match resp.parse(buf) {
        Ok(httparse::Status::Complete(n)) => n,
        _ => return None,
    };

    let hdrs = collect_headers(resp.headers);
    let body_len = content_length(&hdrs).min(buf.len().saturating_sub(head_len));
    let body = buf[head_len..head_len + body_len].to_vec();

    Some(HttpResponseParts {
        status: resp.code.unwrap_or(0),
        reason: resp.reason.unwrap_or("").to_string(),
        headers: hdrs,
        body,
        consumed: head_len + body_len,
    })
}

/// Total byte length of a *complete* HTTP request message at the front of `buf`,
/// or `None` if the message is not yet fully present (or cannot be framed, e.g.
/// a chunked request body). Used to carve one exchange off a keep-alive stream.
pub fn request_len(buf: &[u8]) -> Option<usize> {
    let mut headers = [httparse::EMPTY_HEADER; MAX_HEADERS];
    let mut req = httparse::Request::new(&mut headers);
    let head_len = match req.parse(buf) {
        Ok(httparse::Status::Complete(n)) => n,
        _ => return None,
    };
    let hdrs = collect_headers(req.headers);
    if is_chunked(&hdrs) {
        return None; // chunked body: not framed here, defer to connection close.
    }
    let cl = content_length(&hdrs);
    let total = head_len + cl;
    if buf.len() >= total {
        Some(total)
    } else {
        None
    }
}

/// Total byte length of a *complete* HTTP response message at the front of `buf`,
/// or `None` if it is not fully present or is delimited by connection close
/// (no Content-Length and not chunked) / chunked — those are deferred to teardown.
pub fn response_len(buf: &[u8]) -> Option<usize> {
    let mut headers = [httparse::EMPTY_HEADER; MAX_HEADERS];
    let mut resp = httparse::Response::new(&mut headers);
    let head_len = match resp.parse(buf) {
        Ok(httparse::Status::Complete(n)) => n,
        _ => return None,
    };
    let status = resp.code.unwrap_or(0);
    // Bodyless responses (RFC 7230 §3.3.3): 1xx, 204, 304 carry no body.
    if (100..200).contains(&status) || status == 204 || status == 304 {
        return Some(head_len);
    }
    let hdrs = collect_headers(resp.headers);
    if is_chunked(&hdrs) {
        return None;
    }
    match header_value(&hdrs, "content-length") {
        Some(_) => {
            let total = head_len + content_length(&hdrs);
            if buf.len() >= total {
                Some(total)
            } else {
                None
            }
        }
        // No Content-Length and not chunked: body runs until the server closes.
        None => None,
    }
}

/// Extract the SNI host name from a TLS ClientHello record (TLS 1.2/1.3). Returns
/// `None` if `buf` is not a ClientHello or carries no SNI extension. Used for
/// HTTPS metadata in Phase 1 (no decryption).
pub fn parse_client_hello_sni(buf: &[u8]) -> Option<String> {
    // TLS record: content_type(0x16 handshake) | version(2) | length(2)
    if buf.len() < 5 || buf[0] != 0x16 {
        return None;
    }
    let mut p = 5usize; // into handshake
    // Handshake: type(1=client_hello) | length(3)
    if buf.len() < p + 4 || buf[p] != 0x01 {
        return None;
    }
    p += 4;
    // client_version(2) + random(32)
    p += 34;
    if p >= buf.len() {
        return None;
    }
    // session_id
    let sid_len = *buf.get(p)? as usize;
    p += 1 + sid_len;
    // cipher_suites
    let cs_len = u16::from_be_bytes([*buf.get(p)?, *buf.get(p + 1)?]) as usize;
    p += 2 + cs_len;
    // compression_methods
    let comp_len = *buf.get(p)? as usize;
    p += 1 + comp_len;
    // extensions
    if p + 2 > buf.len() {
        return None;
    }
    let ext_total = u16::from_be_bytes([buf[p], buf[p + 1]]) as usize;
    p += 2;
    let ext_end = (p + ext_total).min(buf.len());
    while p + 4 <= ext_end {
        let ext_type = u16::from_be_bytes([buf[p], buf[p + 1]]);
        let ext_len = u16::from_be_bytes([buf[p + 2], buf[p + 3]]) as usize;
        p += 4;
        if ext_type == 0x0000 {
            // server_name extension: list_len(2) | type(1) | name_len(2) | name
            let mut q = p + 2; // skip server_name_list length
            if q + 3 > buf.len() {
                return None;
            }
            let name_type = buf[q];
            let name_len = u16::from_be_bytes([buf[q + 1], buf[q + 2]]) as usize;
            q += 3;
            if name_type == 0 && q + name_len <= buf.len() {
                return String::from_utf8(buf[q..q + name_len].to_vec()).ok();
            }
            return None;
        }
        p += ext_len;
    }
    None
}

/// Kept so the existing `NativeHttpEngine` JNI symbol stays valid. Parses a
/// request head and returns a compact CBOR summary (method, path, host).
pub struct HttpEngine;

impl HttpEngine {
    pub fn new() -> Self {
        Self
    }

    pub fn parse_stream(&mut self, data: &[u8]) -> Vec<u8> {
        let summary = match parse_request(data) {
            Some(r) => (r.method, r.path, r.host.unwrap_or_default()),
            None => (String::new(), String::new(), String::new()),
        };
        serde_cbor::to_vec(&summary).unwrap_or_default()
    }
}

impl Default for HttpEngine {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_get_request_with_host() {
        let raw = b"GET /index.html HTTP/1.1\r\nHost: example.com\r\nAccept: */*\r\n\r\n";
        let r = parse_request(raw).unwrap();
        assert_eq!(r.method, "GET");
        assert_eq!(r.path, "/index.html");
        assert_eq!(r.host.as_deref(), Some("example.com"));
        assert_eq!(r.consumed, raw.len());
    }

    #[test]
    fn parses_post_body_by_content_length() {
        let raw = b"POST /submit HTTP/1.1\r\nHost: h\r\nContent-Length: 5\r\n\r\nhello";
        let r = parse_request(raw).unwrap();
        assert_eq!(r.method, "POST");
        assert_eq!(r.body, b"hello");
    }

    #[test]
    fn partial_request_returns_none() {
        let raw = b"GET / HTTP/1.1\r\nHost: exa";
        assert!(parse_request(raw).is_none());
    }

    #[test]
    fn parses_response_status() {
        let raw = b"HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nhi";
        let r = parse_response(raw).unwrap();
        assert_eq!(r.status, 200);
        assert_eq!(r.reason, "OK");
        assert_eq!(r.body, b"hi");
    }

    #[test]
    fn frames_complete_and_incomplete_messages() {
        let full = b"HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nhi";
        assert_eq!(response_len(full), Some(full.len()));
        // Body not fully arrived yet.
        let partial = b"HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhi";
        assert_eq!(response_len(partial), None);
        // 204 has no body.
        let no_body = b"HTTP/1.1 204 No Content\r\n\r\n";
        assert_eq!(response_len(no_body), Some(no_body.len()));
        // Body-until-close is not frameable mid-stream.
        let until_close = b"HTTP/1.1 200 OK\r\n\r\nsome bytes";
        assert_eq!(response_len(until_close), None);
        // Two pipelined responses: only the first is carved off.
        let mut two = full.to_vec();
        two.extend_from_slice(full);
        assert_eq!(response_len(&two), Some(full.len()));

        let req = b"GET / HTTP/1.1\r\nHost: h\r\n\r\n";
        assert_eq!(request_len(req), Some(req.len()));
        let req_post = b"POST /x HTTP/1.1\r\nContent-Length: 3\r\n\r\nabc";
        assert_eq!(request_len(req_post), Some(req_post.len()));
    }

    #[test]
    fn extracts_sni_from_client_hello() {
        // Minimal handcrafted ClientHello with SNI = "test.com".
        let host = b"test.com";
        let sni_name_len = host.len();
        let server_name_list_len = 3 + sni_name_len; // type(1)+len(2)+name
        let sne_body_len = 2 + server_name_list_len; // list_len(2) + entry
        let mut ext = Vec::new();
        ext.extend_from_slice(&[0x00, 0x00]); // ext type server_name
        ext.extend_from_slice(&(sne_body_len as u16).to_be_bytes());
        ext.extend_from_slice(&(server_name_list_len as u16).to_be_bytes());
        ext.push(0x00); // name type host_name
        ext.extend_from_slice(&(sni_name_len as u16).to_be_bytes());
        ext.extend_from_slice(host);

        let mut hs_body = Vec::new();
        hs_body.extend_from_slice(&[0x03, 0x03]); // client_version TLS1.2
        hs_body.extend_from_slice(&[0u8; 32]); // random
        hs_body.push(0x00); // session_id len
        hs_body.extend_from_slice(&[0x00, 0x02, 0x13, 0x01]); // cipher suites len + one suite
        hs_body.extend_from_slice(&[0x01, 0x00]); // compression methods len + null
        hs_body.extend_from_slice(&(ext.len() as u16).to_be_bytes());
        hs_body.extend_from_slice(&ext);

        let mut hs = Vec::new();
        hs.push(0x01); // client_hello
        let l = hs_body.len();
        hs.extend_from_slice(&[(l >> 16) as u8, (l >> 8) as u8, l as u8]);
        hs.extend_from_slice(&hs_body);

        let mut record = Vec::new();
        record.push(0x16); // handshake
        record.extend_from_slice(&[0x03, 0x01]); // record version
        record.extend_from_slice(&(hs.len() as u16).to_be_bytes());
        record.extend_from_slice(&hs);

        assert_eq!(parse_client_hello_sni(&record).as_deref(), Some("test.com"));
    }
}
