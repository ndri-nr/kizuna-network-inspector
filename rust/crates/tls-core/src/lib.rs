use rcgen::{
    BasicConstraints, Certificate, CertificateParams, DistinguishedName, DnType, IsCa,
    KeyUsagePurpose,
};
use std::fs;
use std::path::PathBuf;

const CA_CERT_FILE: &str = "kni_ca_cert.pem";
const CA_KEY_FILE: &str = "kni_ca_key.pem";

/// Owns the Kizuna Root CA. The CA private key lives here (Rust) because Phase 2
/// TLS termination must mint per-host leaf certs signed by this CA without ever
/// exporting the private key across the JNI boundary. Only the public certificate
/// PEM is handed to the platform layer for the user to install.
///
/// Phase 1 only needs the CA certificate PEM (for the user to install as a trusted
/// root). The private key is persisted alongside it so Phase 2 can reload it to
/// sign leaf certificates; reconstruction of the rcgen signing object is deferred
/// to that phase to avoid pulling the `x509-parser` feature (and its incompatible
/// `time` transitive) into the build now.
pub struct TlsEngine {
    ca_cert_pem: String,
    #[allow(dead_code)]
    ca_key_pem: String,
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
            if let (Ok(cert_pem), Ok(key_pem)) = (
                fs::read_to_string(&cert_path),
                fs::read_to_string(&key_path),
            ) {
                if cert_pem.contains("BEGIN CERTIFICATE") && key_pem.contains("PRIVATE KEY") {
                    return Ok(Self {
                        ca_cert_pem: cert_pem,
                        ca_key_pem: key_pem,
                    });
                }
            }
            // Corrupt/unreadable persisted CA: fall through and regenerate.
        }

        let engine = Self::generate()?;
        fs::write(&cert_path, engine.ca_cert_pem.as_bytes()).map_err(|e| e.to_string())?;
        fs::write(&key_path, engine.ca_key_pem.as_bytes()).map_err(|e| e.to_string())?;
        Ok(engine)
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

    fn generate() -> Result<Self, String> {
        let ca = Certificate::from_params(Self::ca_params()).map_err(|e| e.to_string())?;
        let ca_cert_pem = ca.serialize_pem().map_err(|e| e.to_string())?;
        let ca_key_pem = ca.serialize_private_key_pem();
        Ok(Self {
            ca_cert_pem,
            ca_key_pem,
        })
    }

    /// PEM of the CA certificate for the user to install as a trusted root.
    pub fn ca_cert_pem(&self) -> &str {
        &self.ca_cert_pem
    }

    /// Phase 2 hook: mint/serve a leaf cert for `sni` signed by this CA and
    /// terminate the TLS handshake. No-op until MITM decryption lands.
    pub fn intercept_handshake(&mut self, _conn_id: u64, _sni: &str) -> Result<(), &'static str> {
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn generates_valid_ca_pem() {
        let engine = TlsEngine::generate().unwrap();
        let pem = engine.ca_cert_pem();
        assert!(pem.contains("BEGIN CERTIFICATE"));
        assert!(pem.contains("END CERTIFICATE"));
        assert!(engine.ca_key_pem.contains("PRIVATE KEY"));
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
}
