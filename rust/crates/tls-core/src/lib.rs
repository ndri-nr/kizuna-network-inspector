use rcgen::{
    BasicConstraints, Certificate, CertificateParams, DistinguishedName, DnType, IsCa, KeyPair,
    KeyUsagePurpose, SanType,
};
use std::collections::HashMap;
use std::fs;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};

use rustls::server::{ClientHello, ResolvesServerCert};
use rustls::sign::{any_supported_type, CertifiedKey};
use rustls::{
    Certificate as RustlsCert, ClientConfig, OwnedTrustAnchor, PrivateKey, RootCertStore,
    ServerConfig,
};
use zeroize::Zeroizing;

const CA_CERT_FILE: &str = "kni_ca_cert.pem";
const CA_KEY_FILE: &str = "kni_ca_key.pem";

/// Owns the Kizuna Root CA and mints per-host leaf certificates for TLS
/// interception. The CA private key lives here (Rust) so Phase 2 MITM can sign
/// leaf certs without ever exporting the key across the JNI boundary; only the
/// public certificate PEM is handed to the platform for the user to install.
pub struct TlsEngine {
    ca_cert_pem: String,
    /// PEM of the CA private key, retained only so it is zeroized on drop; the
    /// live signer is `ca`. Not read after construction.
    #[allow(dead_code)]
    ca_key_pem: Zeroizing<String>,
    /// The rcgen signing object used to sign leaf certificates. Rebuilt from the
    /// persisted PEMs on load, or the freshly generated CA on first launch.
    ca: Certificate,
    /// Per-host leaf certificate cache (spec: cache leaves to avoid re-signing).
    leaf_cache: Mutex<HashMap<String, Arc<CertifiedKey>>>,
    /// Upstream (KNI→server) TLS client config, built once. Verifies the real
    /// server against the bundled Mozilla root set; ALPN offers http/1.1 only so
    /// decrypted streams stay HTTP/1.1 for the existing parser.
    client_config: Arc<ClientConfig>,
}

impl TlsEngine {
    /// Load the persisted CA from `ca_dir`, or generate and persist a new one if
    /// none exists. Idempotent across launches so an installed CA stays trusted.
    pub fn new(ca_dir: &str) -> Result<Self, String> {
        let dir = PathBuf::from(ca_dir);
        fs::create_dir_all(&dir).map_err(|e| format!("create ca_dir: {e}"))?;
        let cert_path = dir.join(CA_CERT_FILE);
        let key_path = dir.join(CA_KEY_FILE);

        if cert_path.exists() && key_path.exists() {
            if let (Ok(cert_pem), Ok(key_pem)) =
                (fs::read_to_string(&cert_path), fs::read_to_string(&key_path))
            {
                if cert_pem.contains("BEGIN CERTIFICATE") && key_pem.contains("PRIVATE KEY") {
                    return Self::from_pems(cert_pem, key_pem);
                }
            }
            // Corrupt/unreadable persisted CA: fall through and regenerate.
        }

        let (cert_pem, key_pem, ca) = Self::generate_ca()?;
        fs::write(&cert_path, cert_pem.as_bytes()).map_err(|e| e.to_string())?;
        fs::write(&key_path, key_pem.as_bytes()).map_err(|e| e.to_string())?;
        Self::assemble(cert_pem, key_pem, ca)
    }

    fn ca_params() -> CertificateParams {
        let mut params = CertificateParams::default();
        let mut dn = DistinguishedName::new();
        dn.push(DnType::CommonName, "Kizuna Network Inspector Root CA");
        dn.push(DnType::OrganizationName, "Kizuna");
        params.distinguished_name = dn;
        params.is_ca = IsCa::Ca(BasicConstraints::Unconstrained);
        params.key_usages = vec![
            KeyUsagePurpose::KeyCertSign,
            KeyUsagePurpose::CrlSign,
            KeyUsagePurpose::DigitalSignature,
        ];
        params
    }

    /// Generate a fresh CA, returning its (cert PEM, key PEM, signer).
    fn generate_ca() -> Result<(String, String, Certificate), String> {
        let ca = Certificate::from_params(Self::ca_params()).map_err(|e| e.to_string())?;
        let cert_pem = ca.serialize_pem().map_err(|e| e.to_string())?;
        let key_pem = ca.serialize_private_key_pem();
        Ok((cert_pem, key_pem, ca))
    }

    /// Rebuild the signing object from persisted PEMs. We reconstruct the CA from
    /// our fixed `ca_params()` plus the loaded key pair rather than re-parsing the
    /// certificate (which would require rcgen's `x509-parser` feature). Because the
    /// CA is always generated from `ca_params()`, the rebuilt signer carries the
    /// same issuer DN and the same key as the installed root, so leaves it signs
    /// chain to and validate against it. The original `cert_pem` is retained
    /// verbatim for the user; `ca` is used only to sign leaves, never re-serialized.
    fn from_pems(cert_pem: String, key_pem: String) -> Result<Self, String> {
        let key_pair = KeyPair::from_pem(&key_pem).map_err(|e| format!("ca key parse: {e}"))?;
        let mut params = Self::ca_params();
        params.key_pair = Some(key_pair);
        let ca = Certificate::from_params(params).map_err(|e| e.to_string())?;
        Self::assemble(cert_pem, key_pem, ca)
    }

    fn assemble(cert_pem: String, key_pem: String, ca: Certificate) -> Result<Self, String> {
        Ok(Self {
            ca_cert_pem: cert_pem,
            ca_key_pem: Zeroizing::new(key_pem),
            ca,
            leaf_cache: Mutex::new(HashMap::new()),
            client_config: Self::build_client_config(),
        })
    }

