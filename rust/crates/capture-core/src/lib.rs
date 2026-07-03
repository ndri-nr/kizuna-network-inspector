//! Capture engine: the userspace network endpoint for the VPN tun.
//!
//! Phase 1 ("relay + plaintext"): a `smoltcp` interface terminates TCP against the
//! tun so connectivity is preserved, relays each connection to a `protect()`ed
//! upstream socket, and tees the byte streams into the parser so plaintext HTTP
//! (port 80) is captured in full and HTTPS (port 443) is captured as metadata
//! (SNI host) without decryption. DNS (UDP 53) is relayed so name resolution keeps
//! working. Completed exchanges are written to the shared SQLite store.
//!
//! Phase 2 will insert TLS termination (see `tls-core`) into the 443 path.

use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};

#[cfg(unix)]
use std::os::unix::io::RawFd;
#[cfg(not(unix))]
type RawFd = i32;

/// Called with a socket fd that must be excluded from the VPN tunnel before use.
/// Implemented by the JNI layer as `VpnService.protect(fd)`. Must be `Fn` (shared)
/// so the capture loop can hold it behind `&self`.
pub type ProtectFn = Box<dyn Fn(RawFd) -> bool + Send + Sync>;

// Fields are consumed by the unix capture loop (`imp::run`); on non-unix hosts
// `run` is a stub, so silence the otherwise-spurious dead-code warning there.
#[cfg_attr(not(unix), allow(dead_code))]
pub struct VpnEngine {
    tun_fd: RawFd,
    db_path: String,
    /// Directory holding the Root CA (cert + key) used to mint leaf certs for MITM.
    ca_dir: String,
    /// When true, HTTPS (443) connections are terminated and decrypted (MITM);
    /// when false they are relayed opaquely (metadata/SNI only). MITM presents a
    /// KNI-signed leaf, so apps that pin certificates or don't trust the user CA
    /// will fail to connect while it is on — hence it is opt-in and best paired
    /// with the app picker so only the app under test is routed through the VPN.
    decrypt: AtomicBool,
    protect: ProtectFn,
    stop: AtomicBool,
    paused: AtomicBool,
    packets: AtomicU64,
    exchanges: AtomicU64,
}

impl VpnEngine {
    pub fn new(tun_fd: RawFd, db_path: &str, ca_dir: &str, decrypt: bool, protect: ProtectFn) -> Self {
        Self {
            tun_fd,
            db_path: db_path.to_string(),
            ca_dir: ca_dir.to_string(),
            decrypt: AtomicBool::new(decrypt),
            protect,
            stop: AtomicBool::new(false),
            paused: AtomicBool::new(false),
            packets: AtomicU64::new(0),
            exchanges: AtomicU64::new(0),
        }
    }

    /// Toggle HTTPS decryption (MITM) at runtime.
    pub fn set_decrypt(&self, decrypt: bool) {
        self.decrypt.store(decrypt, Ordering::SeqCst);
    }

    /// Signal the capture loop to exit. Safe to call from another thread while
    /// `run` is executing (both take `&self`; all shared state is atomic).
    pub fn stop(&self) {
        self.stop.store(true, Ordering::SeqCst);
    }

    /// While paused the relay keeps forwarding traffic (connectivity is
    /// preserved) but completed exchanges are not recorded.
    pub fn set_paused(&self, paused: bool) {
        self.paused.store(paused, Ordering::SeqCst);
    }

    /// (packets_processed, exchanges_recorded)
    pub fn stats(&self) -> (u64, u64) {
        (
            self.packets.load(Ordering::Relaxed),
            self.exchanges.load(Ordering::Relaxed),
        )
    }
}

#[cfg(not(unix))]
impl VpnEngine {
    /// The capture loop requires unix socket/fd APIs (Android). On other hosts it
    /// is a no-op so the workspace still builds for tooling/tests.
    pub fn run(&self) -> i32 {
        -1
    }
}

#[cfg(unix)]
mod imp {
    use super::*;
    use parser_core::{
        parse_client_hello_sni, parse_request, parse_response, request_len, response_len,
    };
    use rustls::{ClientConnection, ServerConnection, ServerName};
    use smoltcp::iface::{Config, Interface, SocketHandle, SocketSet};
    use smoltcp::phy::{Device, DeviceCapabilities, Medium, RxToken, TxToken};
    use smoltcp::socket::{tcp, udp};
    use smoltcp::time::Instant as SmolInstant;
    use smoltcp::wire::{HardwareAddress, IpAddress, IpCidr, IpEndpoint};
    use socket2::{Domain, Protocol, SockAddr, Socket as Sock2, Type};
    use std::collections::VecDeque;
    use std::io::{Read, Write};
    use std::net::{IpAddr, Ipv4Addr, Ipv6Addr, SocketAddr, TcpStream, UdpSocket};
    use std::os::unix::io::{AsRawFd, RawFd};
    use std::sync::Arc;
    use std::time::{Instant, SystemTime, UNIX_EPOCH};
    use storage_core::{HttpExchange, StorageEngine};
    use tls_core::TlsEngine;

