pub struct VpnEngine {
    pub tun_fd: i32,
}

impl VpnEngine {
    pub fn new(tun_fd: i32) -> Self {
        Self { tun_fd }
    }

    pub fn read_packets(&mut self) -> i32 {
        0
    }
}