    fn build_client_config() -> Arc<ClientConfig> {
        let mut roots = RootCertStore::empty();
        roots.add_trust_anchors(webpki_roots::TLS_SERVER_ROOTS.iter().map(|ta| {
            OwnedTrustAnchor::from_subject_spki_name_constraints(
                ta.subject,
                ta.spki,
                ta.name_constraints,
            )
        }));
        let mut config = ClientConfig::builder()
            .with_safe_defaults()
            .with_root_certificates(roots)
            .with_no_client_auth();
        config.alpn_protocols = vec![b"http/1.1".to_vec()];
        Arc::new(config)
    }

    /// PEM of the CA certificate for the user to install as a trusted root.
    pub fn ca_cert_pem(&self) -> &str {
        &self.ca_cert_pem
    }

    /// Mint (or fetch from cache) a leaf certificate for `host`, signed by the CA.
    pub fn leaf_for(&self, host: &str) -> Result<Arc<CertifiedKey>, String> {
        if let Some(hit) = self.leaf_cache.lock().unwrap().get(host).cloned() {
            return Ok(hit);
        }

        let mut params = CertificateParams::new(vec![host.to_string()]);
        params.subject_alt_names = vec![SanType::DnsName(host.to_string())];
        let mut dn = DistinguishedName::new();
        dn.push(DnType::CommonName, host);
        params.distinguished_name = dn;
        // Fixed, wide validity avoids depending on a wall clock in the core.
        params.not_before = rcgen::date_time_ymd(2020, 1, 1);
        params.not_after = rcgen::date_time_ymd(2035, 1, 1);

        let leaf = Certificate::from_params(params).map_err(|e| e.to_string())?;
        let cert_der = leaf
            .serialize_der_with_signer(&self.ca)
            .map_err(|e| e.to_string())?;
        let key_der = leaf.serialize_private_key_der();

        let signing_key = any_supported_type(&PrivateKey(key_der))
            .map_err(|e| format!("leaf key: {e}"))?;
        let certified = Arc::new(CertifiedKey::new(vec![RustlsCert(cert_der)], signing_key));

        self.leaf_cache
            .lock()
            .unwrap()
            .insert(host.to_string(), certified.clone());
        Ok(certified)
    }

    /// TLS server config that mints a leaf per SNI on the fly (client-facing leg).
    pub fn server_config(self: &Arc<Self>) -> Arc<ServerConfig> {
        let resolver = Arc::new(SniCertResolver {
            engine: Arc::clone(self),
        });
        let mut config = ServerConfig::builder()
            .with_safe_defaults()
            .with_no_client_auth()
            .with_cert_resolver(resolver);
        config.alpn_protocols = vec![b"http/1.1".to_vec()];
        Arc::new(config)
    }

    /// Upstream TLS client config (KNI→real server leg).
    pub fn client_config(&self) -> Arc<ClientConfig> {
        Arc::clone(&self.client_config)
    }
}

/// Resolves the client-facing leaf certificate from the SNI in the ClientHello.
struct SniCertResolver {
    engine: Arc<TlsEngine>,
}

impl ResolvesServerCert for SniCertResolver {
    fn resolve(&self, client_hello: ClientHello) -> Option<Arc<CertifiedKey>> {
        let host = client_hello.server_name()?;
        self.engine.leaf_for(host).ok()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn generates_valid_ca_pem() {
        let (cert_pem, key_pem, _ca) = TlsEngine::generate_ca().unwrap();
        assert!(cert_pem.contains("BEGIN CERTIFICATE"));
        assert!(cert_pem.contains("END CERTIFICATE"));
        assert!(key_pem.contains("PRIVATE KEY"));
    }

    #[test]
    fn persists_and_reloads_same_ca() {
        let dir = std::env::temp_dir().join("kni_ca_test_reload");
        let _ = std::fs::remove_dir_all(&dir);
        let dir_str = dir.to_str().unwrap();

        let first = TlsEngine::new(dir_str).unwrap().ca_cert_pem().to_string();
        // Second construction must load the persisted CA, not regenerate a new one.
        let second = TlsEngine::new(dir_str).unwrap().ca_cert_pem().to_string();
        assert_eq!(first, second, "CA must be stable across constructions");

        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn mints_and_caches_leaf() {
        let dir = std::env::temp_dir().join("kni_ca_test_leaf");
        let _ = std::fs::remove_dir_all(&dir);
        let engine = TlsEngine::new(dir.to_str().unwrap()).unwrap();

        let a = engine.leaf_for("api.example.com").unwrap();
        let b = engine.leaf_for("api.example.com").unwrap();
        assert!(Arc::ptr_eq(&a, &b), "same host must return cached leaf");
        assert!(!a.cert.is_empty(), "leaf must carry a certificate");

        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn builds_server_and_client_configs() {
        let dir = std::env::temp_dir().join("kni_ca_test_cfg");
        let _ = std::fs::remove_dir_all(&dir);
        let engine = Arc::new(TlsEngine::new(dir.to_str().unwrap()).unwrap());

        let sc = engine.server_config();
        assert_eq!(sc.alpn_protocols, vec![b"http/1.1".to_vec()]);
        let cc = engine.client_config();
        assert_eq!(cc.alpn_protocols, vec![b"http/1.1".to_vec()]);

        let _ = std::fs::remove_dir_all(&dir);
    }
}