    const TCP_PORTS: [u16; 2] = [80, 443];
    const LISTENERS_PER_PORT: usize = 8;
    const TCP_BUF: usize = 64 * 1024;
    const ACC_CAP: usize = 128 * 1024;
    const DNS_TIMEOUT_MS: u128 = 5_000;

    // ---- tun device ---------------------------------------------------------

    fn set_nonblocking(fd: RawFd) {
        unsafe {
            let flags = libc::fcntl(fd, libc::F_GETFL, 0);
            if flags >= 0 {
                libc::fcntl(fd, libc::F_SETFL, flags | libc::O_NONBLOCK);
            }
        }
    }

    struct TunDevice {
        fd: RawFd,
        packets: *const AtomicU64,
    }

    struct TunRx(Vec<u8>);
    struct TunTx(RawFd);

    impl RxToken for TunRx {
        fn consume<R, F: FnOnce(&[u8]) -> R>(self, f: F) -> R {
            f(&self.0)
        }
    }

    impl TxToken for TunTx {
        fn consume<R, F: FnOnce(&mut [u8]) -> R>(self, len: usize, f: F) -> R {
            let mut buf = vec![0u8; len];
            let r = f(&mut buf);
            unsafe {
                libc::write(self.0, buf.as_ptr() as *const libc::c_void, len);
            }
            r
        }
    }

    impl Device for TunDevice {
        type RxToken<'a> = TunRx;
        type TxToken<'a> = TunTx;

        fn receive(&mut self, _t: SmolInstant) -> Option<(Self::RxToken<'_>, Self::TxToken<'_>)> {
            let mut buf = [0u8; 2048];
            let n = unsafe {
                libc::read(self.fd, buf.as_mut_ptr() as *mut libc::c_void, buf.len())
            };
            if n > 0 {
                unsafe { (*self.packets).fetch_add(1, Ordering::Relaxed) };
                Some((TunRx(buf[..n as usize].to_vec()), TunTx(self.fd)))
            } else {
                None
            }
        }

        fn transmit(&mut self, _t: SmolInstant) -> Option<Self::TxToken<'_>> {
            Some(TunTx(self.fd))
        }

        fn capabilities(&self) -> DeviceCapabilities {
            let mut caps = DeviceCapabilities::default();
            caps.medium = Medium::Ip;
            caps.max_transmission_unit = 1500;
            caps
        }
    }

    // ---- upstream socket helpers -------------------------------------------

    fn ip_to_std(addr: IpAddress) -> IpAddr {
        match addr {
            IpAddress::Ipv4(v4) => IpAddr::V4(Ipv4Addr::from(v4.octets())),
            IpAddress::Ipv6(v6) => IpAddr::V6(Ipv6Addr::from(v6.octets())),
        }
    }

    /// Create a `protect()`ed TCP socket connected (non-blocking) to `dest`.
    fn connect_upstream_tcp(dest: SocketAddr, protect: &ProtectFn) -> std::io::Result<TcpStream> {
        let domain = if dest.is_ipv4() { Domain::IPV4 } else { Domain::IPV6 };
        let sock = Sock2::new(domain, Type::STREAM, Some(Protocol::TCP))?;
        if !protect(sock.as_raw_fd()) {
            return Err(std::io::Error::new(
                std::io::ErrorKind::Other,
                "protect() failed",
            ));
        }
        sock.set_nonblocking(true)?;
        // Non-blocking connect returns EINPROGRESS; that is expected.
        match sock.connect(&SockAddr::from(dest)) {
            Ok(_) => {}
            Err(e) if e.raw_os_error() == Some(libc::EINPROGRESS) => {}
            Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => {}
            Err(e) => return Err(e),
        }
        Ok(TcpStream::from(sock))
    }

    fn protected_udp(protect: &ProtectFn, ipv4: bool) -> std::io::Result<UdpSocket> {
        let domain = if ipv4 { Domain::IPV4 } else { Domain::IPV6 };
        let sock = Sock2::new(domain, Type::DGRAM, Some(Protocol::UDP))?;
        if !protect(sock.as_raw_fd()) {
            return Err(std::io::Error::new(
                std::io::ErrorKind::Other,
                "protect() failed",
            ));
        }
        sock.set_nonblocking(true)?;
        Ok(UdpSocket::from(sock))
    }

    // ---- connection state ---------------------------------------------------

    struct Conn {
        handle: SocketHandle,
        port: u16,
        dest: SocketAddr,
        upstream: TcpStream,
        connected: bool,
        /// Bytes queued toward the upstream server (ciphertext when `tls` is set).
        to_up: VecDeque<u8>,
        /// Bytes queued toward the client via smoltcp (ciphertext when `tls` is set).
        to_cl: VecDeque<u8>,
        /// Plaintext request bytes awaiting HTTP framing (decrypted for MITM conns).
        req_acc: Vec<u8>,
        /// Plaintext response bytes awaiting HTTP framing.
        resp_acc: Vec<u8>,
        req_bytes: u64,
        resp_bytes: u64,
        start: Instant,
        server_closed: bool,
        finalized: bool,
        /// TLS interception state; `None` for plaintext (port 80) or opaque relay.
        tls: Option<TlsState>,
        /// True once at least one exchange has been flushed from this connection
        /// (keep-alive framing), so teardown does not double-record it.
        emitted: bool,
    }

    /// Per-connection MITM state. The client-facing leg (`server`) presents a
    /// KNI-signed leaf; the upstream leg (`client`) is a real TLS client to the
    /// origin server. Plaintext moves between them and is teed into the accumulators.
    struct TlsState {
        server: ServerConnection,
        client: Option<ClientConnection>,
        client_cfg: Arc<rustls::ClientConfig>,
        sni: Option<String>,
        version: String,
        cipher: String,
        cert: String,
        /// Set when interception could not complete (client rejected our leaf,
        /// pinned cert, or a protocol error) — recorded as a metadata note.
        failed: bool,
    }

    struct PendingDns {
        upstream: UdpSocket,
        client: IpEndpoint,
        server: IpAddress,
        start: Instant,
    }

    fn append_capped(acc: &mut Vec<u8>, data: &[u8]) {
        if acc.len() < ACC_CAP {
            let room = ACC_CAP - acc.len();
            acc.extend_from_slice(&data[..data.len().min(room)]);
        }
    }

    fn headers_json(headers: &[(String, String)]) -> String {
        let mut s = String::from("{");
        for (i, (k, v)) in headers.iter().enumerate() {
            if i > 0 {
                s.push(',');
            }
            s.push_str(&format!("{}:{}", json_str(k), json_str(v)));
        }
        s.push('}');
        s
    }

    fn json_str(s: &str) -> String {
        let mut out = String::with_capacity(s.len() + 2);
        out.push('"');
        for c in s.chars() {
            match c {
                '"' => out.push_str("\\\""),
                '\\' => out.push_str("\\\\"),
                '\n' => out.push_str("\\n"),
                '\r' => out.push_str("\\r"),
                '\t' => out.push_str("\\t"),
                c if (c as u32) < 0x20 => out.push(' '),
                c => out.push(c),
            }
        }
        out.push('"');
        out
    }

    fn now_millis() -> i64 {
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_millis() as i64)
            .unwrap_or(0)
    }

    fn new_tcp_socket() -> tcp::Socket<'static> {
        let rx = tcp::SocketBuffer::new(vec![0u8; TCP_BUF]);
        let tx = tcp::SocketBuffer::new(vec![0u8; TCP_BUF]);
        tcp::Socket::new(rx, tx)
    }

    /// Assemble an exchange by parsing plaintext request/response bytes. Used for
    /// plaintext HTTP (port 80) and for decrypted HTTPS (MITM) alike.
    #[allow(clippy::too_many_arguments)]
    fn assemble_http(
        id: String,
        scheme: &str,
        dest_host: &str,
        req_plain: &[u8],
        resp_plain: &[u8],
        req_size: i64,
        resp_size: i64,
        ts: i64,
        duration: i64,
        tls_version: &str,
        tls_cipher: &str,
        tls_cert: &str,
    ) -> HttpExchange {
        let req = parse_request(req_plain);
        let resp = parse_response(resp_plain);
        let (method, path, host, req_headers) = match &req {
            Some(r) => (
                r.method.clone(),
                r.path.clone(),
                r.host.clone().unwrap_or_else(|| dest_host.to_string()),
                headers_json(&r.headers),
            ),
            None => (
                "?".to_string(),
                "/".to_string(),
                dest_host.to_string(),
                "{}".to_string(),
            ),
        };
        let (status, resp_headers, resp_body) = match &resp {
            Some(r) => (
                Some(r.status as i32),
                headers_json(&r.headers),
                String::from_utf8_lossy(&r.body).to_string(),
            ),
            None => (None, "{}".to_string(), String::new()),
        };
        let req_body = req
            .as_ref()
            .map(|r| String::from_utf8_lossy(&r.body).to_string())
            .unwrap_or_default();
        HttpExchange {
            id,
            session_id: "live".to_string(),
            scheme: scheme.to_string(),
            host: host.clone(),
            method,
            url: format!("{}://{}{}", scheme, host, path),
            status_code: status,
            timestamp: ts,
            duration_ms: Some(duration),
            req_size,
            resp_size,
            request_headers: req_headers,
            response_headers: resp_headers,
            request_body: req_body,
            response_body: resp_body,
            tls_version: tls_version.to_string(),
            tls_cipher: tls_cipher.to_string(),
            tls_cert: tls_cert.to_string(),
        }
    }

    /// SNI-only metadata exchange for an HTTPS connection we did not decrypt
    /// (decryption disabled, no SNI, or a failed interception handshake).
    #[allow(clippy::too_many_arguments)]
    fn metadata_exchange(
        id: String,
        host: String,
        req_size: i64,
        resp_size: i64,
        ts: i64,
        duration: i64,
        tls_cert_note: String,
    ) -> HttpExchange {
        HttpExchange {
            id,
            session_id: "live".to_string(),
            scheme: "https".to_string(),
            url: format!("https://{}", host),
            host,
            method: "CONNECT".to_string(),
            status_code: None,
            timestamp: ts,
            duration_ms: Some(duration),
            req_size,
            resp_size,
            request_headers: "{}".to_string(),
            response_headers: "{}".to_string(),
            request_body: String::new(),
            response_body: String::new(),
            tls_version: String::new(),
            tls_cipher: String::new(),
            tls_cert: tls_cert_note,
        }
    }

    /// Whether this connection's accumulators hold plaintext HTTP that can be
    /// framed (plaintext port 80, or a decrypted MITM 443). Opaque 443 relays
    /// accumulate ciphertext and are handled as SNI-only metadata at teardown.
    fn is_plaintext(conn: &Conn) -> bool {
        conn.port == 80 || conn.tls.is_some()
    }

    /// Carve every *complete* request/response pair off the front of the plaintext
    /// accumulators and record it, so keep-alive / long-lived connections surface
    /// incrementally instead of only on teardown.
    fn flush_messages(
        conn: &mut Conn,
        storage: &StorageEngine,
        engine: &VpnEngine,
        counter: &mut u64,
    ) {
        if !is_plaintext(conn) {
            return;
        }
        let scheme = if conn.port == 443 { "https" } else { "http" };
        loop {
            let rlen = match response_len(&conn.resp_acc) {
                Some(n) if n > 0 && n <= conn.resp_acc.len() => n,
                _ => break,
            };
            let reqlen = request_len(&conn.req_acc)
                .unwrap_or(0)
                .min(conn.req_acc.len());
            let ts = now_millis();
            let duration = conn.start.elapsed().as_millis() as i64;
            *counter += 1;
            let id = format!("{}-{}", ts, *counter);
            let dest_host = conn.dest.ip().to_string();
            let (tv, tc, tcert) = conn
                .tls
                .as_ref()
                .map(|s| (s.version.clone(), s.cipher.clone(), s.cert.clone()))
                .unwrap_or_default();
            let ex = assemble_http(
                id,
                scheme,
                &dest_host,
                &conn.req_acc[..reqlen],
                &conn.resp_acc[..rlen],
                reqlen as i64,
                rlen as i64,
                ts,
                duration,
                &tv,
                &tc,
                &tcert,
            );
            if !engine.paused.load(Ordering::SeqCst)
                && storage.write_exchange(&ex).is_ok()
            {
                engine.exchanges.fetch_add(1, Ordering::Relaxed);
            }
            conn.resp_acc.drain(..rlen);
            if reqlen > 0 {
                conn.req_acc.drain(..reqlen);
            }
            conn.emitted = true;
            conn.start = Instant::now();
        }
    }

    /// Record the final exchange for a connection at teardown: flush any complete
    /// keep-alive messages, then emit whatever remains (body-until-close plaintext,
    /// or SNI-only metadata for opaque / failed HTTPS).
    fn finalize(conn: &mut Conn, storage: &StorageEngine, engine: &VpnEngine, counter: &mut u64) {
        if conn.finalized {
            return;
        }
        conn.finalized = true;
        if engine.paused.load(Ordering::SeqCst) {
            return;
        }

        flush_messages(conn, storage, engine, counter);

        let ts = now_millis();
        let duration = conn.start.elapsed().as_millis() as i64;

        let record = |ex: HttpExchange| {
            if storage.write_exchange(&ex).is_ok() {
                engine.exchanges.fetch_add(1, Ordering::Relaxed);
            }
        };

        // HTTPS that we did not (fully) decrypt: SNI-only metadata.
        if conn.port == 443 {
            match &conn.tls {
                Some(st) if st.failed || (!conn.emitted && conn.resp_acc.is_empty()) => {
                    let host = st
                        .sni
                        .clone()
                        .unwrap_or_else(|| conn.dest.ip().to_string());
                    let note = if st.failed {
                        "handshake failed (pinned cert or untrusted CA)".to_string()
                    } else {
                        "no plaintext captured".to_string()
                    };
                    *counter += 1;
                    let id = format!("{}-{}", ts, *counter);
                    record(metadata_exchange(
                        id,
                        host,
                        conn.req_bytes as i64,
                        conn.resp_bytes as i64,
                        ts,
                        duration,
                        note,
                    ));
                    return;
                }
                None => {
                    // Opaque relay (decryption disabled): SNI-only metadata.
                    if !conn.emitted {
                        let host = parse_client_hello_sni(&conn.req_acc)
                            .unwrap_or_else(|| conn.dest.ip().to_string());
                        *counter += 1;
                        let id = format!("{}-{}", ts, *counter);
                        record(metadata_exchange(
                            id,
                            host,
                            conn.req_bytes as i64,
                            conn.resp_bytes as i64,
                            ts,
                            duration,
                            String::new(),
                        ));
                    }
                    return;
                }
                _ => {}
            }
        }

        // Plaintext leftover (e.g. body-until-close) not yet flushed.
        if !conn.resp_acc.is_empty() || (!conn.emitted && !conn.req_acc.is_empty()) {
            let scheme = if conn.port == 443 { "https" } else { "http" };
            let dest_host = conn.dest.ip().to_string();
            let (tv, tc, tcert) = conn
                .tls
                .as_ref()
                .map(|s| (s.version.clone(), s.cipher.clone(), s.cert.clone()))
                .unwrap_or_default();
            *counter += 1;
            let id = format!("{}-{}", ts, *counter);
            let req_plain = std::mem::take(&mut conn.req_acc);
            let resp_plain = std::mem::take(&mut conn.resp_acc);
            record(assemble_http(
                id,
                scheme,
                &dest_host,
                &req_plain,
                &resp_plain,
                req_plain.len() as i64,
                resp_plain.len() as i64,
                ts,
                duration,
                &tv,
                &tc,
                &tcert,
            ));
        }
    }

    /// Bulk-write queued bytes to the upstream socket (was byte-at-a-time, which
    /// stalled high-volume connections). Writes contiguous `VecDeque` slices.
    fn write_upstream(conn: &mut Conn, did_work: &mut bool) {
        while !conn.to_up.is_empty() {
            let (a, b) = conn.to_up.as_slices();
            let buf = if !a.is_empty() { a } else { b };
            match conn.upstream.write(buf) {
                Ok(0) => break,
                Ok(n) => {
                    conn.to_up.drain(..n);
                    *did_work = true;
                }
                Err(ref e) if e.kind() == std::io::ErrorKind::WouldBlock => break,
                Err(_) => {
                    conn.server_closed = true;
                    break;
                }
            }
        }
    }

    /// Send queued bytes back to the client through the smoltcp socket.
    fn send_client(conn: &mut Conn, sockets: &mut SocketSet<'static>, did_work: &mut bool) {
        let s = sockets.get_mut::<tcp::Socket>(conn.handle);
        while s.can_send() && !conn.to_cl.is_empty() {
            let (a, b) = conn.to_cl.as_slices();
            let chunk = if !a.is_empty() { a } else { b };
            match s.send_slice(chunk) {
                Ok(0) => break,
                Ok(n) => {
                    conn.to_cl.drain(..n);
                    *did_work = true;
                }
                Err(_) => break,
            }
        }
    }

    /// Opaque relay pump (plaintext port 80, or an un-decrypted 443 tunnel):
    /// move bytes both directions and tee them into the accumulators.
    fn pump_plain(conn: &mut Conn, sockets: &mut SocketSet<'static>, did_work: &mut bool) {
        {
            let s = sockets.get_mut::<tcp::Socket>(conn.handle);
            let mut tmp = [0u8; 8192];
            while s.can_recv() {
                match s.recv_slice(&mut tmp) {
                    Ok(0) => break,
                    Ok(n) => {
                        conn.req_bytes += n as u64;
                        append_capped(&mut conn.req_acc, &tmp[..n]);
                        conn.to_up.extend(&tmp[..n]);
                        *did_work = true;
                    }
                    Err(_) => break,
                }
            }
        }

        if conn.connected {
            write_upstream(conn, did_work);

            let mut tmp = [0u8; 8192];
            loop {
                match conn.upstream.read(&mut tmp) {
                    Ok(0) => {
                        conn.server_closed = true;
                        break;
                    }
                    Ok(n) => {
                        conn.resp_bytes += n as u64;
                        append_capped(&mut conn.resp_acc, &tmp[..n]);
                        conn.to_cl.extend(&tmp[..n]);
                        *did_work = true;
                    }
                    Err(ref e) if e.kind() == std::io::ErrorKind::WouldBlock => break,
                    Err(_) => {
                        conn.server_closed = true;
                        break;
                    }
                }
            }
        }

        send_client(conn, sockets, did_work);
    }

    /// MITM pump: terminate TLS toward the client, re-originate TLS toward the
    /// upstream server, and tee the plaintext in the middle into the accumulators.
    fn pump_tls(conn: &mut Conn, sockets: &mut SocketSet<'static>, did_work: &mut bool) {
        // 1. Client ciphertext (smoltcp rx) -> server-side rustls.
        {
            let s = sockets.get_mut::<tcp::Socket>(conn.handle);
            let st = conn.tls.as_mut().unwrap();
            let mut tmp = [0u8; 8192];
            while s.can_recv() {
                match s.recv_slice(&mut tmp) {
                    Ok(0) => break,
                    Ok(n) => {
                        conn.req_bytes += n as u64;
                        let mut data: &[u8] = &tmp[..n];
                        while !data.is_empty() {
                            match st.server.read_tls(&mut data) {
                                Ok(0) => break,
                                Ok(_) => {
                                    if st.server.process_new_packets().is_err() {
                                        st.failed = true;
                                        break;
                                    }
                                }
                                Err(_) => {
                                    st.failed = true;
                                    break;
                                }
                            }
                        }
                        *did_work = true;
                    }
                    Err(_) => break,
                }
            }
        }

        // 2. Once the ClientHello has yielded an SNI, originate the upstream TLS.
        {
            let st = conn.tls.as_mut().unwrap();
            if st.client.is_none() && !st.failed {
                if let Some(name) = st.server.server_name() {
                    st.sni = Some(name.to_string());
                    match ServerName::try_from(name) {
                        Ok(sn) => match ClientConnection::new(st.client_cfg.clone(), sn) {
                            Ok(c) => st.client = Some(c),
                            Err(_) => st.failed = true,
                        },
                        Err(_) => st.failed = true,
                    }
                }
            }
        }

        // 3. Decrypted request plaintext -> req_acc + re-encrypt toward upstream.
        {
            let st = conn.tls.as_mut().unwrap();
            if let Some(client) = st.client.as_mut() {
                let mut buf = [0u8; 8192];
                loop {
                    match st.server.reader().read(&mut buf) {
                        Ok(0) => break,
                        Ok(n) => {
                            append_capped(&mut conn.req_acc, &buf[..n]);
                            let _ = client.writer().write_all(&buf[..n]);
                            *did_work = true;
                        }
                        Err(ref e) if e.kind() == std::io::ErrorKind::WouldBlock => break,
                        Err(_) => {
                            st.failed = true;
                            break;
                        }
                    }
                }
            }
        }

        // 4. Upstream ciphertext -> client-side rustls (the KNI->server leg).
        if conn.connected {
            let st = conn.tls.as_mut().unwrap();
            if let Some(client) = st.client.as_mut() {
                let mut tmp = [0u8; 8192];
                loop {
                    match conn.upstream.read(&mut tmp) {
                        Ok(0) => {
                            conn.server_closed = true;
                            break;
                        }
                        Ok(n) => {
                            conn.resp_bytes += n as u64;
                            let mut data: &[u8] = &tmp[..n];
                            while !data.is_empty() {
                                match client.read_tls(&mut data) {
                                    Ok(0) => break,
                                    Ok(_) => {
                                        if client.process_new_packets().is_err() {
                                            st.failed = true;
                                            break;
                                        }
                                    }
                                    Err(_) => {
                                        st.failed = true;
                                        break;
                                    }
                                }
                            }
                            *did_work = true;
                        }
                        Err(ref e) if e.kind() == std::io::ErrorKind::WouldBlock => break,
                        Err(_) => {
                            conn.server_closed = true;
                            break;
                        }
                    }
                }
            }
        }

        // 5. Decrypted response plaintext -> resp_acc + re-encrypt toward client.
        {
            let st = conn.tls.as_mut().unwrap();
            if let Some(client) = st.client.as_mut() {
                let mut buf = [0u8; 8192];
                loop {
                    match client.reader().read(&mut buf) {
                        Ok(0) => break,
                        Ok(n) => {
                            append_capped(&mut conn.resp_acc, &buf[..n]);
                            let _ = st.server.writer().write_all(&buf[..n]);
                            *did_work = true;
                        }
                        Err(ref e) if e.kind() == std::io::ErrorKind::WouldBlock => break,
                        Err(_) => {
                            st.failed = true;
                            break;
                        }
                    }
                }
            }
        }

        // 6. Capture negotiated TLS parameters once the client-facing handshake is done.
        {
            let st = conn.tls.as_mut().unwrap();
            if st.version.is_empty() && !st.server.is_handshaking() {
                if let Some(v) = st.server.protocol_version() {
                    st.version = format!("{:?}", v);
                }
                if let Some(cs) = st.server.negotiated_cipher_suite() {
                    st.cipher = format!("{:?}", cs.suite());
                }
                let n = st
                    .client
                    .as_ref()
                    .and_then(|c| c.peer_certificates().map(|certs| certs.len()))
                    .unwrap_or(0);
                let host = st.sni.clone().unwrap_or_default();
                st.cert = format!(
                    "{} — {} cert(s) in upstream chain, verified against system roots",
                    host, n
                );
            }
        }

        // 7. Drain outgoing TLS records into the ciphertext queues.
        {
            let st = conn.tls.as_mut().unwrap();
            while st.server.wants_write() {
                let mut tmp = Vec::new();
                match st.server.write_tls(&mut tmp) {
                    Ok(0) | Err(_) => break,
                    Ok(_) => {}
                }
                conn.to_cl.extend(tmp);
                *did_work = true;
            }
            if let Some(client) = st.client.as_mut() {
                while client.wants_write() {
                    let mut tmp = Vec::new();
                    match client.write_tls(&mut tmp) {
                        Ok(0) | Err(_) => break,
                        Ok(_) => {}
                    }
                    conn.to_up.extend(tmp);
                    *did_work = true;
                }
            }
        }

        // 8. Flush queued ciphertext to the real sockets.
        if conn.connected {
            write_upstream(conn, did_work);
        }
        send_client(conn, sockets, did_work);

        // A failed interception cannot recover; wind the connection down. Drop any
        // queued ciphertext so the connection meets the drain condition and is reaped
        // (the client has rejected our leaf, so there is nothing useful left to send).
        if conn.tls.as_ref().map(|s| s.failed).unwrap_or(false) {
            conn.server_closed = true;
            conn.to_up.clear();
            conn.to_cl.clear();
        }
    }

    pub fn run(engine: &VpnEngine) -> i32 {
        set_nonblocking(engine.tun_fd);
        let storage = StorageEngine::new(&engine.db_path);

        // Build the CA-backed TLS engine and reusable rustls configs. If the CA
        // cannot be prepared, MITM is simply unavailable and 443 stays opaque.
        let tls_engine = TlsEngine::new(&engine.ca_dir).ok().map(Arc::new);
        let server_cfg = tls_engine.as_ref().map(|e| e.server_config());
        let client_cfg = tls_engine.as_ref().map(|e| e.client_config());

        let mut device = TunDevice {
            fd: engine.tun_fd,
            packets: &engine.packets as *const AtomicU64,
        };

        let config = Config::new(HardwareAddress::Ip);
        let start_instant = Instant::now();
        let mut iface = Interface::new(config, &mut device, SmolInstant::from_millis(0));
        iface.set_any_ip(true);
        iface.update_ip_addrs(|addrs| {
            let _ = addrs.push(IpCidr::new(IpAddress::v4(10, 0, 0, 1), 24));
        });
        // Deliver egress to the on-link client (10.0.0.2); any_ip handles the rest.
        let _ = iface
            .routes_mut()
            .add_default_ipv4_route(smoltcp::wire::Ipv4Address::new(10, 0, 0, 1));

        let mut sockets = SocketSet::new(Vec::new());

        // Seed the listener pool for each relayed TCP port.
        let mut listeners: Vec<(u16, SocketHandle)> = Vec::new();
        for &port in TCP_PORTS.iter() {
            for _ in 0..LISTENERS_PER_PORT {
                let mut s = new_tcp_socket();
                let _ = s.listen(port);
                let h = sockets.add(s);
                listeners.push((port, h));
            }
        }

        // DNS relay socket.
        let udp_rx = udp::PacketBuffer::new(
            vec![udp::PacketMetadata::EMPTY; 32],
            vec![0u8; 64 * 1024],
        );
        let udp_tx = udp::PacketBuffer::new(
            vec![udp::PacketMetadata::EMPTY; 32],
            vec![0u8; 64 * 1024],
        );
        let mut dns_sock = udp::Socket::new(udp_rx, udp_tx);
        let _ = dns_sock.bind(53);
        let dns_handle = sockets.add(dns_sock);

        let mut conns: Vec<Conn> = Vec::new();
        let mut pending_dns: Vec<PendingDns> = Vec::new();
        let mut counter: u64 = 0;

        while !engine.stop.load(Ordering::SeqCst) {
            let now = SmolInstant::from_micros(start_instant.elapsed().as_micros() as i64);
            iface.poll(now, &mut device, &mut sockets);

            let mut did_work = false;

            // Promote listeners that accepted a connection.
            let mut i = 0;
            while i < listeners.len() {
                let (port, handle) = listeners[i];
                let s = sockets.get::<tcp::Socket>(handle);
                let active = s.state() != tcp::State::Listen && s.state() != tcp::State::Closed;
                let local = s.local_endpoint();
                if active {
                    if let Some(ep) = local {
                        let dest = SocketAddr::new(ip_to_std(ep.addr), ep.port);
                        listeners.remove(i);
                        // Refill the pool.
                        let mut ns = new_tcp_socket();
                        let _ = ns.listen(port);
                        let nh = sockets.add(ns);
                        listeners.push((port, nh));

                        match connect_upstream_tcp(dest, &engine.protect) {
                            Ok(up) => {
                                // Attempt MITM on 443 only when decryption is enabled
                                // and the CA/config is available; otherwise relay opaquely.
                                let tls = if port == 443
                                    && engine.decrypt.load(Ordering::SeqCst)
                                    && server_cfg.is_some()
                                {
                                    match ServerConnection::new(server_cfg.clone().unwrap()) {
                                        Ok(server) => Some(TlsState {
                                            server,
                                            client: None,
                                            client_cfg: client_cfg.clone().unwrap(),
                                            sni: None,
                                            version: String::new(),
                                            cipher: String::new(),
                                            cert: String::new(),
                                            failed: false,
                                        }),
                                        Err(_) => None,
                                    }
                                } else {
                                    None
                                };
                                conns.push(Conn {
                                    handle,
                                    port,
                                    dest,
                                    upstream: up,
                                    connected: false,
                                    to_up: VecDeque::new(),
                                    to_cl: VecDeque::new(),
                                    req_acc: Vec::new(),
                                    resp_acc: Vec::new(),
                                    req_bytes: 0,
                                    resp_bytes: 0,
                                    start: Instant::now(),
                                    server_closed: false,
                                    finalized: false,
                                    tls,
                                    emitted: false,
                                });
                            }
                            Err(_) => {
                                sockets.get_mut::<tcp::Socket>(handle).abort();
                            }
                        }
                        did_work = true;
                        continue;
                    }
                }
                i += 1;
            }

            // Pump each live connection.
            let mut c = 0;
            while c < conns.len() {
                let mut remove = false;
                {
                    let conn = &mut conns[c];

                    // Detect upstream connect completion.
                    if !conn.connected && conn.upstream.peer_addr().is_ok() {
                        conn.connected = true;
                    }

                    if conn.tls.is_some() {
                        pump_tls(conn, &mut sockets, &mut did_work);
                    } else {
                        pump_plain(conn, &mut sockets, &mut did_work);
                    }

                    // Close detection.
                    let s = sockets.get_mut::<tcp::Socket>(conn.handle);
                    let state = s.state();
                    let client_gone = matches!(
                        state,
                        tcp::State::Closed | tcp::State::CloseWait | tcp::State::TimeWait
                    );
                    if conn.server_closed && conn.to_cl.is_empty() {
                        s.close();
                    }
                    if (client_gone || conn.server_closed)
                        && conn.to_cl.is_empty()
                        && conn.to_up.is_empty()
                    {
                        remove = true;
                    }
                }

                // Surface completed keep-alive messages as they finish.
                flush_messages(&mut conns[c], &storage, engine, &mut counter);

                if remove {
                    finalize(&mut conns[c], &storage, engine, &mut counter);
                    let _ = conns[c].upstream.shutdown(std::net::Shutdown::Both);
                    sockets.remove(conns[c].handle);
                    conns.remove(c);
                    did_work = true;
                } else {
                    c += 1;
                }
            }

            // DNS: forward client queries to protected upstream sockets.
            {
                let s = sockets.get_mut::<udp::Socket>(dns_handle);
                loop {
                    match s.recv() {
                        Ok((data, meta)) => {
                            let server = match meta.local_address {
                                Some(a) => a,
                                None => continue,
                            };
                            let server_addr = SocketAddr::new(ip_to_std(server), 53);
                            let is_v4 = server_addr.is_ipv4();
                            if let Ok(up) = protected_udp(&engine.protect, is_v4) {
                                if up.connect(server_addr).is_ok() && up.send(data).is_ok() {
                                    pending_dns.push(PendingDns {
                                        upstream: up,
                                        client: meta.endpoint,
                                        server,
                                        start: Instant::now(),
                                    });
                                    did_work = true;
                                }
                            }
                        }
                        Err(_) => break,
                    }
                }
            }

            // DNS: return upstream responses to the originating client.
            {
                let mut p = 0;
                while p < pending_dns.len() {
                    let mut done = false;
                    let mut buf = [0u8; 2048];
                    match pending_dns[p].upstream.recv(&mut buf) {
                        Ok(n) if n > 0 => {
                            let client = pending_dns[p].client;
                            let server = pending_dns[p].server;
                            let s = sockets.get_mut::<udp::Socket>(dns_handle);
                            let meta = udp::UdpMetadata {
                                endpoint: client,
                                local_address: Some(server),
                                meta: Default::default(),
                            };
                            let _ = s.send_slice(&buf[..n], meta);
                            done = true;
                            did_work = true;
                        }
                        _ => {}
                    }
                    if done || pending_dns[p].start.elapsed().as_millis() > DNS_TIMEOUT_MS {
                        pending_dns.remove(p);
                    } else {
                        p += 1;
                    }
                }
            }

            if !did_work {
                std::thread::sleep(std::time::Duration::from_millis(5));
            }
        }

        0
    }
}

#[cfg(unix)]
impl VpnEngine {
    /// Run the capture loop until `stop()` is called. Blocks the calling thread.
    pub fn run(&self) -> i32 {
        imp::run(self)
    }
}
